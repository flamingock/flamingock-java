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

package io.flamingock.importer.cloud.mongodb.v4;

import com.mongodb.client.MongoCollection;
import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.importer.cloud.common.MongockLegacyAuditEntry;
import io.flamingock.importer.cloud.common.MongockLegacyImporterConfiguration;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MongoDBLegacyImportConfiguration implements MongockLegacyImporterConfiguration {

    private final MongoCollection<Document> changeUnitsStorage;
    private final EnvironmentId environmentId;
    private final String jwt;
    private final ServiceId serviceId;
    private final String serverHost;

    public MongoDBLegacyImportConfiguration(EnvironmentId environmentId,
                                            ServiceId serviceId,
                                            String jwt,
                                            String serverHost,
                                            MongoCollection<Document> changeUnitsStorage) {
        this.environmentId = environmentId;
        this.serviceId = serviceId;
        this.jwt = jwt;
        this.serverHost = serverHost;
        this.changeUnitsStorage = changeUnitsStorage;
    }

    static MongockLegacyAuditEntry toMongockLegacyAuditEntry(Document document) {
        return new MongockLegacyAuditEntry(
                document.getString("executionId"),
                document.getString("changeId"),
                document.getString("state"),
                document.getString("type"),
                document.getString("author"),
                document.getDate("timestamp").getTime(),
                document.getString("changeLogClass"),
                document.getString("changeSetMethod"),
                document.get("metadata"),
                document.getLong("executionMillis"),
                document.getString("executionHostName"),
                document.getString("errorTrace"),
                document.getBoolean("systemChange")
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
    public List<MongockLegacyAuditEntry> readMongockLegacyAuditEntries() {
        return changeUnitsStorage
                .find()
                .into(new ArrayList<>())
                .stream()
                .map(MongoDBLegacyImportConfiguration::toMongockLegacyAuditEntry)
                .sorted(Comparator.comparing(MongockLegacyAuditEntry::getTimestamp))
                .collect(Collectors.toList());
    }
}
