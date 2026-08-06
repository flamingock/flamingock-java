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

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.query.QueryScanConsistency;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionInitializator;
import io.flamingock.internal.common.couchbase.CouchbaseJournalEventMapper;
import io.flamingock.internal.core.journal.JournalEventStore;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_ACKNOWLEDGED;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_EVENT_ID;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_STREAM_ID;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_STREAM_SEQUENCE;

/**
 * Couchbase implementation of the local journal ({@code flamingockJournalEvents}).
 * <p>
 * Sibling of {@link CouchbaseAuditor}/{@link CouchbaseLockService}: it owns its own collection and index setup.
 * <p>
 * Couchbase has no unique secondary indexes, so the document key is the only place the stream-position
 * invariant can be enforced: each event is keyed {@code journal::<streamId>::<streamSequence>} and appended
 * with an {@code insert} (never an {@code upsert}), so a colliding position fails loudly with a
 * {@code DocumentExistsException} instead of being silently overwritten. {@code eventId} uniqueness is not
 * enforced here — the backend deduplicates on it independently — so its index only serves the acknowledgement
 * lookup.
 * <p>
 * Reads and acknowledgements are exposed through {@link JournalEventStore}. The append
 * ({@link #contributeToTransaction(TransactionAttemptContext, JournalEvent)}) deliberately is not: it takes
 * the {@link TransactionAttemptContext} the transaction wrapper injects, so the event lands in the same
 * transaction attempt as the audit entry it mirrors, and a driver-specific transaction handle has no place in
 * a core interface. Package-private, and only {@link CouchbaseAuditPersistence} — which owns that transaction
 * boundary — calls it.
 */
public class CouchbaseJournalEventStore implements JournalEventStore {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("CouchbaseJournal");

    private static final String KEY_PREFIX = "journal";

    static final String STREAM_SEQUENCE_INDEX_NAME = "idx_journal_stream_sequence";
    static final String UNACKNOWLEDGED_INDEX_NAME = "idx_journal_unacknowledged";
    static final String EVENT_ID_INDEX_NAME = "idx_journal_event_id";

    private final Cluster cluster;
    private final Bucket bucket;
    private final CouchbaseJournalEventMapper mapper = new CouchbaseJournalEventMapper();

    private Collection collection;
    private boolean initialized = false;

    public CouchbaseJournalEventStore(Cluster cluster, Bucket bucket) {
        this.cluster = cluster;
        this.bucket = bucket;
    }

    /**
     * Creates the collection and its indexes (or validates them, when {@code autoCreate} is {@code false}).
     * A no-op if already initialized, or gated out entirely by the caller under
     * {@code Features.JOURNAL_EVENTS} — see {@link CouchbaseAuditPersistence#doInitialize}.
     */
    synchronized void initialize(boolean autoCreate, String scopeName, String collectionName) {
        if (initialized) {
            return;
        }
        // Collection + primary index: the same concern CouchbaseAuditor/CouchbaseLockService delegate to this
        // helper, so ad-hoc tooling and test cleanup that scan "all documents" keep working here too.
        new CouchbaseCollectionInitializator(cluster, bucket, scopeName, collectionName).initialize(autoCreate);
        if (autoCreate) {
            createIndexes(scopeName, collectionName);
        } else {
            requireIndex(scopeName, collectionName, STREAM_SEQUENCE_INDEX_NAME);
            requireIndex(scopeName, collectionName, UNACKNOWLEDGED_INDEX_NAME);
            requireIndex(scopeName, collectionName, EVENT_ID_INDEX_NAME);
        }
        this.collection = bucket.scope(scopeName).collection(collectionName);
        initialized = true;
    }

    /**
     * Three indexes for the event buffer:
     * <ul>
     *   <li>{@code (streamId, streamSequence)} serving "last event per stream" (reverse scan);</li>
     *   <li>a partial {@code (acknowledged, streamId, streamSequence)} index over {@code acknowledged = false},
     *       serving the ordered unacknowledged batch scan while staying sized to the backlog;</li>
     *   <li>a non-unique {@code eventId} index serving the {@link #acknowledgeEvents(java.util.Collection)}
     *       lookup — {@code eventId} uniqueness cannot be enforced by Couchbase and is not this index's job;
     *       the stream-position document key is the real backstop.</li>
     * </ul>
     */
    private void createIndexes(String scopeName, String collectionName) {
        CouchbaseCollectionHelper.createIndexIfNotExists(cluster, bucket.name(), scopeName, collectionName,
                STREAM_SEQUENCE_INDEX_NAME, KEY_STREAM_ID + ", " + KEY_STREAM_SEQUENCE, null);
        CouchbaseCollectionHelper.createIndexIfNotExists(cluster, bucket.name(), scopeName, collectionName,
                UNACKNOWLEDGED_INDEX_NAME, KEY_ACKNOWLEDGED + ", " + KEY_STREAM_ID + ", " + KEY_STREAM_SEQUENCE,
                KEY_ACKNOWLEDGED + " = false");
        CouchbaseCollectionHelper.createIndexIfNotExists(cluster, bucket.name(), scopeName, collectionName,
                EVENT_ID_INDEX_NAME, KEY_EVENT_ID, null);
    }

