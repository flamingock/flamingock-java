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

package io.flamingock.importer.cloud.dynamodb.legacy;

import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.importer.cloud.common.AuditReader;
import io.flamingock.importer.cloud.common.MongockLegacyAuditEntry;
import io.flamingock.importer.cloud.dynamodb.legacy.entities.ChangeEntryEntity;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class DynamoDBLegacyAuditReader implements AuditReader {

    private final DynamoDbTable<ChangeEntryEntity> changeUnitsStorage;

    public DynamoDBLegacyAuditReader(DynamoDbTable<ChangeEntryEntity> changeUnitsStorage) {
        this.changeUnitsStorage = changeUnitsStorage;
    }

    static MongockLegacyAuditEntry toMongockLegacyAuditEntry(ChangeEntryEntity changeEntryDynamoDB) {
        return new MongockLegacyAuditEntry(
                changeEntryDynamoDB.getExecutionId(),
                changeEntryDynamoDB.getChangeId(),
                changeEntryDynamoDB.getState(),
                changeEntryDynamoDB.getType(),
                changeEntryDynamoDB.getAuthor(),
                Date.from(Instant.ofEpochMilli(changeEntryDynamoDB.getTimestamp())).getTime(),
                changeEntryDynamoDB.getChangeLogClass(),
                changeEntryDynamoDB.getChangeSetMethod(),
                changeEntryDynamoDB.getMetadata(),
                changeEntryDynamoDB.getExecutionMillis(),
                changeEntryDynamoDB.getExecutionHostname(),
                changeEntryDynamoDB.getErrorTrace(),
                changeEntryDynamoDB.getSystemChange()
        );
    }

    @Override
    public List<AuditEntry> readAuditEntries() {
        return changeUnitsStorage
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .map(DynamoDBLegacyAuditReader::toMongockLegacyAuditEntry)
                .map(MongockLegacyAuditEntry::toAuditEntry)
                .sorted(Comparator.comparing(AuditEntry::getCreatedAt))
                .collect(Collectors.toList());
    }
}
