/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.cloud.transaction.dynamodb;

import io.flamingock.commons.utils.DynamoDBUtil;
import io.flamingock.core.cloud.api.transaction.OngoingStatus;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.local.TransactionManager;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.task.navigation.step.FailedStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class DynamoDBCloudTransactioner implements CloudTransactioner {
    protected static final Logger logger = LoggerFactory.getLogger(DynamoDBCloudTransactioner.class);
    protected static DynamoDBUtil dynamoDBUtil;
    protected static TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager = new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder);
    protected DynamoDbTable<OngoingTaskEntity> table;

    public DynamoDBCloudTransactioner setDynamoDbClient(DynamoDbClient dynamoDbClient) {
        dynamoDBUtil = new DynamoDBUtil(dynamoDbClient);
        return this;
    }

    @Override
    public void initialize() {

        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions("taskId", null),
                dynamoDBUtil.getKeySchemas("taskId", null),
                dynamoDBUtil.getProvisionedThroughput(5L, 5L),
                OngoingTaskEntity.tableName,
                emptyList(),
                emptyList()
        );

        this.table = dynamoDBUtil.getEnhancedClient().table(OngoingTaskEntity.tableName, TableSchema.fromBean(OngoingTaskEntity.class));

        logger.info("table {} created successfully", OngoingTaskEntity.tableName);
    }

    @Override
    public Set<OngoingStatus> getOngoingStatuses() {

        return table
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .map(OngoingTaskEntity::toOngoingStatus)
                .collect(Collectors.toSet());
    }

    @Override
    public void cleanOngoingStatus(String taskId) {

        table.deleteItem(
                Key.builder()
                        .partitionValue(taskId)
                        .build()
        );

        logger.trace("removed ongoing task[{}]", taskId);
    }

    @Override
    public void saveOngoingStatus(OngoingStatus status) {

        table.putItem(
                PutItemEnhancedRequest.builder(OngoingTaskEntity.class)
                        .item(new OngoingTaskEntity(status.getTaskId(), status.getOperation().toString()))
                        .build()
        );

        logger.debug("saved ongoing task[{}]", status.getTaskId());
    }

    @Override
    public <T> T wrapInTransaction(TaskDescriptor taskDescriptor, DependencyInjectable dependencyInjectable, Supplier<T> operation) {
        String sessionId = taskDescriptor.getId();
        try {
            TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder = transactionManager.startSession(sessionId);
            dependencyInjectable.addDependency(writeRequestBuilder);
            T result = operation.get();
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

    @Override
    public void close() {
        dynamoDBUtil.getDynamoDbClient().close();
    }

}
