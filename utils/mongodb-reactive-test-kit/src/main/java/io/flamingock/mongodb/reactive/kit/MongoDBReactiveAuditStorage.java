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
package io.flamingock.mongodb.reactive.kit;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.reactive.util.PublisherSync;
import org.bson.Document;

import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;

/**
 * MongoDB reactive implementation of AuditStorage for real database testing.
 * Only depends on MongoDB client/database and core Flamingock classes.
 * Does not depend on MongoDB-specific Flamingock components like MongoDBReactiveTargetSystem.
 */
public class MongoDBReactiveAuditStorage implements AuditStorage {

    private static final String AUDIT_COLLECTION_NAME = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;

    private final MongoCollection<Document> auditCollection;
    private final MongoDBAuditMapper<MongoDBDocumentHelper> mapper;

    public MongoDBReactiveAuditStorage(MongoDatabase database) {
        this(database, AUDIT_COLLECTION_NAME);
    }

    public MongoDBReactiveAuditStorage(MongoDatabase database, String collectionName) {
        this.auditCollection = database.getCollection(collectionName);
        this.mapper = new MongoDBAuditMapper<>(() -> new MongoDBDocumentHelper(new Document()));
    }

    @Override
    public void addAuditEntry(AuditEntry auditEntry) {
        MongoDBDocumentHelper documentHelper = mapper.toDocument(auditEntry);
        PublisherSync.complete(auditCollection.insertOne(documentHelper.getDocument()));
    }

    @Override
    public List<AuditEntry> getAuditEntries() {
        return PublisherSync.collect(auditCollection.find())
                .stream()
                .map(doc -> mapper.fromDocument(new MongoDBDocumentHelper(doc)))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEntry> getAuditEntriesForChange(String changeId) {
        return PublisherSync.collect(auditCollection.find(new Document(KEY_CHANGE_ID, changeId)))
                .stream()
                .map(doc -> mapper.fromDocument(new MongoDBDocumentHelper(doc)))
                .collect(Collectors.toList());
    }

    @Override
    public long countAuditEntriesWithStatus(AuditEntry.Status status) {
        Long count = PublisherSync.first(auditCollection.countDocuments(new Document(KEY_STATE, status.toString())));
        return count != null ? count : 0L;
    }

    @Override
    public boolean hasAuditEntries() {
        Long count = PublisherSync.first(auditCollection.countDocuments());
        return count != null && count > 0;
    }

    @Override
    public void clear() {
        PublisherSync.complete(auditCollection.deleteMany(new Document()));
    }
}
