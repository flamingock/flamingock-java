/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.oss.driver.dynamodb;

import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.oss.driver.dynamodb.internal.entities.AuditEntryEntity;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoClients;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoDBConstants;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;
import java.util.stream.Collectors;

public class DynamoDBTestHelper {
    public final DynamoClients client;

    public DynamoDBTestHelper(DynamoDbClient client) {
        this.client = new DynamoClients(client);
    }

    public boolean tableExists(String tableName) {
        return client.getDynamoDbClient().listTables().tableNames().contains(tableName);
    }

    public List<AuditEntry> getAuditEntriesSorted() {
        return client.getEnhancedClient().table(DynamoDBConstants.AUDIT_LOG_TABLE_NAME, TableSchema.fromBean(AuditEntryEntity.class))
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .sorted((o1, o2) -> o1.getCreatedAt().compareTo(o2.getCreatedAt()))
                .map(AuditEntryEntity::toAuditEntry)
                .collect(Collectors.toList());
    }
}
