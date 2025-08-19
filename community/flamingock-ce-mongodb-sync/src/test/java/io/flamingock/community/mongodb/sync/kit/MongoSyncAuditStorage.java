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
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.targetystem.mongodb.sync.util.MongoSyncDocumentHelper;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static io.flamingock.internal.common.core.audit.AuditEntryField.KEY_CHANGE_ID;
import static io.flamingock.internal.common.core.audit.AuditEntryField.KEY_STATE;

/**
 * MongoDB implementation of AuditStorage for real database testing.
 * Stores audit entries in a MongoDB collection and provides 
 * operations for testing audit behavior with actual MongoDB storage.
 */
public class MongoSyncAuditStorage implements AuditStorage {
    
    private static final String AUDIT_COLLECTION_NAME = "flamingockAuditLogs";
    
    private final MongoDatabase database;
    private final MongoCollection<Document> auditCollection;
    private final MongoDBAuditMapper<MongoSyncDocumentHelper> mapper;
    
    public MongoSyncAuditStorage(MongoDatabase database) {
        this.database = database;
        this.auditCollection = database.getCollection(AUDIT_COLLECTION_NAME);
        this.mapper = new MongoDBAuditMapper<>(() -> new MongoSyncDocumentHelper(new Document()));
    }
    
    @Override
    public void addAuditEntry(AuditEntry auditEntry) {
        // Use the existing mapper to convert AuditEntry to MongoDB document
        MongoSyncDocumentHelper documentHelper = mapper.toDocument(auditEntry);
        auditCollection.insertOne(documentHelper.getDocument());
    }
    
    @Override
    public List<AuditEntry> getAuditEntries() {
        List<AuditEntry> entries = new ArrayList<>();
        for (Document doc : auditCollection.find()) {
            entries.add(mapper.fromDocument(new MongoSyncDocumentHelper(doc)));
        }
        return entries;
    }
    
    @Override
    public List<AuditEntry> getAuditEntriesForChange(String changeId) {
        return auditCollection.find(new Document(KEY_CHANGE_ID, changeId))
                .map(doc -> mapper.fromDocument(new MongoSyncDocumentHelper(doc)))
                .into(new ArrayList<>());
    }
    
    @Override
    public long countAuditEntriesWithStatus(AuditEntry.Status status) {
        // Use the correct field name from AuditEntryField constants
        return auditCollection.countDocuments(new Document(KEY_STATE, status.toString()));
    }
    
    @Override
    public boolean hasAuditEntries() {
        return auditCollection.countDocuments() > 0;
    }
    
    @Override
    public void clear() {
        auditCollection.deleteMany(new Document());
    }
}