    private void requireIndex(String scopeName, String collectionName, String indexName) {
        if (!CouchbaseCollectionHelper.indexExists(cluster, bucket.name(), scopeName, collectionName, indexName)) {
            throw new RuntimeException(String.format(
                    "Auto-creation is disabled and required journal index '%s' does not exist on `%s`.`%s`.`%s`",
                    indexName, bucket.name(), scopeName, collectionName));
        }
    }

    /**
     * Appends an event within the caller's transaction attempt, keyed {@code journal::<streamId>::<sequence>}.
     * <p>
     * Uses {@code insert}, never {@code upsert}: an event that is already there is a defect, not something to
     * overwrite. If the single-writer-per-stream assumption is ever violated, the second writer collides on
     * this key instead of silently duplicating or clobbering, and the resulting {@code DocumentExistsException}
     * aborts the transaction attempt, taking the audit entry with it.
     *
     * @param ctx   the transaction attempt this append must join
     * @param event the event to append
     * @return {@link Result#OK()} — failures surface as exceptions, not as a result
     */
    Result contributeToTransaction(TransactionAttemptContext ctx, JournalEvent<AuditEntry> event) {
        if (!initialized) {
            throw new IllegalStateException("Couchbase journal store is not initialized");
        }
        String key = toKey(event.getStreamId(), event.getStreamSequence());
        JsonObject document = mapper.toDocument(event);
        ctx.insert(collection, key, document);
        logger.debug("Journal event appended [eventId={} type={} stream={} sequence={}]",
                event.getEventId(), event.getEventType(), event.getStreamId(), event.getStreamSequence());
        return Result.OK();
    }

    @Override
    public Optional<JournalEvent<AuditEntry>> getLastEventByStream(String streamId) {
        if (!initialized) {
            return Optional.empty();
        }
        String query = String.format(
                "SELECT c.* FROM `%s`.`%s`.`%s` AS c WHERE c.%s = $streamId ORDER BY c.%s DESC LIMIT 1",
                collection.bucketName(), collection.scopeName(), collection.name(), KEY_STREAM_ID, KEY_STREAM_SEQUENCE);
        QueryResult result = cluster.query(query, QueryOptions.queryOptions()
                .scanConsistency(QueryScanConsistency.REQUEST_PLUS)
                .parameters(JsonObject.create().put("streamId", streamId)));
        List<JsonObject> rows = result.rowsAsObject();
        return rows.isEmpty() ? Optional.empty() : Optional.of(mapper.fromDocument(rows.get(0)));
    }

    @Override
    public List<JournalEvent<AuditEntry>> getUnacknowledgedEvents(int limit) {
        if (!initialized) {
            return new ArrayList<>();
        }
        String query = String.format(
                "SELECT c.* FROM `%s`.`%s`.`%s` AS c WHERE c.%s = false ORDER BY c.%s, c.%s LIMIT $limit",
                collection.bucketName(), collection.scopeName(), collection.name(),
                KEY_ACKNOWLEDGED, KEY_STREAM_ID, KEY_STREAM_SEQUENCE);
        QueryResult result = cluster.query(query, QueryOptions.queryOptions()
                .scanConsistency(QueryScanConsistency.REQUEST_PLUS)
                .parameters(JsonObject.create().put("limit", limit)));
        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        for (JsonObject row : result.rowsAsObject()) {
            events.add(mapper.fromDocument(row));
        }
        return events;
    }

    @Override
    public long acknowledgeEvents(java.util.Collection<String> eventIds) {
        if (!initialized || eventIds == null || eventIds.isEmpty()) {
            return 0L;
        }
        String query = String.format(
                "UPDATE `%s`.`%s`.`%s` SET %s = true WHERE %s IN $eventIds RETURNING META().id",
                collection.bucketName(), collection.scopeName(), collection.name(), KEY_ACKNOWLEDGED, KEY_EVENT_ID);
        QueryResult result = cluster.query(query, QueryOptions.queryOptions()
                .scanConsistency(QueryScanConsistency.REQUEST_PLUS)
                .parameters(JsonObject.create().put("eventIds", JsonArray.from(new ArrayList<>(eventIds)))));
        return result.rowsAsObject().size();
    }

    private static String toKey(String streamId, long streamSequence) {
        return KEY_PREFIX + "::" + streamId + "::" + streamSequence;
    }
}
