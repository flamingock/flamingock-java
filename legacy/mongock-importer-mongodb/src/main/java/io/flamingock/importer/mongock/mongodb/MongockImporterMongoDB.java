/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.importer.mongock.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import org.bson.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class MongockImporterMongoDB implements AuditHistoryReader {

    private final MongoCollection<Document> sourceCollection;

    public MongockImporterMongoDB(MongoDatabase mongoDatabase, String collectionName) {
        this.sourceCollection = mongoDatabase.getCollection(collectionName);
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return sourceCollection.find()
                .into(new ArrayList<>())
                .stream()
                .map(MongockImporterMongoDB::toAuditEntry)
                .collect(Collectors.toList());
    }


    private static AuditEntry toAuditEntry(Document document) {
        MongockAuditEntry changeEntry = toChangeEntry(document);
        LocalDateTime timestamp = Instant.ofEpochMilli(changeEntry.getTimestamp().getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (changeEntry.shouldBeIgnored()) {
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
                changeEntry.getExecutionMillis(),
                changeEntry.getExecutionHostname(),
                changeEntry.getMetadata(),
                changeEntry.getSystemChange(),
                changeEntry.getErrorTrace()
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
