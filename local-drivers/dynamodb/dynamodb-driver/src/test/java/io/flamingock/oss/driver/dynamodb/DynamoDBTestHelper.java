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
import io.flamingock.oss.driver.dynamodb.internal.mongock.ChangeEntryDynamoDB;
import io.flamingock.commons.utils.DynamoDBUtil;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DynamoDBTestHelper {
    public final DynamoDBUtil dynamoDBUtil;

    public DynamoDBTestHelper(DynamoDbClient client) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
    }

    public boolean tableExists(String tableName) {
        return dynamoDBUtil.getDynamoDbClient().listTables().tableNames().contains(tableName);
    }

    public List<AuditEntry> getAuditEntriesSorted(String auditLogTable) {
        return dynamoDBUtil.getEnhancedClient().table(auditLogTable, TableSchema.fromBean(AuditEntryEntity.class))
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .sorted(Comparator.comparing(AuditEntryEntity::getCreatedAt))
                .map(AuditEntryEntity::toAuditEntry)
                .collect(Collectors.toList());
    }

    public List<ChangeEntryDynamoDB> getChangeEntriesSorted(String auditLogTable) {
        return dynamoDBUtil.getEnhancedClient().table(auditLogTable, TableSchema.fromBean(ChangeEntryDynamoDB.class))
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .sorted(Comparator.comparing(ChangeEntryDynamoDB::getTimestamp))
                .collect(Collectors.toList());
    }
}
