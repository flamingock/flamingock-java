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

package io.flamingock.importer.cloud.mongodb.v4.legacy;

import com.mongodb.client.MongoCollection;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.importer.cloud.common.AuditReader;
import io.flamingock.importer.cloud.common.MongockLegacyAuditEntry;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MongoDBLegacyAuditReader implements AuditReader {

    private final MongoCollection<Document> changeUnitsStorage;

    public MongoDBLegacyAuditReader(MongoCollection<Document> changeUnitsStorage) {
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
    public List<AuditEntry> readAuditEntries() {
        return changeUnitsStorage
                .find()
                .into(new ArrayList<>())
                .stream()
                .map(MongoDBLegacyAuditReader::toMongockLegacyAuditEntry)
                .map(MongockLegacyAuditEntry::toAuditEntry)
                .sorted(Comparator.comparing(AuditEntry::getCreatedAt))
                .collect(Collectors.toList());
    }
}
