/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.targetsystem.mongodb.springdata;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

public class MongoSpringDataTxWrapper implements TransactionWrapper {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("SpringMongoTx");
    
    private final TransactionTemplate txTemplate;

    private MongoSpringDataTxWrapper(MongoDatabaseFactory mongoDatabaseFactory, TransactionOptions txOptions) {
        MongoTransactionManager txManager = new MongoTransactionManager(mongoDatabaseFactory, txOptions);

        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.txTemplate.setName("flamingock-transaction");
    }


    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        LocalDateTime transactionStart = LocalDateTime.now();
        
        try {
            logger.debug("Starting MongoDB Spring Data transaction");
            
            return txTemplate.execute(status -> {
                try {
                    T result = operation.apply(executionRuntime);
                    Duration transactionDuration = Duration.between(transactionStart, LocalDateTime.now());
                    
                    if (result instanceof FailedStep) {
                        logger.info("Rolling back MongoDB Spring Data transaction due to failed step [duration={}]", formatDuration(transactionDuration));
                        status.setRollbackOnly();
                        logger.debug("MongoDB Spring Data transaction marked for rollback [duration={}]", formatDuration(transactionDuration));
                    } else {
                        logger.debug("Committing successful MongoDB Spring Data transaction [duration={}]", formatDuration(transactionDuration));
                    }
                    return result;
                    
                } catch (Exception e) {
                    Duration failureDuration = Duration.between(transactionStart, LocalDateTime.now());
                    logger.error("MongoDB Spring Data transaction failed, marking for rollback [duration={} error={}]", 
                               formatDuration(failureDuration), e.getMessage());
                    status.setRollbackOnly();
                    
                    throw new DatabaseTransactionException(
                        "MongoDB Spring Data transaction failed during operation execution",
                        DatabaseTransactionException.TransactionState.FAILED,
                        null, // isolation level not applicable to MongoDB
                        null, // timeout not available
                        failureDuration,
                        DatabaseTransactionException.RollbackStatus.SUCCESS, // Spring handles rollback
                        null, // specific operation not available at this level
                        "Spring Data MongoDB",
                        e
                    );
                }
            });
            
        } catch (Exception e) {
            Duration failureDuration = Duration.between(transactionStart, LocalDateTime.now());
            
            // If it's already our exception, re-throw it
            if (e instanceof DatabaseTransactionException) {
                throw e;
            }
            
            throw new DatabaseTransactionException(
                "MongoDB Spring Data transaction failed to start or commit",
                DatabaseTransactionException.TransactionState.FAILED,
                null,
                null,
                failureDuration,
                DatabaseTransactionException.RollbackStatus.SUCCESS, // Spring handles rollback
                null,
                "Spring Data MongoDB",
                e
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ReadConcern readConcern = ReadConcern.MAJORITY;
        private ReadPreference readPreference = ReadPreference.primary();
        private WriteConcern writeConcern = WriteConcern.MAJORITY.withJournal(true);
        private MongoTemplate mongoTemplate;

        private Builder() {
        }

        public Builder readConcern(ReadConcern readConcern) {
            this.readConcern = readConcern;
            return this;
        }

        public Builder readPreference(ReadPreference readPreference) {
            this.readPreference = readPreference;
            return this;
        }

        public Builder writeConcern(WriteConcern writeConcern) {
            this.writeConcern = writeConcern;
            return this;
        }

        public Builder mongoTemplate(MongoTemplate mongoTemplate) {
            this.mongoTemplate = mongoTemplate;
            return this;
        }

        public MongoSpringDataTxWrapper build() {
            if (mongoTemplate == null) {
                throw new IllegalArgumentException("MongoTemplate is required");
            }

            TransactionOptions txOptions = TransactionOptions.builder()
                    .readConcern(readConcern)
                    .readPreference(readPreference)
                    .writeConcern(writeConcern)
                    .build();

            return new MongoSpringDataTxWrapper(mongoTemplate.getMongoDatabaseFactory(),txOptions);
        }
    }
    
    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%.1fm", millis / 60000.0);
        }
    }

}
