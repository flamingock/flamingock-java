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

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.IndexDefinition;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper;
import io.flamingock.internal.common.mongodb.MongoDBReactiveCollectionHelper;
import io.flamingock.internal.core.journal.JournalEventStore;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import io.flamingock.reactive.util.PublisherSync;
import org.bson.Document;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.flamingock.internal.common.mongodb.journal.JournalEventFieldConstants.KEY_ACKNOWLEDGED;
import static io.flamingock.internal.common.mongodb.journal.JournalEventFieldConstants.KEY_EVENT_ID;
import static io.flamingock.internal.common.mongodb.journal.JournalEventFieldConstants.KEY_STREAM_ID;
import static io.flamingock.internal.common.mongodb.journal.JournalEventFieldConstants.KEY_STREAM_SEQUENCE;

/**
 * Native MongoDB Reactive Streams implementation of the local journal event buffer.
 *
 * <p>The collection and indexes intentionally mirror the synchronous MongoDB implementation. Appending is
 * kept outside {@link JournalEventStore} because it must receive the native driver {@link ClientSession} that
 * also owns the current-state audit write.
 */
public class MongoDBReactiveJournalEventStore implements JournalEventStore {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoDBReactiveJournal");

    static final String UNIQUE_INDEX_NAME = "unique_key_sequence";
    static final String UNACKNOWLEDGED_INDEX_NAME = "unacknowledged_by_key_sequence";
    static final String EVENT_ID_INDEX_NAME = "unique_event_id";

    private final MongoCollection<Document> collection;
    private final MongoDBJournalEventMapper mapper = new MongoDBJournalEventMapper();
    private final CollectionInitializator<MongoDBDocumentHelper> initializer;
    private boolean initialized;

    public MongoDBReactiveJournalEventStore(MongoDatabase database,
                                            String collectionName,
                                            ReadConcern readConcern,
                                            ReadPreference readPreference,
                                            WriteConcern writeConcern) {
        this.collection = database.getCollection(collectionName)
                .withReadConcern(readConcern)
                .withReadPreference(readPreference)
                .withWriteConcern(writeConcern);
        this.initializer = new CollectionInitializator<>(
                new MongoDBReactiveCollectionHelper(collection),
                () -> new MongoDBDocumentHelper(new Document()),
                indexDefinitions());
    }

    public synchronized void initialize(boolean autoCreate) {
        if (initialized) {
            return;
        }
        if (autoCreate) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }
        initialized = true;
    }

    private static List<IndexDefinition> indexDefinitions() {
        LinkedHashMap<String, Integer> streamUniqueKeys = new LinkedHashMap<>();
        streamUniqueKeys.put(KEY_STREAM_ID, 1);
        streamUniqueKeys.put(KEY_STREAM_SEQUENCE, 1);
        IndexDefinition streamUniqueIndex = new IndexDefinition(streamUniqueKeys, true, null, UNIQUE_INDEX_NAME);

        LinkedHashMap<String, Integer> partialKeys = new LinkedHashMap<>();
        partialKeys.put(KEY_ACKNOWLEDGED, 1);
        partialKeys.put(KEY_STREAM_ID, 1);
        partialKeys.put(KEY_STREAM_SEQUENCE, 1);
        Map<String, Object> partialFilter = new LinkedHashMap<>();
        partialFilter.put(KEY_ACKNOWLEDGED, false);
        IndexDefinition unacknowledgedIndex =
                new IndexDefinition(partialKeys, false, partialFilter, UNACKNOWLEDGED_INDEX_NAME);

        LinkedHashMap<String, Integer> eventIdKeys = new LinkedHashMap<>();
        eventIdKeys.put(KEY_EVENT_ID, 1);
        IndexDefinition eventIdIndex = new IndexDefinition(eventIdKeys, true, null, EVENT_ID_INDEX_NAME);

        return Arrays.asList(streamUniqueIndex, unacknowledgedIndex, eventIdIndex);
    }

    /**
     * Appends an immutable event in the supplied transaction session.
     *
     * <p>This is deliberately an insert, never an upsert. A duplicate stream position or event id is a
     * transaction failure, so the corresponding audit-state write is rolled back as well.
     *
     * @param clientSession session owning the transaction
     * @param event         event to append
     * @return successful write result; driver failures are propagated
     */
    Result append(ClientSession clientSession, JournalEvent<AuditEntry> event) {
        if (!initialized) {
            throw new IllegalStateException("MongoDB reactive journal is not initialized");
        }
        PublisherSync.first(collection.insertOne(clientSession, mapper.toDocument(event)));
        logger.debug("Journal event appended [eventId={} type={} stream={} sequence={}]",
                event.getEventId(), event.getEventType(), event.getStreamId(), event.getStreamSequence());
        return Result.OK();
    }

    @Override
    public Optional<JournalEvent<AuditEntry>> getLastEventByStream(String streamId) {
        Document document = PublisherSync.first(collection.find(Filters.eq(KEY_STREAM_ID, streamId))
                .sort(Sorts.descending(KEY_STREAM_SEQUENCE))
                .limit(1)
                .first());
        return document == null ? Optional.empty() : Optional.of(mapper.fromDocument(document));
    }

    @Override
    public List<JournalEvent<AuditEntry>> getUnacknowledgedEvents(int limit) {
        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        PublisherSync.collect(collection.find(Filters.eq(KEY_ACKNOWLEDGED, false))
                        .sort(Sorts.ascending(KEY_STREAM_ID, KEY_STREAM_SEQUENCE))
                        .limit(limit))
                .forEach(document -> events.add(mapper.fromDocument(document)));
        return events;
    }

    @Override
    public long acknowledgeEvents(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return 0L;
        }
        return PublisherSync.first(collection.updateMany(
                Filters.in(KEY_EVENT_ID, eventIds), Updates.set(KEY_ACKNOWLEDGED, true))).getModifiedCount();
    }
}
