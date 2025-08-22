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

import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;


public class DynamoDbTargetSystemAuditMarker implements TargetSystemAuditMarker {
    protected static final Logger logger = FlamingockLoggerFactory.getLogger("DynamoAuditMarker");

    public static final String OPERATION = "operation";
    private static final String TASK_ID = "taskId";
    private final TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager;
    protected DynamoDbTable<OngoingTaskEntity> onGoingTaskStatusTable;

    public static Builder builder(DynamoDbClient dynamoDbClient,
                                  TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager) {
        return new Builder(dynamoDbClient, txManager);
    }

    public DynamoDbTargetSystemAuditMarker(DynamoDbTable<OngoingTaskEntity> onGoingTaskStatusTable,
                                           TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager) {
        this.onGoingTaskStatusTable = onGoingTaskStatusTable;
        this.txManager = txManager;
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {

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
    public void clearMark(String changeId) {
        onGoingTaskStatusTable.deleteItem(
                DeleteItemEnhancedRequest.builder()
                        .key(Key.builder().partitionValue(changeId).build())
                        .build()
        );
        logger.trace("removed ongoing task[{}]", changeId);
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {
        TransactWriteItemsEnhancedRequest.Builder tx =
                txManager.getSessionOrThrow(auditMark.getTaskId());

        OngoingTaskEntity entity =
                new OngoingTaskEntity(auditMark.getTaskId(), auditMark.getOperation().toString());

        tx.addPutItem(onGoingTaskStatusTable, entity);

        logger.debug("queued local audit mark [{}] into transaction session", auditMark.getTaskId());
    }


    public static class Builder {
        protected static DynamoDBUtil dynamoDBUtil;
        private final TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager;
        private String tableName = "flamingockOnGoingTasks";
        private boolean autoCreate = true;
        protected DynamoDbTable<OngoingTaskEntity> onGoingTaskStatusTable;

        public Builder(DynamoDbClient dynamoDbClient, TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager) {
            dynamoDBUtil = new DynamoDBUtil(dynamoDbClient);
            this.txManager = txManager;
        }

        public Builder setTableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder withAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public DynamoDbTargetSystemAuditMarker build() {
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

            return new DynamoDbTargetSystemAuditMarker(this.onGoingTaskStatusTable, txManager);
        }
    }
}
