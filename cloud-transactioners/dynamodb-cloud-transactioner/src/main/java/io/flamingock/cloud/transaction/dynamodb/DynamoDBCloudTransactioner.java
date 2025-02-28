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

import io.flamingock.cloud.transaction.dynamodb.wrapper.DynamoDBTransactionWrapper;
import io.flamingock.commons.utils.DynamoDBUtil;
import io.flamingock.core.cloud.transaction.TaskWithOngoingStatus;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.local.TransactionManager;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.descriptor.LoadedTask;
import io.flamingock.core.transaction.TransactionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;


public class DynamoDBCloudTransactioner implements CloudTransactioner {
    protected static final Logger logger = LoggerFactory.getLogger(DynamoDBCloudTransactioner.class);
    protected static DynamoDBUtil dynamoDBUtil;
    protected DynamoDbTable<OngoingTaskEntity> table;

    private TransactionWrapper transactionWrapper;

    public DynamoDBCloudTransactioner(DynamoDbClient dynamoDbClient) {
        dynamoDBUtil = new DynamoDBUtil(dynamoDbClient);
    }

    @Override
    public void initialize() {
        TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager = new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder);
        transactionWrapper = new DynamoDBTransactionWrapper(dynamoDBUtil.getEnhancedClient(), transactionManager);

        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions("taskId", null),
                dynamoDBUtil.getKeySchemas("taskId", null),
                dynamoDBUtil.getProvisionedThroughput(5L, 5L),
                OngoingTaskEntity.tableName,
                emptyList(),
                emptyList()
        );

        this.table = dynamoDBUtil.getEnhancedClient().table(OngoingTaskEntity.tableName, TableSchema.fromBean(OngoingTaskEntity.class));

        logger.info("table {} created successfully", table.tableName());
    }

    @Override
    public Set<TaskWithOngoingStatus> getOngoingStatuses() {

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
    public void saveOngoingStatus(TaskWithOngoingStatus status) {

        table.putItem(
                PutItemEnhancedRequest.builder(OngoingTaskEntity.class)
                        .item(new OngoingTaskEntity(status.getTaskId(), status.getOperation().toString()))
                        .build()
        );

        logger.debug("saved ongoing task[{}]", status.getTaskId());
    }

    @Override
    public <T> T wrapInTransaction(LoadedTask loadedTask, DependencyInjectable dependencyInjectable, Supplier<T> operation) {
        return transactionWrapper.wrapInTransaction(loadedTask, dependencyInjectable, operation);
    }

    @Override
    public void close() {
        dynamoDBUtil.getDynamoDbClient().close();
    }

}
