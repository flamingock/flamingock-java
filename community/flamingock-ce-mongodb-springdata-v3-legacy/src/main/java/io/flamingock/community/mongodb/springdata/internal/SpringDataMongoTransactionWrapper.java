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
package io.flamingock.community.mongodb.springdata.internal;

import com.mongodb.TransactionOptions;
import io.flamingock.community.mongodb.sync.internal.ReadWriteConfiguration;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Function;

public class SpringDataMongoTransactionWrapper implements TransactionWrapper {

    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate txTemplate;

    SpringDataMongoTransactionWrapper(MongoTemplate mongoTemplate, ReadWriteConfiguration readWriteConfiguration) {
        this.mongoTemplate = mongoTemplate;
        MongoTransactionManager txManager = new MongoTransactionManager(
                mongoTemplate.getMongoDatabaseFactory(),
                TransactionOptions.builder()
                        .readConcern(readWriteConfiguration.getReadConcern())
                        .readPreference(readWriteConfiguration.getReadPreference())
                        .writeConcern(readWriteConfiguration.getWriteConcern())
                        .build()
        );

        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.txTemplate.setName("flamingock-transaction");
    }


    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        executionRuntime.addDependency(mongoTemplate);
        return txTemplate.execute(status -> {
            T result = operation.apply(executionRuntime);
            if (result instanceof FailedStep) {
                status.setRollbackOnly();
            }
            return result;
        });
    }
}
