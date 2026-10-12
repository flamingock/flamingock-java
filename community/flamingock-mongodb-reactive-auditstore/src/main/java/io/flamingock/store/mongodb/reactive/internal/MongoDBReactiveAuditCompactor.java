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
package io.flamingock.store.mongodb.reactive.internal;

import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditSnapshotBuilder;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.core.external.store.AuditCompactor;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import io.flamingock.reactive.util.PublisherSync;
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
 * Collapses the MongoDB Reactive Streams audit collection to one document per change.
 * <p>
 * Identical in shape and guarantees to {@code MongoDBSyncAuditCompactor} — same driver family, same
 * document model, same auto {@code _id} that compaction never needs to change. The only difference is
 * plumbing: the reactive driver returns {@code Publisher}s, and {@code compact()} is synchronous by
 * contract, so every call is bridged with {@link PublisherSync}, the same idiom
 * {@code MongoDBReactiveAuditRepository} already uses for its own writes and reads.
 * <p>
 * Nothing is ever written. See {@code MongoDBSyncAuditCompactor}'s javadoc for the full reasoning on why
 * the survivor is identified by {@code _id} rather than by attributes.
 */
class MongoDBReactiveAuditCompactor implements AuditCompactor {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoDBReactiveAuditCompactor");

    /** MongoDB's document primary key. Not in {@code AuditEntryFieldConstants} — it is the driver's, not ours. */
    static final String KEY_ID = "_id";

    private final MongoCollection<Document> collection;
    private final MongoDBAuditMapper<MongoDBDocumentHelper> mapper;

    MongoDBReactiveAuditCompactor(MongoCollection<Document> collection,
                                  MongoDBAuditMapper<MongoDBDocumentHelper> mapper) {
        this.collection = Objects.requireNonNull(collection, "collection must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public Result compact() {
        try {
            // One pass, reading the whole collection before anything is deleted. Keeping each document's
            // _id alongside the entry it maps to is the entire reason this reads documents rather than
            // reusing AuditReader.getAuditSnapshotByChangeId() - see MongoDBSyncAuditCompactor's javadoc.
            Map<AuditEntry, Object> idOf = new IdentityHashMap<>();
            Map<String, List<AuditEntry>> byChange = new LinkedHashMap<>();
            for (Document document : PublisherSync.collect(collection.find())) {
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

        DeleteResult result = PublisherSync.first(collection.deleteMany(supersededForThisChange));
        long removed = result == null ? 0 : result.getDeletedCount();
        logger.debug("Compacted change [changeId={} documentsRemoved={}]", changeId, removed);
        return removed;
    }

    /**
     * Picks the entry holding the change's current effective state, via {@link AuditSnapshotBuilder} rather
     * than a reimplementation of "latest wins" - which would resolve the equal-{@code createdAt} case
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
