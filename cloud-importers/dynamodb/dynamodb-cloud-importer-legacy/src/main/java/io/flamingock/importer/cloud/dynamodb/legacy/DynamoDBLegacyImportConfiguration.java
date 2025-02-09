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

import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.importer.cloud.common.ImporterConfiguration;
import io.flamingock.importer.cloud.common.MongockLegacyAuditEntry;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class DynamoDBLegacyImportConfiguration implements ImporterConfiguration {

    private final DynamoDbTable<ChangeEntry> changeUnitsStorage;
    private final EnvironmentId environmentId;
    private final String jwt;
    private final ServiceId serviceId;
    private final String serverHost;

    public DynamoDBLegacyImportConfiguration(EnvironmentId environmentId,
                                             ServiceId serviceId,
                                             String jwt,
                                             String serverHost,
                                             DynamoDbTable<ChangeEntry> changeUnitsStorage) {
        this.environmentId = environmentId;
        this.serviceId = serviceId;
        this.jwt = jwt;
        this.serverHost = serverHost;
        this.changeUnitsStorage = changeUnitsStorage;
    }

    static MongockLegacyAuditEntry toMongockLegacyAuditEntry(ChangeEntry changeEntryDynamoDB) {
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
    public EnvironmentId getEnvironmentId() {
        return environmentId;
    }

    @Override
    public ServiceId getServiceId() {
        return serviceId;
    }

    @Override
    public String getJwt() {
        return jwt;
    }

    @Override
    public String getServerHost() {
        return serverHost;
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
                .map(DynamoDBLegacyImportConfiguration::toMongockLegacyAuditEntry)
                .map(MongockLegacyAuditEntry::toAuditEntry)
                .sorted(Comparator.comparing(AuditEntry::getCreatedAt))
                .collect(Collectors.toList());
    }
}
