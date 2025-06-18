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

package io.flamingock.importer.adapter.dynamodb.outdated;


import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.api.annotations.Execution;
import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.importer.model.MongockChangeEntry;
import io.flamingock.importer.model.MongockChangeState;
import io.flamingock.importer.model.MongockChangeType;
import io.flamingock.internal.common.core.audit.AuditEntry;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@ChangeUnit(id = "mongock-local-legacy-importer-dynamodb", order = "001")
public class MongockImporterChangeUnit {


    private static MongockChangeEntry toChangeEntry(ChangeEntryDynamoDB changeEntryDynamoDB) {
        Date timestamp = Date.from(Instant.ofEpochMilli(changeEntryDynamoDB.getTimestamp()));

        return new MongockChangeEntry(
                changeEntryDynamoDB.getExecutionId(),
                changeEntryDynamoDB.getChangeId(),
                changeEntryDynamoDB.getAuthor(),
                timestamp,
                MongockChangeState.valueOf(changeEntryDynamoDB.getState()),
                MongockChangeType.valueOf(changeEntryDynamoDB.getType()),
                changeEntryDynamoDB.getChangeLogClass(),
                changeEntryDynamoDB.getChangeSetMethod(),
                changeEntryDynamoDB.getMetadata(),
                changeEntryDynamoDB.getExecutionMillis(),
                changeEntryDynamoDB.getExecutionHostname(),
                changeEntryDynamoDB.getErrorTrace(),
                changeEntryDynamoDB.getSystemChange(),
                timestamp
        );
    }

    private static AuditEntry toAuditEntry(MongockChangeEntry changeEntry) {
        LocalDateTime timestamp = Instant.ofEpochMilli(changeEntry.getTimestamp().getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (changeEntry.getState() == MongockChangeState.IGNORED) {
            return null;
        }
        return new AuditEntry(
                changeEntry.getExecutionId(),
                null,
                changeEntry.getChangeId(),
                changeEntry.getAuthor(),
                timestamp,
                changeEntry.getState().toAuditStatus(),
                changeEntry.getType().toAuditType(),
                changeEntry.getChangeLogClass(),
                changeEntry.getChangeSetMethod(),
                changeEntry.getExecutionMillis(),
                changeEntry.getExecutionHostname(),
                changeEntry.getMetadata(),
                changeEntry.getSystemChange(),
                changeEntry.getErrorTrace()
        );
    }

    @Execution
    public void execution(InternalMongockImporterConfiguration configuration) {
        DynamoDbTable<ChangeEntryDynamoDB> sourceTable = configuration.getSourceTable();
        if (sourceTable == null) {
            throw new RuntimeException("SourceTable not injected");
        }
        AuditWriter auditWriter = configuration.getAuditWriter();
        if (auditWriter == null) {
            throw new RuntimeException("AuditWriter not injected");
        }

        List<AuditEntry> collect = sourceTable
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .map(MongockImporterChangeUnit::toChangeEntry)
                .map(MongockImporterChangeUnit::toAuditEntry)
                .collect(Collectors.toList());
        collect.forEach(auditWriter::writeEntry);

    }
}
