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
import io.flamingock.internal.common.core.audit.AuditSnapshotBuilder;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.core.external.store.AuditCompactor;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;

/**
 * Collapses the MongoDB audit collection to one document per change.
 * <p>
 * MongoDB is the gentle case among the audit stores: the document key is an auto {@code _id} that
 * compaction never needs to change, so <b>nothing is ever written</b> — this only deletes. Contrast
 * DynamoDB and Couchbase, whose ledger and current-state forms use <em>different</em> keys, making
 * compaction there a rekey with a strict write-before-delete ordering to get right.
 *
 * <h2>Why the survivor is identified by {@code _id}</h2>
 * The surviving document is addressed by its <b>primary key</b>, and that is a deliberate choice worth
 * defending, because a shorter implementation is available and is worse.
 * <p>
 * {@code AuditReader.getAuditSnapshotByChangeId()} already returns the current entry per change, so
 * compaction could be written as "delete everything for this change except the document matching the
 * survivor's {@code (executionId, state)}" without reading a single document here. That identifies the
 * survivor by a <em>combination of attributes</em> rather than by its identity, and in a destructive
 * operation the difference matters:
 * <ul>
 *   <li>it relies on the unique {@code (executionId, changeId, state)} index existing to be
 *       single-valued at all — if two documents ever shared that triple, both would be spared;</li>
 *   <li>it needs two predicates to be right instead of one, and dropping either silently spares extra
 *       documents rather than failing;</li>
 *   <li>it has to reason about how a missing field behaves inside a negation.</li>
 * </ul>
 * Reading {@code _id} costs one pass over the collection — the same read the snapshot would have done
 * anyway — and removes all three concerns. {@code $ne} on {@code _id} is an exact primary-key
 * comparison.
 *
 * <h2>How the contract's guarantees are met</h2>
 * <ul>
 *   <li><b>Survivor preserved verbatim.</b> Trivially: it is never written, so it keeps every field and
 *       its {@code _id}.</li>
 *   <li><b>A change can never be left with zero documents.</b> By construction, not by ordering — the
 *       delete filter excludes the survivor's {@code _id}, so no window exists in which it is gone.</li>
 *   <li><b>Selection.</b> Delegated to {@link AuditSnapshotBuilder}, the same rule the execution planner
 *       aggregates with, which is what guarantees the snapshot is identical before and after.</li>
 *   <li><b>No index or schema change, no journal event.</b> This class holds a collection and a mapper,
 *       so it has no way to do either.</li>
 * </ul>
 *
 * <h2>Before changing this</h2>
 * No {@code ClientSession} and no transaction is involved. Each {@code deleteMany} is a single atomic
 * operation, so compaction also works where {@code MongoDBSyncTargetSystem.supportsTransactions()} is
 * {@code false} — unlike the audit write path. Read concern is not set here either: it comes from the
 * collection handle the repository built ({@code ReadConcern.MAJORITY} by default), so there is no
 * per-request consistency flag to forget.
 * <p>
 * The {@code changeId} clause in the delete filter is load-bearing in a way that is easy to miss:
 * {@code _id != survivorId} on its own would match almost every document in the collection.
 */
class MongoDBSyncAuditCompactor implements AuditCompactor {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoDBSyncAuditCompactor");

    /** MongoDB's document primary key. Not in {@code AuditEntryFieldConstants} — it is the driver's, not ours. */
    static final String KEY_ID = "_id";

    private final MongoCollection<Document> collection;
    private final MongoDBAuditMapper<MongoDBDocumentHelper> mapper;

    MongoDBSyncAuditCompactor(MongoCollection<Document> collection,
                              MongoDBAuditMapper<MongoDBDocumentHelper> mapper) {
        this.collection = Objects.requireNonNull(collection, "collection must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public Result compact() {
        try {
            // One pass, reading the whole collection before anything is deleted. Keeping each document's
            // _id alongside the entry it maps to is the entire reason this reads documents rather than
            // reusing AuditReader.getAuditSnapshotByChangeId() — see the class javadoc.
            //
            // A document whose state attribute is absent fails here, during mapping and aggregation,
            // rather than part-way through deleting. That is pre-existing: every read of the audit store
            // has the same property, since AuditEntry.shouldBeReplacedBy dereferences the state.
            Map<AuditEntry, Object> idOf = new IdentityHashMap<>();
            Map<String, List<AuditEntry>> byChange = new LinkedHashMap<>();
            for (Document document : collection.find().into(new ArrayList<Document>())) {
                AuditEntry entry = mapper.fromDocument(new MongoDBDocumentHelper(document));
                idOf.put(entry, document.get(KEY_ID));
                List<AuditEntry> entries = byChange.get(entry.getChangeId());
                if (entries == null) {
                    entries = new ArrayList<>();
                    byChange.put(entry.getChangeId(), entries);
                }
                entries.add(entry);
            }

            long removed = 0;
            for (Map.Entry<String, List<AuditEntry>> change : byChange.entrySet()) {
                // Fail-fast: a failure on one change aborts the rest. Changes are independent and nothing
                // is written, so whatever was already compacted stays compacted and a re-run finishes.
                removed += compactChange(change.getKey(), change.getValue(), idOf);
            }

            logger.debug("Audit compaction finished [changes={} documentsRemoved={}]", byChange.size(), removed);
            return Result.OK();
        } catch (RuntimeException exception) {
            // Reported rather than thrown, so the calling system change decides. Nothing can have been
            // half-done damagingly: every change's survivor is excluded from every delete.
            logger.warn("Audit compaction failed", exception);
            return new Result.Error(exception);
        }
    }

    /**
     * Removes every document of {@code changeId} except the one holding its current state.
     *
     * @return how many documents were removed
     */
    private long compactChange(String changeId, List<AuditEntry> entries, Map<AuditEntry, Object> idOf) {
        if (entries.size() == 1) {
            return 0;
        }

        Object survivorId = idOf.get(selectSurvivor(changeId, entries));
        if (survivorId == null) {
            throw new IllegalStateException("Could not resolve the stored document of the current state of"
                    + " changeId '" + changeId + "'");
        }

        // Both clauses matter. Without the changeId scope this would delete the rest of the collection.
        Bson supersededForThisChange = Filters.and(
                Filters.eq(KEY_CHANGE_ID, changeId),
                Filters.ne(KEY_ID, survivorId));

        long removed = collection.deleteMany(supersededForThisChange).getDeletedCount();
        logger.debug("Compacted change [changeId={} documentsRemoved={}]", changeId, removed);
        return removed;
    }

    /**
     * Picks the entry holding the change's current effective state, via {@link AuditSnapshotBuilder} rather
     * than a reimplementation of "latest wins" — which would resolve the equal-{@code createdAt} case
     * arbitrarily instead of by status priority, and so could silently change which changes are considered
     * applied.
     * <p>
     * The builder deals in {@link AuditEntry}, but what is needed is the stored document's {@code _id}, so
     * entries are tracked back to their documents by identity. Sound because the builder returns the very
     * instances it was given and {@code AuditEntry} declares no {@code equals}.
     */
    private AuditEntry selectSurvivor(String changeId, List<AuditEntry> entries) {
        AuditSnapshotBuilder builder = new AuditSnapshotBuilder();
        for (AuditEntry entry : entries) {
            builder.addEntry(entry);
        }
        List<AuditEntry> survivors = builder.buildList();
        if (survivors.size() != 1) {
            throw new IllegalStateException("Expected a single effective state for changeId '" + changeId
                    + "' but the snapshot produced " + survivors.size());
        }
        return survivors.get(0);
    }
}
