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
package io.flamingock.couchbase.kit;

import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.ReplicateTo;
import com.couchbase.client.java.kv.UpsertOptions;
import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.couchbase.CouchbaseAuditMapper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;

import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;
import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;

/**
 * Couchbase implementation of AuditStorage for real database testing.
 * Only depends on Couchbase client/database and core Flamingock classes.
 * Does not depend on Couchbase-specific Flamingock components like CouchbaseTargetSystem.
 */
public class CouchbaseAuditStorage implements AuditStorage {

    private final Cluster cluster;
    private final Collection auditCollection;

    private final CouchbaseAuditMapper mapper = new CouchbaseAuditMapper();

    public CouchbaseAuditStorage(Cluster cluster, String bucketName, String scopeName) {
        this(cluster, bucketName, scopeName, DEFAULT_AUDIT_STORE_NAME);
    }

    public CouchbaseAuditStorage(Cluster cluster, String bucketName, String scopeName, String auditCollectionName) {
        this.cluster = cluster;
        this.auditCollection = cluster.bucket(bucketName).scope(scopeName).collection(auditCollectionName);
    }

    @Override
    public void addAuditEntry(AuditEntry auditEntry) {
        String key = toKey(auditEntry);

        JsonObject document = mapper.toDocument(auditEntry);

        try {
            auditCollection.upsert(key, document,
                UpsertOptions.upsertOptions().durability(PersistTo.ACTIVE, ReplicateTo.NONE));
        } catch (CouchbaseException couchbaseException) {
            throw new RuntimeException(couchbaseException);
        }
    }

    @Override
    public List<AuditEntry> getAuditEntries() {
        return CouchbaseCollectionHelper.selectAllDocuments(
            cluster, auditCollection.bucketName(), auditCollection.scopeName(), auditCollection.name())
            .stream()
            .map(mapper::fromDocument)
            .collect(Collectors.toList());
    }

    @Override
    public List<AuditEntry> getAuditEntriesForChange(String changeId) {
        return CouchbaseCollectionHelper.selectAllDocuments(
            cluster, auditCollection.bucketName(), auditCollection.scopeName(), auditCollection.name())
            .stream()
            .filter(entry -> entry.getString(KEY_CHANGE_ID).equals(changeId))
            .map(mapper::fromDocument)
            .collect(Collectors.toList());
    }

    @Override
    public long countAuditEntriesWithStatus(AuditEntry.Status status) {
        return CouchbaseCollectionHelper.selectAllDocuments(
            cluster, auditCollection.bucketName(), auditCollection.scopeName(), auditCollection.name())
            .stream()
            .filter(entry -> entry.getString(KEY_STATE).equals(status.name()))
            .count();
    }

    @Override
    public boolean hasAuditEntries() {
        return !this.getAuditEntries().isEmpty();
    }

    @Override
    public void clear() {
        CouchbaseCollectionHelper.deleteAllDocuments(cluster, auditCollection.bucketName(), auditCollection.scopeName(), auditCollection.name());
    }

    private String toKey(AuditEntry auditEntry) {
        return auditEntry.getExecutionId() +
            '#' +
            auditEntry.getChangeId() +
            '#' +
            auditEntry.getState().name();
    }
}
