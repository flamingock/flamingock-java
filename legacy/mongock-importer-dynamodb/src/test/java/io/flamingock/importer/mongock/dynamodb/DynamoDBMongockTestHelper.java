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
package io.flamingock.importer.mongock.dynamodb;

import io.flamingock.common.test.mongock.MongockChangeEntry;
import io.flamingock.common.test.mongock.MongockTestHelper;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;

public class DynamoDBMongockTestHelper implements MongockTestHelper {

    private final DynamoDbTable<MongockDynamoDBAuditEntry> table;

    public DynamoDBMongockTestHelper(DynamoDbClient client, String tableName) {
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(MongockDynamoDBAuditEntry.class));
    }

    @Override
    public void write(MongockChangeEntry entry) {
        table.putItem(convertToMongockDynamoDBAuditEntry(entry));
    }

    @Override
    public int writeAll(List<MongockChangeEntry> entries) {
        for (MongockChangeEntry entry : entries) {
            table.putItem(convertToMongockDynamoDBAuditEntry(entry));
        }
        return entries.size();
    }

    @Override
    public void reset() {
        table.deleteTable();
    }



    private MongockDynamoDBAuditEntry convertToMongockDynamoDBAuditEntry(MongockChangeEntry entry) {
        return new MongockDynamoDBAuditEntry(
                entry.getExecutionId(),
                entry.getChangeId(),
                entry.getAuthor(),
                entry.getTimestamp() != null ? String.valueOf(entry.getTimestamp().getTime()) : null,
                entry.getState() != null ? entry.getState().toString() : null,
                entry.getType() != null ? entry.getType().toString() : null,
                entry.getChangeLogClass(),
                entry.getChangeSetMethod(),
                entry.getMetadata() != null ? entry.getMetadata().toString() : null,
                entry.getExecutionMillis(),
                entry.getExecutionHostname(),
                entry.getErrorTrace(),
                entry.getSystemChange()
        );
    }
}
