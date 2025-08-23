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
package io.flamingock.mongodb.kit;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static io.flamingock.internal.common.core.audit.AuditEntryField.KEY_CHANGE_ID;
import static io.flamingock.internal.common.core.audit.AuditEntryField.KEY_STATE;

/**
 * MongoDB implementation of AuditStorage for real database testing.
 * Only depends on MongoDB client/database and core Flamingock classes.
 * Does not depend on MongoDB-specific Flamingock components like MongoSyncTargetSystem.
 */
public class MongoSyncAuditStorage implements AuditStorage {
    
    private static final String AUDIT_COLLECTION_NAME = "flamingockAuditLogs";
    
    private final MongoDatabase database;
    private final MongoCollection<Document> auditCollection;
    private final MongoDBAuditMapper<SimpleMongoDocumentHelper> mapper;
    
    public MongoSyncAuditStorage(MongoDatabase database) {
        this(database, AUDIT_COLLECTION_NAME);
    }
    
    public MongoSyncAuditStorage(MongoDatabase database, String collectionName) {
        this.database = database;
        this.auditCollection = database.getCollection(collectionName);
        this.mapper = new MongoDBAuditMapper<>(() -> new SimpleMongoDocumentHelper(new Document()));
    }
    
    @Override
    public void addAuditEntry(AuditEntry auditEntry) {
        // Use the existing mapper to convert AuditEntry to MongoDB document
        SimpleMongoDocumentHelper documentHelper = mapper.toDocument(auditEntry);
        auditCollection.insertOne(documentHelper.getDocument());
    }
    
    @Override
    public List<AuditEntry> getAuditEntries() {
        List<AuditEntry> entries = new ArrayList<>();
        for (Document doc : auditCollection.find()) {
            entries.add(mapper.fromDocument(new SimpleMongoDocumentHelper(doc)));
        }
        return entries;
    }
    
    @Override
    public List<AuditEntry> getAuditEntriesForChange(String changeId) {
        return auditCollection.find(new Document(KEY_CHANGE_ID, changeId))
                .map(doc -> mapper.fromDocument(new SimpleMongoDocumentHelper(doc)))
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