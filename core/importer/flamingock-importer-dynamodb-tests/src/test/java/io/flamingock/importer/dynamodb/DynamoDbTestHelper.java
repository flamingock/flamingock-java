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
package io.flamingock.importer.dynamodb;

import io.flamingock.internal.common.core.audit.AuditEntry;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class DynamoDbTestHelper {

    private final DynamoDbClient client;
    private final String tableName;
    private final DynamoDbTable<DynamoDbChangeEntry> table;

    public DynamoDbTestHelper(DynamoDbClient client, String tableName) {
        this.client = client;
        this.tableName = tableName;
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();

        this.table = enhancedClient.table(tableName, TableSchema.fromBean(DynamoDbChangeEntry.class));
    }

    public void ensureTableExists() {
        ListTablesResponse tables = client.listTables();
        if (!tables.tableNames().contains(tableName)) {
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
                            ProvisionedThroughput.builder()
                                    .readCapacityUnits(5L)
                                    .writeCapacityUnits(5L)
                                    .build()
                    )
                    .build());
            // Wait for table to be active
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
            key.put("executionId", item.get("executionId"));
            key.put("changeId", item.get("changeId"));
            client.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build());
        }
    }

    public void insertChangeEntries(List<DynamoDbChangeEntry> entries) {
        for (DynamoDbChangeEntry entry : entries) {
            table.putItem(entry);
        }
    }

    public List<AuditEntry> getAuditEntriesSorted() {
        List<DynamoDbChangeEntry> entries = new ArrayList<>();
        table.scan().items().forEach(entries::add);
        return entries.stream()
                .map(DynamoDbChangeEntry::toAuditEntry)
                .sorted()
                .collect(Collectors.toList());
    }
}