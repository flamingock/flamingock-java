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
package io.flamingock.importer.mongock.dynamodb;

import io.flamingock.importer.dynamodb.MongockDynamoDBAuditEntry;
import io.flamingock.importer.dynamodb.dynamodb.DynamoDBAuditEntryEntity;
import io.flamingock.internal.common.core.audit.AuditEntry;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;

public class DynamoDBTestHelper {

    private final DynamoDbClient client;
    private final String tableName;
    private final DynamoDbTable<?> table;

    public DynamoDBTestHelper(DynamoDbClient client, String tableName) {
        this.client = client;
        this.tableName = tableName;
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();

        if (DEFAULT_AUDIT_STORE_NAME.equals(tableName)) {
            this.table = enhancedClient.table(tableName, TableSchema.fromBean(DynamoDBAuditEntryEntity.class));
        } else {
            this.table = enhancedClient.table(tableName, TableSchema.fromBean(MongockDynamoDBAuditEntry.class));
        }
    }

    public void ensureTableExists() {
        ListTablesResponse tables = client.listTables();
        if (!tables.tableNames().contains(tableName)) {
            if (DEFAULT_AUDIT_STORE_NAME.equals(tableName)) {
                client.createTable(CreateTableRequest.builder()
                        .tableName(tableName)
                        .keySchema(
                                KeySchemaElement.builder().attributeName("partitionKey").keyType(KeyType.HASH).build()
                        )
                        .attributeDefinitions(
                                AttributeDefinition.builder().attributeName("partitionKey").attributeType(ScalarAttributeType.S).build()
                        )
                        .provisionedThroughput(
                                ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build()
                        )
                        .build());
            } else {
                client.createTable(CreateTableRequest.builder()
                        .tableName(tableName)
                        .keySchema(
                                KeySchemaElement.builder().attributeName("executionId").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("changeId").keyType(KeyType.RANGE).build()
                        )
                        .attributeDefinitions(
                                AttributeDefinition.builder().attributeName("executionId").attributeType(ScalarAttributeType.S).build(),
                                AttributeDefinition.builder().attributeName("changeId").attributeType(ScalarAttributeType.S).build()
                        )
                        .provisionedThroughput(
                                ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build()
                        )
                        .build());
            }
            waitForTableActive();
        }
    }

    private void waitForTableActive() {
        while (true) {
            DescribeTableResponse resp = client.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
            if (resp.table().tableStatus() == TableStatus.ACTIVE) {
                break;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
        }
    }

    public void resetTable() {
        ScanRequest scanRequest = ScanRequest.builder().tableName(tableName).build();
        ScanResponse scanResponse = client.scan(scanRequest);
        for (Map<String, AttributeValue> item : scanResponse.items()) {
            Map<String, AttributeValue> key = new HashMap<>();
            if (DEFAULT_AUDIT_STORE_NAME.equals(tableName)) {
                key.put("partitionKey", item.get("partitionKey"));
            } else {
                key.put("executionId", item.get("executionId"));
                key.put("changeId", item.get("changeId"));
            }
            client.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build());
        }
    }

    public void insertChangeEntries(List<MongockDynamoDBAuditEntry> entries) {
        if (DEFAULT_AUDIT_STORE_NAME.equals(tableName)) {
            throw new UnsupportedOperationException("insertChangeEntries is only for change log tables");
        }
        DynamoDbTable<MongockDynamoDBAuditEntry> changeTable = (DynamoDbTable<MongockDynamoDBAuditEntry>) table;
        for (MongockDynamoDBAuditEntry entry : entries) {
            changeTable.putItem(entry);
        }
    }

    public List<AuditEntry> getAuditEntriesSorted() {
        if (DEFAULT_AUDIT_STORE_NAME.equals(tableName)) {
            DynamoDbTable<DynamoDBAuditEntryEntity> auditTable = (DynamoDbTable<DynamoDBAuditEntryEntity>) table;
            List<DynamoDBAuditEntryEntity> entities = new ArrayList<>();
            auditTable.scan().items().forEach(entities::add);
            return entities.stream()
                    .map(DynamoDBAuditEntryEntity::toAuditEntry)
                    .sorted()
                    .collect(Collectors.toList());
        } else {
            DynamoDbTable<MongockDynamoDBAuditEntry> changeTable = (DynamoDbTable<MongockDynamoDBAuditEntry>) table;
            List<MongockDynamoDBAuditEntry> entries = new ArrayList<>();
            changeTable.scan().items().forEach(entries::add);
            return entries.stream()
                    .map(MongockDynamoDBAuditEntry::toAuditEntry)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
