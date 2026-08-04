/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Writes audit entries to DynamoDB.
 *
 * <p>Append writes are used while journal events are disabled. Journal-enabled writes use
 * {@link #contributeToTransaction(TransactWriteItemsEnhancedRequest.Builder, AuditEntry)} so the current
 * audit record is committed with its journal event and reservation.</p>
 */
public class DynamoDBAuditWriter implements AuditWriter {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("DynamoDBAuditWriter");

    private final DynamoDBUtil dynamoDBUtil;
    private DynamoDbTable<AuditEntryEntity> table;

    public DynamoDBAuditWriter(DynamoDbClient client) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
    }

    /**
     * Binds this writer to an audit table initialized by {@link DynamoDBAuditor}.
     *
     * @param autoCreate retained for the persistence construction contract; table creation is owned by the auditor
     * @param tableName audit table name
     * @param readCapacityUnits retained for the persistence construction contract
     * @param writeCapacityUnits retained for the persistence construction contract
     */
    public synchronized void initialize(Boolean autoCreate,
                                        String tableName,
                                        long readCapacityUnits,
                                        long writeCapacityUnits) {
        if (table == null) {
            table = dynamoDBUtil.getEnhancedClient().table(tableName, TableSchema.fromBean(AuditEntryEntity.class));
        }
    }

    /**
     * Appends an audit entry using the historical append key.
     *
     * @param auditEntry entry to append
     * @return successful write result
     */
    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        AuditEntryEntity entity = new AuditEntryEntity(auditEntry);
        logger.debug("Saving audit entry with key {}", entity.getPartitionKey());
        table.putItem(PutItemEnhancedRequest.builder(AuditEntryEntity.class)
                .item(entity)
                .build());
        return Result.OK();
    }

    /**
     * Stages a current-state audit write in a caller-owned DynamoDB transaction.
     *
     * @param builder transaction builder receiving the audit write
     * @param auditEntry entry to stage
     */
    void contributeToTransaction(TransactWriteItemsEnhancedRequest.Builder builder, AuditEntry auditEntry) {
        if (table == null) {
            throw new IllegalStateException("DynamoDB audit writer is not initialized");
        }
        AuditEntryEntity entity = new AuditEntryEntity(auditEntry);
        entity.setPartitionKey(auditEntry.getChangeId());
        builder.addPutItem(table, PutItemEnhancedRequest.builder(AuditEntryEntity.class)
                .item(entity)
                .build());
        logger.debug("Staged current-state audit entry with key {}", entity.getPartitionKey());
    }
}
