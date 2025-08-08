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
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Function;

public class MongoSpringDataTxWrapper implements TransactionWrapper {
    private final TransactionTemplate txTemplate;

    private MongoSpringDataTxWrapper(MongoDatabaseFactory mongoDatabaseFactory, TransactionOptions txOptions) {
        MongoTransactionManager txManager = new MongoTransactionManager(mongoDatabaseFactory, txOptions);

        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.txTemplate.setName("flamingock-transaction");
    }


    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        return txTemplate.execute(status -> {
            T result = operation.apply(executionRuntime);
            if (result instanceof FailedStep) {
                status.setRollbackOnly();
            }
            return result;
        });
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

}
