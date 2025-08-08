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
package io.flamingock.targetsystem.dynamodb;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.context.InjectableContextProvider;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.context.DependencyInjectable;
import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.function.Function;
import java.util.function.Supplier;

public class DynamoDBTxWrapper implements TransactionWrapper {
    private static final Logger logger = LoggerFactory.getLogger(DynamoDBTxWrapper.class);

    private final TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager;
    private final DynamoDBUtil dynamoDBUtil;

    public DynamoDBTxWrapper(DynamoDbClient client) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
        transactionManager = new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder);
    }

    @Deprecated
    public DynamoDBTxWrapper(DynamoDbClient client, TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
        this.transactionManager = transactionManager;
    }

    @Override
    public <T> T wrapInTransaction(TaskDescriptor loadedTask, InjectableContextProvider injectableContextProvider, Function<ContextResolver, T> operation) {
        String sessionId = loadedTask.getId();
        TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder = transactionManager.startSession(sessionId);
        Dependency writeRequestBuilderDependency = new Dependency(writeRequestBuilder);
        try {
            injectableContextProvider.addDependency(writeRequestBuilderDependency);
            T result = operation.apply(injectableContextProvider.getContext());
            if (!(result instanceof FailedStep)) {
                try {
                    dynamoDBUtil.getEnhancedClient().transactWriteItems(writeRequestBuilder.build());
                } catch (TransactionCanceledException ex) {
                    ex.cancellationReasons().forEach(cancellationReason -> logger.info(cancellationReason.toString()));
                }
            }

            return result;
        } finally {
            transactionManager.closeSession(sessionId);
        }
    }


}
