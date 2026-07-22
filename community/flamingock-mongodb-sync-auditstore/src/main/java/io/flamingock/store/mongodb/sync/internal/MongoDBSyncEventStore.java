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

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.IndexDefinition;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.common.mongodb.MongoDBEventMapper;
import io.flamingock.internal.common.mongodb.MongoDBSyncCollectionHelper;
import io.flamingock.internal.core.external.store.event.EventStore;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_ACKNOWLEDGED;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_EVENT_ID;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_STREAM_ID;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_STREAM_SEQUENCE;

/**
 * MongoDB-sync implementation of the local event buffer ({@code flamingockEvents}).
 * <p>
 * Sibling of {@link MongoDBSyncAuditor}/{@link MongoDBSyncLockService}: it owns its own collection and
 * index setup. Phase 1 is read/acknowledge only — event writing (atomic with the state write) is a later phase.
 */
public class MongoDBSyncEventStore implements EventStore {

    static final String UNIQUE_INDEX_NAME = "unique_key_sequence";
    static final String UNACKNOWLEDGED_INDEX_NAME = "unacknowledged_by_key_sequence";

    private final MongoCollection<Document> collection;
    private final MongoDBEventMapper mapper = new MongoDBEventMapper();

    MongoDBSyncEventStore(MongoDatabase database,
                          String collectionName,
                          ReadConcern readConcern,
                          ReadPreference readPreference,
                          WriteConcern writeConcern) {
        this.collection = database.getCollection(collectionName)
                .withReadConcern(readConcern)
                .withReadPreference(readPreference)
                .withWriteConcern(writeConcern);
    }

    protected void initialize(boolean autoCreate) {
        CollectionInitializator<MongoDBDocumentHelper> initializer = new CollectionInitializator<>(
                new MongoDBSyncCollectionHelper(collection),
                () -> new MongoDBDocumentHelper(new Document()),
                indexDefinitions());
        if (autoCreate) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }
    }

    /**
     * The two indexes for the event buffer:
     * <ul>
     *   <li>a unique {@code (streamId, streamSequence)} index enforcing the stream-position invariant and
     *       serving "last event per stream" (reverse scan);</li>
     *   <li>a partial {@code (acknowledged, streamId, streamSequence)} index over {@code acknowledged:false}
     *       serving the ordered unacknowledged batch scan. The leading {@code acknowledged} field gives it a
     *       distinct key pattern from the unique index (avoiding an index-key conflict) while keeping the
     *       index sized to the pending backlog.</li>
     * </ul>
     */
    private static List<IndexDefinition> indexDefinitions() {
        LinkedHashMap<String, Integer> uniqueKeys = new LinkedHashMap<>();
        uniqueKeys.put(KEY_STREAM_ID, 1);
        uniqueKeys.put(KEY_STREAM_SEQUENCE, 1);
        IndexDefinition uniqueIndex = new IndexDefinition(uniqueKeys, true, null, UNIQUE_INDEX_NAME);

        LinkedHashMap<String, Integer> partialKeys = new LinkedHashMap<>();
        partialKeys.put(KEY_ACKNOWLEDGED, 1);
        partialKeys.put(KEY_STREAM_ID, 1);
        partialKeys.put(KEY_STREAM_SEQUENCE, 1);
        Map<String, Object> partialFilter = new LinkedHashMap<>();
        partialFilter.put(KEY_ACKNOWLEDGED, false);
        IndexDefinition unacknowledgedIndex =
                new IndexDefinition(partialKeys, false, partialFilter, UNACKNOWLEDGED_INDEX_NAME);

        return Arrays.asList(uniqueIndex, unacknowledgedIndex);
    }

    @Override
    public Optional<JournalEvent<AuditEntry>> getLastEventByStream(String streamId) {
        Document document = collection.find(Filters.eq(KEY_STREAM_ID, streamId))
                .sort(Sorts.descending(KEY_STREAM_SEQUENCE))
                .limit(1)
                .first();
        return document == null ? Optional.empty() : Optional.of(mapper.fromDocument(document));
    }

    @Override
    public List<JournalEvent<AuditEntry>> getUnacknowledgedEvents(int limit) {
        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        collection.find(Filters.eq(KEY_ACKNOWLEDGED, false))
                .sort(Sorts.ascending(KEY_STREAM_ID, KEY_STREAM_SEQUENCE))
                .limit(limit)
                .forEach(document -> events.add(mapper.fromDocument(document)));
        return events;
    }

    @Override
    public long acknowledgeEvents(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return 0L;
        }
        return collection.updateMany(Filters.in(KEY_EVENT_ID, eventIds), Updates.set(KEY_ACKNOWLEDGED, true))
                .getModifiedCount();
    }
}
