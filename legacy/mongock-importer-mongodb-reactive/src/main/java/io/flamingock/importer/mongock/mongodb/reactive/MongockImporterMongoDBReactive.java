/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.importer.mongock.mongodb.reactive;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.reactive.util.PublisherSync;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Mirrors io.flamingock.importer.mongock.mongodb.MongockImporterMongoDB (sync importer),
 * reading against the reactive streams driver. AuditHistoryReader#getAuditHistory() is a
 * synchronous contract (MongockImportChange calls it directly, no reactive path), so the
 * reactive find() is bridged with PublisherSync — same one-shot blocking pattern used
 * throughout this branch (target-system database-name resolution, migration change-unit
 * saves).
 */
public class MongockImporterMongoDBReactive implements AuditHistoryReader {

    private static final Logger logger = LoggerFactory.getLogger("MongockImporter");

    private final MongoCollection<Document> sourceCollection;

    public MongockImporterMongoDBReactive(MongoDatabase mongoDatabase, String collectionName) {
        this.sourceCollection = mongoDatabase.getCollection(collectionName);
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return PublisherSync.collect(sourceCollection.find())
                .stream()
                .map(MongockImporterMongoDBReactive::toAuditEntry)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    private static AuditEntry toAuditEntry(Document document) {
        MongockAuditEntry changeEntry = toChangeEntry(document);
        LocalDateTime timestamp = Instant.ofEpochMilli(changeEntry.getTimestamp().getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (changeEntry.shouldBeIgnored()) {
            logger.info("Skipping Mongock audit entry with changeId[{}]: state=IGNORED (Mongock never executed this change; nothing to import).",
                    changeEntry.getChangeId());
            return null;
        }
        return new AuditEntry(
                changeEntry.getExecutionId(),
                null,
                changeEntry.getChangeId(),
                changeEntry.getAuthor(),
                timestamp,
                changeEntry.getState(),
                changeEntry.getType(),
                changeEntry.getChangeLogClass(),
                changeEntry.getChangeSetMethod(),
                null, //TODO: set sourceFile
                changeEntry.getExecutionMillis(),
                changeEntry.getExecutionHostname(),
                changeEntry.getMetadata(),
                changeEntry.getSystemChange(),
                changeEntry.getErrorTrace(),
                null,
                null,
                null,
                RecoveryStrategy.MANUAL_INTERVENTION,
                null
        );
    }


    private static MongockAuditEntry toChangeEntry(Document document) {
        Date timestamp = document.getDate("timestamp");
        return new MongockAuditEntry(
                document.getString("executionId"),
                document.getString("changeId"),
                document.getString("author"),
                timestamp,
                document.getString("state"),
                document.getString("type"),
                document.getString("changeLogClass"),
                document.getString("changeSetMethod"),
                document.get("metadata"),
                document.getLong("executionMillis"),
                document.getString("executionHostName"),
                document.getString("errorTrace"),
                document.getBoolean("systemChange"),
                timestamp
        );
    }
}
