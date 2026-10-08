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
package io.flamingock.store.couchbase.internal;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import com.couchbase.client.java.transactions.TransactionGetResult;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditSnapshotBuilder;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.common.couchbase.CouchbaseAuditMapper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.AuditCompactor;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collapses the Couchbase audit collection to one document per change.
 * <p>
 * Couchbase rekeys, like DynamoDB: the historical ledger keys a document by
 * {@code executionId#changeId#state} (see {@link CouchbaseAuditor#append}); the current-state form keys it
 * by the bare {@code changeId} (what {@link CouchbaseAuditor#contributeToTransaction} writes). So compaction
 * here is a <b>rekey</b>, not merely a deletion of superseded documents — and because
 * {@link CouchbaseCollectionHelper#selectAllDocuments} projects document fields only, never
 * {@code META().id}, the key a document lives at cannot be recovered from its fields: a document already
 * at the {@code changeId} key has exactly the same fields as one at the ledger key. This class reads
 * through {@link CouchbaseCollectionHelper#selectAllDocumentsWithId} instead, which carries the key
 * alongside the fields.
 * <p>
 * Unlike DynamoDB, nothing here is laundered by rebuilding a document from an {@code AuditEntry}: a
 * Couchbase document body never embeds its own key, so the survivor's original {@link JsonObject} (query
 * artefacts stripped) is written back under the new key completely unchanged.
 *
 * <h2>Why this can be atomic per change, unlike DynamoDB's</h2>
 * {@link ExecutionWrapper} here is backed by {@code CouchbaseTxWrapper}, which runs the whole attempt inside
 * a real Couchbase ACID transaction ({@code cluster.transactions().run(...)}) — the same machinery
 * {@code CouchbaseAuditPersistence#writeEntry} already uses. So, unlike DynamoDB's 25-item batch-write cap,
 * the survivor write and the superseded deletes for one change either all commit or none do. The
 * write-survivor-before-delete ordering below is kept anyway for clarity and parity with the documented
 * contract, but it is not the safety mechanism here the way it is for DynamoDB.
 * <p>
 * Changes are compacted independently and the first failure aborts the rest, so a partial run always leaves
 * a state the next run can finish.
 */
class CouchbaseAuditCompactor implements AuditCompactor {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("CouchbaseAuditCompactor");
    private static final String ID_FIELD = "id";

    private final Cluster cluster;
    private final Collection collection;
    private final ExecutionWrapper txWrapper;
    private final CouchbaseAuditMapper mapper = new CouchbaseAuditMapper();

    CouchbaseAuditCompactor(Cluster cluster, Collection collection, ExecutionWrapper txWrapper) {
        this.cluster = Objects.requireNonNull(cluster, "cluster must not be null");
        this.collection = Objects.requireNonNull(collection, "collection must not be null");
        this.txWrapper = Objects.requireNonNull(txWrapper, "txWrapper must not be null");
    }

    @Override
    public Result compact() {
        try {
            Map<String, List<StoredDocument>> recordsByChange = scanGroupedByChangeId();
            // Every change id is also a current-state key - used so that no change can delete a key that
            // another change legitimately owns (see compactChange). A live view, not a copy: nothing below
            // mutates recordsByChange.
            Set<String> currentStateKeys = recordsByChange.keySet();
            Map<String, StoredDocument> survivors = selectSurvivors(recordsByChange);
            int compacted = 0;
            for (Map.Entry<String, List<StoredDocument>> group : recordsByChange.entrySet()) {
                String changeId = group.getKey();
                if (compactChange(changeId, group.getValue(), survivors.get(changeId), currentStateKeys)) {
                    compacted++;
                }
            }
            logger.debug("Audit compaction finished [changes={} compacted={}]", recordsByChange.size(), compacted);
            return Result.OK();
        } catch (RuntimeException exception) {
            // Reported rather than thrown, so the calling system change decides. Whatever change failed,
            // every change compacted before it stays compacted (atomic per change via the real Couchbase
            // transaction), and every change after it is simply untouched - both safe to retry.
            logger.warn("Audit compaction failed", exception);
            return new Result.Error(exception);
        }
    }

    /**
     * Reads the whole collection once, with the physical key alongside each document's fields, and groups
     * by change id.
     * <p>
     * Must be strongly consistent ({@code REQUEST_PLUS}): a stale read could miss a change's most recent
     * document, pick an earlier one as the current state, and delete the document it missed - and until
     * the next run the audit store would report a state the change had already moved past.
     */
    private Map<String, List<StoredDocument>> scanGroupedByChangeId() {
        List<JsonObject> rows = CouchbaseCollectionHelper.selectAllDocumentsWithId(
                cluster, collection.bucketName(), collection.scopeName(), collection.name());

        Map<String, List<StoredDocument>> recordsByChange = new LinkedHashMap<>();
        for (JsonObject row : rows) {
            String id = row.getString(ID_FIELD);
            row.removeKey(ID_FIELD);
            AuditEntry entry = mapper.fromDocument(row);
            String changeId = entry.getChangeId();
            if (changeId == null || changeId.trim().isEmpty()) {
                // Such a record is already invisible to the operational path, which looks changes up by id,
                // so it cannot be compacted into anything meaningful. Failing is deliberate: deleting it
                // would be data loss, skipping it would hide corruption, and compaction is exactly when an
                // operator should find out it exists.
                throw new IllegalStateException("Cannot compact the audit store: the document with id '"
                        + id + "' has no changeId");
            }
            recordsByChange.computeIfAbsent(changeId, key -> new ArrayList<>()).add(new StoredDocument(id, row, entry));
        }
        return recordsByChange;
    }

    /**
     * Picks, for every change in one pass, the document holding its current effective state.
     * <p>
     * Delegates the decision to {@link AuditSnapshotBuilder} rather than reimplementing it, which is what
     * guarantees the audit snapshot is identical before and after compaction. The builder works in terms of
     * {@link AuditEntry}, but what has to be written back is the stored <em>document</em>, so converted
     * entries are tracked back to their {@link StoredDocument} by identity - sound because
     * {@code AuditSnapshotBuilder} returns the very instances it was given, and {@code AuditEntry} declares
     * no {@code equals()}.
     */
    private Map<String, StoredDocument> selectSurvivors(Map<String, List<StoredDocument>> recordsByChange) {
        AuditSnapshotBuilder builder = new AuditSnapshotBuilder();
        Map<AuditEntry, StoredDocument> originalOf = new IdentityHashMap<>();
        for (List<StoredDocument> records : recordsByChange.values()) {
            for (StoredDocument record : records) {
                builder.addEntry(record.entry);
                originalOf.put(record.entry, record);
            }
        }
        Map<String, StoredDocument> survivors = new LinkedHashMap<>();
        for (Map.Entry<String, AuditEntry> survivor : builder.buildMap().entrySet()) {
            survivors.put(survivor.getKey(), originalOf.get(survivor.getValue()));
        }
        return survivors;
    }

    /**
     * @return {@code true} when the change needed work, {@code false} when it was already in current-state
     * form
     */
    private boolean compactChange(String changeId, List<StoredDocument> records, StoredDocument survivor,
                                  Set<String> currentStateKeys) {
        if (records.size() == 1 && changeId.equals(records.get(0).id)) {
            return false;
        }

        List<String> supersededIds = new ArrayList<>(records.size());
        for (StoredDocument record : records) {
            if (changeId.equals(record.id)) {
                continue;
            }
            // A change id may contain '#', so a ledger key of this change can in principle be
            // character-for-character another change's id - and therefore that change's current-state key.
            // Deleting it would destroy the only record of a different change. Leaving it is harmless: the
            // change that owns it writes it as its own survivor.
            if (currentStateKeys.contains(record.id)) {
                logger.warn("Not deleting id '{}' while compacting changeId '{}': it is also the "
                        + "current-state id of another change", record.id, changeId);
                continue;
            }
            supersededIds.add(record.id);
        }

        BasicRuntimeContext runtimeContext = new BasicRuntimeContext("compact-" + changeId);
        txWrapper.wrapExecution(runtimeContext, ctx -> {
            TransactionAttemptContext txContext = ctx.getContext().getRequiredDependencyValue(TransactionAttemptContext.class);
            if (!changeId.equals(survivor.id)) {
                try {
                    TransactionGetResult existing = txContext.get(collection, changeId);
                    txContext.replace(existing, survivor.document);
                } catch (DocumentNotFoundException e) {
                    txContext.insert(collection, changeId, survivor.document);
                }
            }
            for (String supersededId : supersededIds) {
                txContext.remove(txContext.get(collection, supersededId));
            }
            return Result.OK();
        });

        logger.debug("Compacted change [changeId={} records={} survivingState={}]",
                changeId, records.size(), survivor.entry.getState());
        return true;
    }

    /** A document as read from the collection: its physical key, raw fields, and parsed entry. */
    private static final class StoredDocument {
        final String id;
        final JsonObject document;
        final AuditEntry entry;

        StoredDocument(String id, JsonObject document, AuditEntry entry) {
            this.id = id;
            this.document = document;
            this.entry = entry;
        }
    }
}
