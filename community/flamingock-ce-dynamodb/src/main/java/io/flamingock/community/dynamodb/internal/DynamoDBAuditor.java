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
package io.flamingock.community.dynamodb.internal;

import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.store.audit.domain.AuditSnapshotMapBuilder;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.transaction.TransactionManager;

import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.dynamodb.DynamoDBConstants;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class DynamoDBAuditor implements LifecycleAuditWriter, AuditReader {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("DynamoAuditor");

    private final DynamoDBUtil dynamoDBUtil;
    protected DynamoDbTable<AuditEntryEntity> table;
    protected final TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager;

    protected DynamoDBAuditor(DynamoDBTargetSystem targetSystem) {
        this.dynamoDBUtil = new DynamoDBUtil(targetSystem.getClient());
        this.transactionManager = targetSystem.getTxManager();
    }

    protected void initialize(Boolean autoCreate, String tableName, long readCapacityUnits, long writeCapacityUnits) {
        if (autoCreate) {
            dynamoDBUtil.createTable(
                    dynamoDBUtil.getAttributeDefinitions(DynamoDBConstants.AUDIT_LOG_PK, null),
                    dynamoDBUtil.getKeySchemas(DynamoDBConstants.AUDIT_LOG_PK, null),
                    dynamoDBUtil.getProvisionedThroughput(readCapacityUnits, writeCapacityUnits),
                    tableName,
                    emptyList(),
                    emptyList()
            );
        }
        table = dynamoDBUtil.getEnhancedClient().table(tableName, TableSchema.fromBean(AuditEntryEntity.class));
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        AuditEntryEntity entity = new AuditEntryEntity(auditEntry);
        logger.debug("Saving audit entry with key {}", entity.getPartitionKey());

        TransactWriteItemsEnhancedRequest.Builder transactionBuilder = transactionManager
                .getSession(auditEntry.getTaskId())
                .orElse(null);

        if (transactionBuilder != null) {
            logger.debug("Adding PUT item to transaction for key {}", entity.getPartitionKey());
            transactionBuilder.addPutItem(table, entity);
        } else {
            try {
                table.putItem(
                        PutItemEnhancedRequest.builder(AuditEntryEntity.class)
                                .item(entity)
                                .build()
                );
            } catch (ConditionalCheckFailedException ex) {
                logger.warn("Error saving audit entry with key {}", entity.getPartitionKey(), ex);
                throw ex;
            }
        }

        return Result.OK();
    }

    @Override
    public Map<String, AuditEntry> getAuditSnapshotByChangeId() {
        AuditSnapshotMapBuilder builder = new AuditSnapshotMapBuilder();
        table
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .map(AuditEntryEntity::toAuditEntry)
                .collect(Collectors.toList())
                .forEach(builder::addEntry);
        return builder.build();
    }

}
