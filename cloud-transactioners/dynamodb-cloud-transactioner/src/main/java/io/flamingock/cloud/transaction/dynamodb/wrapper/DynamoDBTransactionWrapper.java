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

package io.flamingock.cloud.transaction.dynamodb.wrapper;

import io.flamingock.core.local.TransactionManager;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.task.navigation.step.FailedStep;
import io.flamingock.core.transaction.TransactionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.function.Supplier;

public class DynamoDBTransactionWrapper implements TransactionWrapper {
    private static final Logger logger = LoggerFactory.getLogger(DynamoDBTransactionWrapper.class);

    private final TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager;
    private final DynamoDbEnhancedClient client;


    public DynamoDBTransactionWrapper(DynamoDbEnhancedClient client, TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager) {
        this.client = client;
        this.transactionManager = transactionManager;
    }

    @Override
    public <T> T wrapInTransaction(TaskDescriptor taskDescriptor, DependencyInjectable dependencyInjectable, Supplier<T> operation) {
        String sessionId = taskDescriptor.getId();
        TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder = transactionManager.startSession(sessionId);
        Dependency writeRequestBuilderDependency = new Dependency(writeRequestBuilder);
        try {
            dependencyInjectable.addDependency(writeRequestBuilderDependency);
            T result = operation.get();
            if (!(result instanceof FailedStep)) {
                try {
                    client.transactWriteItems(writeRequestBuilder.build());
                } catch (TransactionCanceledException ex) {
                    ex.cancellationReasons().forEach(cancellationReason -> logger.info(cancellationReason.toString()));
                }
            }

            return result;
        } finally {
            transactionManager.closeSession(sessionId);
            dependencyInjectable.removeDependencyByRef(writeRequestBuilderDependency);
        }
    }


}
