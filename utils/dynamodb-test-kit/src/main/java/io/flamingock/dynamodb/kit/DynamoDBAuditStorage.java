/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.dynamodb.kit;

import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;

/**
 * DynamoDB implementation of AuditStorage for real database testing.
 *
 * <p>This implementation provides audit storage functionality backed by actual DynamoDB
 * tables, enabling comprehensive E2E testing with real database operations. It handles
 * table lifecycle management, data operations, and cleanup for isolated testing.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Automatic table creation and deletion for test isolation</li>
 *   <li>Consistent read operations for reliable test assertions</li>
 *   <li>Efficient filtering and querying capabilities</li>
 *   <li>Integration with DynamoDB Enhanced Client for type safety</li>
 * </ul>
 */
public class DynamoDBAuditStorage implements AuditStorage {

    private final DynamoDbClient client;
    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName;
    private final boolean autoCleanup;
    private final Long readCapacityUnits;
    private final Long writeCapacityUnits;
    private final DynamoDbTable<AuditEntryEntity> table;

    public DynamoDBAuditStorage(DynamoDbClient client,
                                String tableName,
                                boolean autoCleanup,
                                Long readCapacityUnits,
                                Long writeCapacityUnits) {
        this.client = client;
        this.enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        this.tableName = tableName;
        this.autoCleanup = autoCleanup;
        this.readCapacityUnits = readCapacityUnits;
        this.writeCapacityUnits = writeCapacityUnits;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(AuditEntryEntity.class));
    }

    @Override
    public void addAuditEntry(AuditEntry auditEntry) {
        AuditEntryEntity entity = AuditEntryEntity.fromAuditEntry(auditEntry);
        table.putItem(entity);
    }

    @Override
    public List<AuditEntry> getAuditEntries() {
        SdkIterable<AuditEntryEntity> items = table.scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build())
                .items();
        return items
                .stream()
                .sorted()
                .map(AuditEntryEntity::toAuditEntry)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEntry> getAuditEntriesForChange(String changeId) {
        // Use query operation for efficient retrieval by change ID
        return table.scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .filterExpression(buildFilterExpression(KEY_CHANGE_ID, changeId))
                        .build())
                .items()
                .stream()
                .sorted()
                .map(AuditEntryEntity::toAuditEntry)
                .collect(Collectors.toList());
    }

    @Override
    public long countAuditEntriesWithStatus(AuditEntry.Status status) {
        return table.scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .filterExpression(buildFilterExpression(KEY_STATE, status.name()))
                        .build())
                .items()
                .stream()
                .count();
    }

    @Override
    public boolean hasAuditEntries() {
        // Efficient check using scan with limit
        return table.scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .limit(1)
                        .build())
                .items()
                .stream()
                .findFirst()
                .isPresent();
    }

    @Override
    public void clear() {
        if (autoCleanup) {
            // Delete all items from the table using unique ID
            table.scan().items().forEach(item ->
                    table.deleteItem(Key.builder()
                            .partitionValue(item.getPartitionKey())
                            .build())
            );
        }
    }

    /**
     * Builds a filter expression for DynamoDB scan operations.
     */
    private Expression buildFilterExpression(String attributeName, String value) {
        return Expression.builder()
                .expression("#attr = :val")
                .putExpressionName("#attr", attributeName)
                .putExpressionValue(":val", AttributeValue.builder().s(value).build())
                .build();
    }
}