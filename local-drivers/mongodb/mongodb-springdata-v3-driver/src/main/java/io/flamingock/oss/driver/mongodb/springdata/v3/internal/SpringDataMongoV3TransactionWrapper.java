/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.oss.driver.mongodb.springdata.v3.internal;

import com.mongodb.TransactionOptions;

import io.flamingock.core.task.navigation.step.FailedStep;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.descriptor.LoadedTask;
import io.flamingock.core.transaction.TransactionWrapper;
import io.flamingock.cloud.transaction.mongodb.sync.v4.cofig.ReadWriteConfiguration;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.function.Supplier;

public class SpringDataMongoV3TransactionWrapper implements TransactionWrapper {

    private final MongoTransactionManager txManager;

    SpringDataMongoV3TransactionWrapper(MongoTemplate mongoTemplate, ReadWriteConfiguration readWriteConfiguration) {
        this.txManager = new MongoTransactionManager(mongoTemplate.getMongoDatabaseFactory(), 
                                                    TransactionOptions.builder()
                                                                        .readConcern(readWriteConfiguration.getReadConcern())
                                                                        .readPreference(readWriteConfiguration.getReadPreference())
                                                                        .writeConcern(readWriteConfiguration.getWriteConcern())
                                                                        .build());
    }

    @Override
    public <T> T wrapInTransaction(LoadedTask taskDescriptor, DependencyInjectable dependencyInjectable, Supplier<T> operation) {
        TransactionStatus txStatus = getTxStatus(txManager);
        T result = operation.get();
        if (result instanceof FailedStep) {
            txManager.rollback(txStatus);
        } else {
            txManager.commit(txStatus);
        }
        return result;
    }

    protected TransactionStatus getTxStatus(PlatformTransactionManager txManager) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        // explicitly setting the transaction name is something that can be done only
        // programmatically
        def.setName("flamingock-transaction-spring-data-3");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return txManager.getTransaction(def);
    }
}
