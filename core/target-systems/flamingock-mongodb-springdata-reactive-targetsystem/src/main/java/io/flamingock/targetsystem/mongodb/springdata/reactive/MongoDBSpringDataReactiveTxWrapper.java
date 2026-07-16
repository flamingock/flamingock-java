/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.targetsystem.mongodb.springdata.reactive;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.ClientSessionOptions;
import com.mongodb.reactivestreams.client.ClientSession;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.core.change.navigation.step.FailedStep;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

public class MongoDBSpringDataReactiveTxWrapper implements TransactionWrapper {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("SpringMongoReactiveTx");
    private final ReactiveMongoTemplate mongoTemplate;
    private final TransactionOptions txOptions;

    private MongoDBSpringDataReactiveTxWrapper(ReactiveMongoTemplate mongoTemplate, TransactionOptions txOptions) {
        this.mongoTemplate = mongoTemplate;
        this.txOptions = txOptions;
    }

    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        LocalDateTime transactionStart = LocalDateTime.now();
        ClientSession clientSession = null;
        try {
            logger.debug("Starting MongoDB Spring Data reactive transaction");

            clientSession = mongoTemplate.getMongoDatabaseFactory()
                    .getSession(ClientSessionOptions.builder().build())
                    .block();
            clientSession.startTransaction(txOptions);

            ReactiveMongoTemplate sessionMongoTemplate = new ReactiveMongoTemplate(
                    mongoTemplate.getMongoDatabaseFactory().withSession(clientSession),
                    mongoTemplate.getConverter());
            executionRuntime.addDependency(new Dependency(ReactiveMongoTemplate.class, sessionMongoTemplate));

            try {
                T result = operation.apply(executionRuntime);
                Duration transactionDuration = Duration.between(transactionStart, LocalDateTime.now());

                if (result instanceof FailedStep) {
                    logger.info("Rolling back MongoDB Spring Data reactive transaction due to failed step [duration={}]", formatDuration(transactionDuration));
                    abortTransaction(clientSession);
                    logger.debug("MongoDB Spring Data reactive transaction rollback completed successfully [duration={}]", formatDuration(transactionDuration));
                } else {
                    logger.debug("Committing successful MongoDB Spring Data reactive transaction [duration={}]", formatDuration(transactionDuration));
                    commitTransaction(clientSession);
                    logger.debug("MongoDB Spring Data reactive transaction commit completed successfully [duration={}]", formatDuration(transactionDuration));
                }
                return result;

            } catch (Exception e) {
                Duration failureDuration = Duration.between(transactionStart, LocalDateTime.now());
                logger.debug("MongoDB Spring Data reactive transaction failed, attempting rollback [duration={} error={}]",
                        formatDuration(failureDuration), e.getMessage());
                abortTransaction(clientSession);

                throw new DatabaseTransactionException(
                        "MongoDB Spring Data reactive transaction failed during operation execution",
                        DatabaseTransactionException.TransactionState.FAILED,
                        null, // isolation level not applicable to MongoDB
                        null, // timeout not available
                        failureDuration,
                        DatabaseTransactionException.RollbackStatus.SUCCESS, // Spring handles rollback
                        null, // specific operation not available at this level
                        "Spring Data Reactive MongoDB",
                        e);
            }
        } catch (Exception e) {
            Duration failureDuration = Duration.between(transactionStart, LocalDateTime.now());
            if (e instanceof DatabaseTransactionException) {
                throw e;
            }
            throw new DatabaseTransactionException(
                    "MongoDB Spring Data reactive transaction failed to start or commit",
                    DatabaseTransactionException.TransactionState.FAILED,
                    null, // isolation level not applicable to MongoDB
                    null, // timeout not available
                    failureDuration,
                    DatabaseTransactionException.RollbackStatus.SUCCESS, // Spring handles rollback
                    null, // specific operation not available at this level
                    "Spring Data Reactive MongoDB",
                    e);
        } finally {
            executionRuntime.addDependency(new Dependency(ReactiveMongoTemplate.class, mongoTemplate));
            if (clientSession != null) {
                clientSession.close();
            }
        }
    }

    private void commitTransaction(ClientSession clientSession) {
        reactor.core.publisher.Mono.from(clientSession.commitTransaction()).block();
    }

    private void abortTransaction(ClientSession clientSession) {
        reactor.core.publisher.Mono.from(clientSession.abortTransaction()).block();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ReadConcern readConcern = ReadConcern.MAJORITY;
        private ReadPreference readPreference = ReadPreference.primary();
        private WriteConcern writeConcern = WriteConcern.MAJORITY.withJournal(true);
        private ReactiveMongoTemplate mongoTemplate;

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

        public Builder mongoTemplate(ReactiveMongoTemplate mongoTemplate) {
            this.mongoTemplate = mongoTemplate;
            return this;
        }

        public MongoDBSpringDataReactiveTxWrapper build() {
            if (mongoTemplate == null) {
                throw new IllegalArgumentException("ReactiveMongoTemplate is required");
            }
            TransactionOptions txOptions = TransactionOptions.builder()
                    .readConcern(readConcern)
                    .readPreference(readPreference)
                    .writeConcern(writeConcern)
                    .build();
            return new MongoDBSpringDataReactiveTxWrapper(mongoTemplate, txOptions);
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
