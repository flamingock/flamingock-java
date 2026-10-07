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
package io.flamingock.store.mongodb.sync.internal;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.core.external.store.AuditCompactor;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_EXECUTION_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;

/**
 * Collapses the MongoDB audit collection to one document per change.
 * <p>
 * Short, and worth saying why rather than leaving the brevity looking like an oversight. MongoDB is the
 * easy case among the audit stores for two independent reasons:
 * <ul>
 *   <li>the document key is an auto {@code _id} that compaction never needs to change, so <b>nothing is
 *       written at all</b> — this only ever deletes;</li>
 *   <li>the unique index on {@code (executionId, changeId, state)} identifies a single document, so the
 *       surviving document is addressable from an {@link AuditEntry} alone.</li>
 * </ul>
 * Together those mean the whole operation is "ask which entry is current, then delete that change's other
 * documents". Contrast DynamoDB, whose audit table is partition-key-only and whose ledger and
 * current-state forms use <em>different</em> keys: compaction there is a rekey, and needs an entity-level
 * scan, winner-to-record identity mapping, strict write-before-delete ordering and a key-collision guard.
 * None of that applies here, and importing it would be cargo cult.
 *
 * <h2>How the contract's guarantees are met</h2>
 * <ul>
 *   <li><b>Survivor preserved verbatim.</b> Trivially — the surviving document is never touched, so it
 *       keeps every field and its original {@code _id}.</li>
 *   <li><b>A change can never be left with zero documents.</b> Not by careful ordering, but by
 *       construction: the delete filter explicitly excludes the survivor, so no window exists in which
 *       it is gone. This is a stronger position than the rekeying stores can reach.</li>
 *   <li><b>Selection.</b> Delegated entirely to {@link AuditReader#getAuditSnapshotByChangeId()}, which
 *       is the same aggregation the execution planner reads. Reusing it, rather than reimplementing
 *       "latest wins", is what guarantees the snapshot is identical before and after.</li>
 *   <li><b>No index or schema change, no journal event.</b> This class holds a collection and a reader and
 *       nothing else, so it has no way to do either.</li>
 * </ul>
 *
 * <h2>Two things to know before changing this</h2>
 * No {@code ClientSession} and no transaction is involved. Each {@code deleteMany} is a single atomic
 * operation, which also means compaction works on deployments where
 * {@code MongoDBSyncTargetSystem.supportsTransactions()} is {@code false} — unlike the audit write path.
 * Read concern is not set here either: it comes from the collection handle the repository built
 * ({@code ReadConcern.MAJORITY} by default), so there is no per-request consistency flag to forget.
 * <p>
 * The "exactly one document survives per change" guarantee leans on that unique index existing. Without
 * it, two documents could share an {@code (executionId, changeId, state)} triple and both would be
 * spared. {@code CollectionInitializator} creates it under {@code autoCreate} and fails loudly otherwise,
 * so it holds on every normal path — but the dependency is real and worth knowing.
 */
class MongoDBSyncAuditCompactor implements AuditCompactor {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoDBSyncAuditCompactor");

    private final MongoCollection<Document> collection;
    private final AuditReader auditReader;

    MongoDBSyncAuditCompactor(MongoCollection<Document> collection, AuditReader auditReader) {
        this.collection = Objects.requireNonNull(collection, "collection must not be null");
        this.auditReader = Objects.requireNonNull(auditReader, "auditReader must not be null");
    }

    @Override
    public Result compact() {
        try {
            // One read of the collection. A document whose state attribute is absent would already have
            // failed here, inside the snapshot aggregation, rather than part-way through deleting —
            // AuditEntry.shouldBeReplacedBy dereferences the state. That is pre-existing: every read of
            // the audit store has the same property.
            Map<String, AuditEntry> currentStateByChange = auditReader.getAuditSnapshotByChangeId();

            long removed = 0;
            for (Map.Entry<String, AuditEntry> change : currentStateByChange.entrySet()) {
                // Fail-fast: a failure on one change aborts the rest. Changes are independent and nothing
                // has been written, so whatever was already compacted stays compacted and a re-run
                // finishes the job.
                removed += deleteSupersededDocuments(change.getKey(), change.getValue());
            }

            logger.debug("Audit compaction finished [changes={} documentsRemoved={}]",
                    currentStateByChange.size(), removed);
            return Result.OK();
        } catch (RuntimeException exception) {
            // Reported rather than thrown, so the calling system change decides. Nothing can have been
            // half-done in a damaging way: the survivor of every change is excluded from every delete.
            logger.warn("Audit compaction failed", exception);
            return new Result.Error(exception);
        }
    }

    /**
     * Removes every document of {@code changeId} except the one holding its current state.
     * <p>
     * {@code isSurvivor} is deliberately the same filter shape as
     * {@code MongoDBSyncAuditRepository.append()} uses to address a single ledger document, so that
     * "this identifies exactly one document" and "delete everything that is not it" are visibly the same
     * predicate, negated.
     * <p>
     * {@code $nor} is used rather than {@code $or} of two {@code $ne}s: both are De Morgan equivalents with
     * identical treatment of missing fields, but negating the positive predicate reads as the intent.
     * A document carrying this {@code changeId} but no {@code executionId} fails {@code isSurvivor} and is
     * therefore removed, which is correct — it is not the survivor — though it does mean a malformed
     * sibling is deleted rather than kept.
     *
     * @return how many documents were removed
     */
    private long deleteSupersededDocuments(String changeId, AuditEntry currentState) {
        Bson isSurvivor = Filters.and(
                Filters.eq(KEY_EXECUTION_ID, currentState.getExecutionId()),
                Filters.eq(KEY_STATE, currentState.getState().name()));

        Bson supersededForThisChange = Filters.and(
                Filters.eq(KEY_CHANGE_ID, changeId),
                Filters.nor(isSurvivor));

        long removed = collection.deleteMany(supersededForThisChange).getDeletedCount();
        if (removed > 0) {
            logger.debug("Compacted change [changeId={} documentsRemoved={} survivingState={}]",
                    changeId, removed, currentState.getState());
        }
        return removed;
    }
}
