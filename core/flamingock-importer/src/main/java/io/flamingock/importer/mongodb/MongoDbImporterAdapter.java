/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
 
package io.flamingock.importer.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.importer.ImporterAdapter;
import io.flamingock.importer.mongock.MongockChangeEntry;
import io.flamingock.importer.mongock.MongockChangeState;
import io.flamingock.importer.mongock.MongockChangeType;
import io.flamingock.internal.common.core.audit.AuditEntry;
import org.bson.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

//TODO implement reading from Flamingock
public class MongoDbImporterAdapter implements ImporterAdapter {

    private final MongoCollection<Document> sourceCollection;

    public MongoDbImporterAdapter(MongoDatabase mongoDatabase, String collectionName) {
        this.sourceCollection = mongoDatabase.getCollection(collectionName);
    }

    @Override
    public List<AuditEntry> getAuditEntries() {
        return sourceCollection.find()
                .into(new ArrayList<>())
                .stream()
                .map(MongoDbImporterAdapter::toAuditEntry)
                .collect(Collectors.toList());
    }


    private static AuditEntry toAuditEntry(Document document) {
        MongockChangeEntry changeEntry = toChangeEntry(document);
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

    private static MongockChangeEntry toChangeEntry(Document document) {
        Date timestamp = document.getDate("timestamp");
        return new MongockChangeEntry(
                document.getString("executionId"),
                document.getString("changeId"),
                document.getString("author"),
                timestamp,
                MongockChangeState.valueOf(document.getString("state")),
                MongockChangeType.valueOf(document.getString("type")),
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
