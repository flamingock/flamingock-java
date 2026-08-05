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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.util.dynamodb.DynamoDBConstants;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class DynamoDBAuditRepository {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("DynamoDBAuditRepository");

    private final DynamoDBUtil dynamoDBUtil;
    protected DynamoDbTable<AuditEntryEntity> table;

    public DynamoDBAuditRepository(DynamoDbClient client) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
    }

    public synchronized void initialize(Boolean autoCreate,
                                        String tableName,
                                        long readCapacityUnits,
                                        long writeCapacityUnits) {
        if (table != null) {
            return;
        }
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
        validateSchema(tableName);
        table = dynamoDBUtil.getEnhancedClient().table(tableName, TableSchema.fromBean(AuditEntryEntity.class));
    }

    private void validateSchema(String tableName) {
        TableDescription description;
        try {
            description = dynamoDBUtil.getDynamoDBClient().describeTable(
                    DescribeTableRequest.builder().tableName(tableName).build()).table();
        } catch (ResourceNotFoundException exception) {
            throw new IllegalStateException("DynamoDB audit table '" + tableName
                    + "' is missing or has an invalid schema", exception);
        }

        boolean keyValid = description.keySchema() != null
                && description.keySchema().size() == 1
                && hasKey(description.keySchema().get(0), DynamoDBConstants.AUDIT_LOG_PK, KeyType.HASH);
        boolean attributeValid = description.attributeDefinitions() != null
                && description.attributeDefinitions().stream()
                .anyMatch(attribute -> hasAttribute(attribute, DynamoDBConstants.AUDIT_LOG_PK, ScalarAttributeType.S));
        if (!keyValid || !attributeValid) {
            throw new IllegalStateException("DynamoDB audit table '" + tableName
                    + "' has an invalid key schema");
        }
    }

    private boolean hasKey(KeySchemaElement key, String name, KeyType type) {
        return key != null && name.equals(key.attributeName()) && type == key.keyType();
    }

    private boolean hasAttribute(AttributeDefinition attribute, String name, ScalarAttributeType type) {
        return attribute != null && name.equals(attribute.attributeName()) && type == attribute.attributeType();
    }

    /**
     * Appends an audit entry using the historical append key.
     *
     * @param auditEntry entry to append
     * @return successful write result
     */
    Result writeEntry(AuditEntry auditEntry) {
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
    Result contributeToTransaction(TransactWriteItemsEnhancedRequest.Builder builder, AuditEntry auditEntry) {
        if (table == null) {
            throw new IllegalStateException("DynamoDB audit writer is not initialized");
        }
        AuditEntryEntity entity = new AuditEntryEntity(auditEntry);
        entity.setPartitionKey(auditEntry.getChangeId());
        builder.addPutItem(table, PutItemEnhancedRequest.builder(AuditEntryEntity.class)
            .item(entity)
            .build());
        logger.debug("Staged current-state audit entry with key {}", entity.getPartitionKey());
        return Result.OK();
    }

    public List<AuditEntry> getAuditHistory() {
        return table
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .map(AuditEntryEntity::toAuditEntry)
                .collect(Collectors.toList());
    }
}
