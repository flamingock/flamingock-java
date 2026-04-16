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

import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
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


public class DynamoDBTargetSystemAuditMarker implements TargetSystemAuditMarker {
    protected static final Logger logger = FlamingockLoggerFactory.getLogger("DynamoAuditMarker");

    public static final String OPERATION = "operation";
    private static final String CHANGE_ID = "changeId";
    private final TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager;
    protected DynamoDbTable<OngoingChangeEntity> onGoingChangeStatusTable;

    public static Builder builder(DynamoDbClient dynamoDBClient,
                                  TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager) {
        return new Builder(dynamoDBClient, txManager);
    }

    public DynamoDBTargetSystemAuditMarker(DynamoDbTable<OngoingChangeEntity> onGoingChangeStatusTable,
                                           TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager) {
        this.onGoingChangeStatusTable = onGoingChangeStatusTable;
        this.txManager = txManager;
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {

        return onGoingChangeStatusTable
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .map(OngoingChangeEntity::toOngoingStatus)
                .collect(Collectors.toSet());
    }

    @Override
    public void clearMark(String changeId) {
        onGoingChangeStatusTable.deleteItem(
                DeleteItemEnhancedRequest.builder()
                        .key(Key.builder().partitionValue(changeId).build())
                        .build()
        );
        logger.trace("removed ongoing change[{}]", changeId);
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {
        TransactWriteItemsEnhancedRequest.Builder tx =
                txManager.getSessionOrThrow(auditMark.getChangeId());

        OngoingChangeEntity entity =
                new OngoingChangeEntity(auditMark.getChangeId(), auditMark.getOperation().toString());

        tx.addPutItem(onGoingChangeStatusTable, entity);

        logger.debug("queued local audit mark [{}] into transaction session", auditMark.getChangeId());
    }


    public static class Builder {
        protected static DynamoDBUtil dynamoDBUtil;
        private final TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager;
        private String tableName = CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME;
        private boolean autoCreate = true;
        protected DynamoDbTable<OngoingChangeEntity> onGoingChangeStatusTable;

        public Builder(DynamoDbClient dynamoDBClient, TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager) {
            dynamoDBUtil = new DynamoDBUtil(dynamoDBClient);
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

        public DynamoDBTargetSystemAuditMarker build() {
            if (autoCreate) {
                dynamoDBUtil.createTable(
                        dynamoDBUtil.getAttributeDefinitions("changeId", null),
                        dynamoDBUtil.getKeySchemas("changeId", null),
                        dynamoDBUtil.getProvisionedThroughput(5L, 5L),
                        this.tableName,
                        emptyList(),
                        emptyList()
                );
                this.onGoingChangeStatusTable = dynamoDBUtil.getEnhancedClient().table(this.tableName, TableSchema.fromBean(OngoingChangeEntity.class));
                logger.info("table {} created successfully", this.onGoingChangeStatusTable.tableName());
            } else {
                this.onGoingChangeStatusTable = dynamoDBUtil.getEnhancedClient().table(this.tableName, TableSchema.fromBean(OngoingChangeEntity.class));
            }

            return new DynamoDBTargetSystemAuditMarker(this.onGoingChangeStatusTable, txManager);
        }
    }
}
