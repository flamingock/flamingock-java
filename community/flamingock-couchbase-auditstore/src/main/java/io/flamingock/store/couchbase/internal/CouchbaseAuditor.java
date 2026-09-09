/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.couchbase.internal;

import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.ReplicateTo;
import com.couchbase.client.java.kv.UpsertOptions;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import com.couchbase.client.java.transactions.TransactionGetResult;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.couchbase.CouchbaseAuditMapper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionInitializator;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.Collectors;


/**
 * Internal to this module — not exposed as {@code AuditWriter}/{@code AuditReader}, since callers reach it
 * only through {@link CouchbaseAuditPersistence}, which owns the {@code Features.JOURNAL_EVENTS} branching.
 */
public class CouchbaseAuditor {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("CouchbaseAuditor");

    protected final Cluster cluster;
    protected final Bucket bucket;
    protected Collection collection;
    protected CouchbaseCollectionInitializator collectionInitializator;
    private boolean initialized = false;


    private final CouchbaseAuditMapper mapper = new CouchbaseAuditMapper();

    public CouchbaseAuditor(Cluster cluster, Bucket bucket) {
        this.cluster = cluster;
        this.bucket = bucket;
    }

    /**
     * A no-op past the first call: shared across every stage's persistence, so each new stage would otherwise
     * re-run the create-if-not-exists calls needlessly.
     */
    public synchronized void initialize(boolean autoCreate, String scopeName, String collectionName) {
        if (initialized) {
            return;
        }
        this.collectionInitializator = new CouchbaseCollectionInitializator(cluster, bucket, scopeName, collectionName);
        this.collectionInitializator.initialize(autoCreate);
        this.collection = this.bucket.scope(scopeName).collection(collectionName);
        initialized = true;
    }

    /**
     * Keeps one record per {@code (executionId, changeId, state)} — the append-oriented audit ledger, where a
     * change accumulates a document per state transition and the collection is itself the history.
     * <p>
     * This is the behaviour used when journal events are disabled, and it is what the Mongock importer needs
     * regardless: a legacy changelog can hold several entries for the same change across executions, and
     * {@link #contributeToTransaction} would collapse them onto each other, discarding the very history being
     * imported.
     */
    Result append(AuditEntry auditEntry) {

        String key = toKey(auditEntry);
        logger.debug("Saving audit entry with key {}", key);

        JsonObject document = mapper.toDocument(auditEntry);

        try {
            collection.upsert(key, document,
                    UpsertOptions.upsertOptions().durability(PersistTo.ACTIVE, ReplicateTo.NONE));
        } catch (CouchbaseException couchbaseException) {
            logger.warn("Error saving audit entry with key {}", key, couchbaseException);
            throw new RuntimeException(couchbaseException);
        }

        return Result.OK();
    }

    /**
     * Keeps a single document per change, overwritten on every state transition — the change's current
     * state — within the caller's transaction attempt.
     * <p>
     * The history of how it got there lives in the journal, so this is only correct when journal events are
     * being written; see {@code CouchbaseAuditPersistence.writeEntry}.
     * <p>
     * Keyed on {@code changeId} alone, which is safe because {@code LoadedPipeline.validate()} rejects
     * duplicate change ids across all stages. Nothing at the database level enforces one-document-per-change —
     * the single-writer guarantee comes from the stage lock. Couchbase transactions have no {@code upsert}, so
     * this reads first and replaces on a hit, inserting only on {@link DocumentNotFoundException} — the same
     * idiom {@code CouchbaseTargetSystemAuditMarker.mark()} already uses.
     */
    Result contributeToTransaction(TransactionAttemptContext ctx, AuditEntry auditEntry) {
        String key = auditEntry.getChangeId();
        JsonObject document = mapper.toDocument(auditEntry);
        try {
            TransactionGetResult existing = ctx.get(collection, key);
            ctx.replace(existing, document);
        } catch (DocumentNotFoundException e) {
            ctx.insert(collection, key, document);
        }
        logger.debug("Staged current-state audit entry with key {}", key);
        return Result.OK();
    }


    public List<AuditEntry> getAuditHistory() {
        return CouchbaseCollectionHelper.selectAllDocuments(cluster, collection.bucketName(), collection.scopeName(), collection.name())
                .stream()
                .map(mapper::fromDocument)
                .collect(Collectors.toList());

    }

    private String toKey(AuditEntry auditEntry) {
        return auditEntry.getExecutionId() +
                '#' +
                auditEntry.getChangeId() +
                '#' +
                auditEntry.getState().name();
    }
}
