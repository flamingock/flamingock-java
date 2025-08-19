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
package io.flamingock.community.mongodb.sync.kit;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * MongoDB implementation of AuditStorage for real database testing.
 * Stores audit entries in a MongoDB collection and provides 
 * operations for testing audit behavior with actual MongoDB storage.
 */
public class MongoSyncAuditStorage implements AuditStorage {
    
    private static final String AUDIT_COLLECTION_NAME = "flamingockAuditEntries";
    
    private final MongoDatabase database;
    private final MongoCollection<Document> auditCollection;
    
    public MongoSyncAuditStorage(MongoDatabase database) {
        this.database = database;
        this.auditCollection = database.getCollection(AUDIT_COLLECTION_NAME);
    }
    
    @Override
    public void addAuditEntry(AuditEntry auditEntry) {
        Document doc = new Document()
                .append("executionId", auditEntry.getExecutionId())
                .append("stageId", auditEntry.getStageId())
                .append("taskId", auditEntry.getTaskId())
                .append("author", auditEntry.getAuthor())
                .append("createdAt", Date.from(auditEntry.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()))
                .append("state", auditEntry.getState().toString())
                .append("type", auditEntry.getType().toString())
                .append("className", auditEntry.getClassName())
                .append("methodName", auditEntry.getMethodName())
                .append("executionMillis", auditEntry.getExecutionMillis())
                .append("executionHostname", auditEntry.getExecutionHostname())
                .append("errorTrace", auditEntry.getErrorTrace())
                .append("systemChange", auditEntry.getSystemChange())
                .append("metadata", auditEntry.getMetadata())
                .append("auditTxType", auditEntry.getTxType().toString());
        
        auditCollection.insertOne(doc);
    }
    
    @Override
    public List<AuditEntry> getAuditEntries() {
        List<AuditEntry> entries = new ArrayList<>();
        for (Document doc : auditCollection.find()) {
            entries.add(documentToAuditEntry(doc));
        }
        return entries;
    }
    
    @Override
    public List<AuditEntry> getAuditEntriesForChange(String changeId) {
        return auditCollection.find(new Document("taskId", changeId))
                .map(this::documentToAuditEntry)
                .into(new ArrayList<>());
    }
    
    @Override
    public long countAuditEntriesWithStatus(AuditEntry.Status status) {
        return auditCollection.countDocuments(new Document("state", status.toString()));
    }
    
    @Override
    public boolean hasAuditEntries() {
        return auditCollection.countDocuments() > 0;
    }
    
    @Override
    public void clear() {
        auditCollection.deleteMany(new Document());
    }
    
    private AuditEntry documentToAuditEntry(Document doc) {
        Date createdAtDate = doc.getDate("createdAt");
        LocalDateTime createdAt = createdAtDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        
        return new AuditEntry(
                doc.getString("executionId"),
                doc.getString("stageId"),
                doc.getString("taskId"),
                doc.getString("author"),
                createdAt,
                AuditEntry.Status.valueOf(doc.getString("state")),
                AuditEntry.ExecutionType.valueOf(doc.getString("type")),
                doc.getString("className"),
                doc.getString("methodName"),
                doc.getLong("executionMillis"),
                doc.getString("executionHostname"),
                doc.getString("errorTrace"),
                doc.getBoolean("systemChange", false),
                doc.getString("metadata"),
                io.flamingock.internal.common.core.audit.AuditTxType.valueOf(doc.getString("auditTxType"))
        );
    }
}