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

import io.flamingock.importer.ImporterAdapter;
import io.flamingock.internal.common.core.audit.AuditEntry;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DynamoDBImporterAdapter implements ImporterAdapter {

    private final DynamoDbTable<MongockDynamoDBAuditEntry> sourceTable;

    public DynamoDBImporterAdapter(DynamoDbClient client, String tableName) {
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();
        this.sourceTable = enhancedClient.table(tableName, TableSchema.fromBean(MongockDynamoDBAuditEntry.class));
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        List<MongockDynamoDBAuditEntry> entries = StreamSupport
                .stream(sourceTable.scan().items().spliterator(), false)
                .collect(Collectors.toList());

        return entries.stream()
                .map(MongockDynamoDBAuditEntry::toAuditEntry)
                .collect(Collectors.toList());
    }
}
