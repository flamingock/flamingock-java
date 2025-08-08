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
import io.flamingock.internal.core.targets.OngoingTaskStatus;
import io.flamingock.internal.core.targets.OngoingTaskStatusRepository;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
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
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;


public class DynamoDBOnGoingTaskStatusRepository implements OngoingTaskStatusRepository {
    protected static final Logger logger = LoggerFactory.getLogger(DynamoDBOnGoingTaskStatusRepository.class);

    public static final String OPERATION = "operation";
    private static final String TASK_ID = "taskId";
    protected DynamoDbTable<OngoingTaskEntity> onGoingTaskStatusTable;

    public static Builder builder(DynamoDbClient dynamoDbClient) {
        return new Builder(dynamoDbClient);
    }

    public DynamoDBOnGoingTaskStatusRepository(DynamoDbTable<OngoingTaskEntity> onGoingTaskStatusTable) {
        this.onGoingTaskStatusTable = onGoingTaskStatusTable;
    }

    @Override
    public Set<OngoingTaskStatus> getAll() {

        return onGoingTaskStatusTable
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
    public void clean(String taskId, ContextResolver contextResolver) {

        contextResolver.getRequiredDependencyValue(TransactWriteItemsEnhancedRequest.Builder.class)
            .addDeleteItem(
                onGoingTaskStatusTable,
                Key.builder()
                    .partitionValue(taskId)
                    .build()
            );

        logger.trace("removed ongoing task[{}]", taskId);
    }

    @Override
    public void register(OngoingTaskStatus status) {

        onGoingTaskStatusTable.putItem(
            PutItemEnhancedRequest.builder(OngoingTaskEntity.class)
                .item(new OngoingTaskEntity(status.getTaskId(), status.getOperation().toString()))
                .build()
        );

        logger.debug("saved ongoing task[{}]", status.getTaskId());
    }


    public static class Builder {
        protected static DynamoDBUtil dynamoDBUtil;
        private String tableName = "flamingockOnGoingTasks";
        private boolean autoCreate = true;
        protected DynamoDbTable<OngoingTaskEntity> onGoingTaskStatusTable;

        public Builder(DynamoDbClient dynamoDbClient) {
            dynamoDBUtil = new DynamoDBUtil(dynamoDbClient);
        }

        public Builder setTableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder withAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public DynamoDBOnGoingTaskStatusRepository build() {
            if (autoCreate) {
                dynamoDBUtil.createTable(
                    dynamoDBUtil.getAttributeDefinitions("taskId", null),
                    dynamoDBUtil.getKeySchemas("taskId", null),
                    dynamoDBUtil.getProvisionedThroughput(5L, 5L),
                    this.tableName,
                    emptyList(),
                    emptyList()
                );
                this.onGoingTaskStatusTable = dynamoDBUtil.getEnhancedClient().table(this.tableName, TableSchema.fromBean(OngoingTaskEntity.class));
                logger.info("table {} created successfully", this.onGoingTaskStatusTable.tableName());
            } else {
                this.onGoingTaskStatusTable = dynamoDBUtil.getEnhancedClient().table(this.tableName, TableSchema.fromBean(OngoingTaskEntity.class));
            }

            return new DynamoDBOnGoingTaskStatusRepository(this.onGoingTaskStatusTable);
        }
    }
}
