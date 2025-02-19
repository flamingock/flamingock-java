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

package io.flamingock.importer.cloud.dynamodb.local;

import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.importer.cloud.common.AuditReader;
import io.flamingock.importer.cloud.dynamodb.local.entities.AuditEntryEntity;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DynamoDBLocalAuditReader implements AuditReader {

    private final DynamoDbTable<AuditEntryEntity> changeUnitsStorage;

    public DynamoDBLocalAuditReader(DynamoDbTable<AuditEntryEntity> changeUnitsStorage) {
        this.changeUnitsStorage = changeUnitsStorage;
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
                .map(AuditEntryEntity::toAuditEntry)
                .sorted(Comparator.comparing(AuditEntry::getCreatedAt))
                .collect(Collectors.toList());
    }
}
