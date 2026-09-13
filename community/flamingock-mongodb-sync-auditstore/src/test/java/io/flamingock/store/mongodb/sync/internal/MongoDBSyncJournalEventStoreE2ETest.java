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
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MongoDBSyncJournalEventStoreE2ETest {

    private static final String DB_NAME = "test";
    private static final String JOURNAL_COLLECTION = "flamingockJournalEvents";

    @Container
    public static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:6")).withReuse(true);

    private final MongoDBJournalEventMapper mapper = new MongoDBJournalEventMapper();

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoDBSyncJournalEventStore journalEventStore;

    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase(DB_NAME);
        journalEventStore = new MongoDBSyncJournalEventStore(
                database, JOURNAL_COLLECTION,
                ReadConcern.MAJORITY, ReadPreference.primary(), WriteConcern.MAJORITY.withJournal(true));
        journalEventStore.initialize(true);
    }

    @AfterEach
    void tearDown() {
        database.drop();
        mongoClient.close();
    }

    @Test
    @DisplayName("initialize creates the unique, partial-unacknowledged and unique-eventId indexes")
    void createsExpectedIndexes() {
        Map<String, Document> byName = listIndexesByName();

        Document unique = byName.get(MongoDBSyncJournalEventStore.UNIQUE_INDEX_NAME);
        assertNotNull(unique, "unique index missing");
        assertTrue(unique.getBoolean("unique", false), "index should be unique");
        assertEquals(new Document("streamId", 1).append("streamSequence", 1), unique.get("key"));

        Document unacked = byName.get(MongoDBSyncJournalEventStore.UNACKNOWLEDGED_INDEX_NAME);
        assertNotNull(unacked, "unacknowledged partial index missing");
        assertFalse(unacked.getBoolean("unique", false), "partial index should not be unique");
        assertEquals(new Document("acknowledged", 1).append("streamId", 1).append("streamSequence", 1), unacked.get("key"));
        assertEquals(new Document("acknowledged", false), unacked.get("partialFilterExpression"));

        Document eventId = byName.get(MongoDBSyncJournalEventStore.EVENT_ID_INDEX_NAME);
        assertNotNull(eventId, "unique eventId index missing");
        assertTrue(eventId.getBoolean("unique", false), "eventId index should be unique");
        assertEquals(new Document("eventId", 1), eventId.get("key"));
    }

    @Test
    @DisplayName("initialize is idempotent and does not duplicate or recreate indexes")
    void initializeIsIdempotent() {
        Map<String, Document> before = listIndexesByName();

        journalEventStore.initialize(true);
        MongoDBSyncJournalEventStore secondInstance = new MongoDBSyncJournalEventStore(
                database, JOURNAL_COLLECTION,
                ReadConcern.MAJORITY, ReadPreference.primary(), WriteConcern.MAJORITY.withJournal(true));
        secondInstance.initialize(true);

        assertEquals(before.keySet(), listIndexesByName().keySet());
    }

    @Test
    @DisplayName("the unique index rejects a second event at the same (streamId, streamSequence)")
    void uniqueIndexEnforcesStreamPosition() {
        seed(Collections.singletonList(event("evt-A1", "stageA", 1L, false)));

        assertThrows(RuntimeException.class,
                () -> seed(Collections.singletonList(event("evt-other", "stageA", 1L, false))));
    }

    @Test
    @DisplayName("the unique eventId index rejects a second event reusing an existing eventId")
    void uniqueIndexEnforcesEventIdentity() {
        seed(Collections.singletonList(event("evt-A1", "stageA", 1L, false)));

        assertThrows(RuntimeException.class,
                () -> seed(Collections.singletonList(event("evt-A1", "stageB", 1L, false))));
    }

    @Test
    @DisplayName("write appends the event, and it is readable once the transaction commits")
    void writeAppendsEventOnCommit() {
        writeInCommittedTransaction(event("evt-A1", "stageA", 1L, false));

        Optional<JournalEvent<AuditEntry>> stored = journalEventStore.getLastEventByStream("stageA");
        assertTrue(stored.isPresent(), "the appended event should be readable after commit");
        assertEquals("evt-A1", stored.get().getEventId());
        assertEquals(1L, stored.get().getStreamSequence());
        assertEquals(JournalEventType.CHANGE_STATE, stored.get().getEventType());
        assertFalse(stored.get().isAcknowledged(), "a freshly appended event is pending synchronization");
        assertEquals("evt-A1", stored.get().getData().getChangeId(), "the audit payload should round-trip");
    }

    @Test
    @DisplayName("write joins the caller's transaction: aborting it discards the append")
    void writeIsDiscardedWhenTransactionAborts() {
        try (ClientSession session = mongoClient.startSession()) {
            session.startTransaction();
            journalEventStore.write(session, event("evt-A1", "stageA", 1L, false));
            session.abortTransaction();
        }

        assertFalse(journalEventStore.getLastEventByStream("stageA").isPresent(),
                "an aborted transaction must leave no event behind");
    }

    @Test
    @DisplayName("write appends rather than upserts: a second event at the same stream position is rejected")
    void writeRejectsDuplicateStreamPosition() {
        writeInCommittedTransaction(event("evt-A1", "stageA", 1L, false));

        assertThrows(RuntimeException.class,
                () -> writeInCommittedTransaction(event("evt-other", "stageA", 1L, false)));

        Optional<JournalEvent<AuditEntry>> stored = journalEventStore.getLastEventByStream("stageA");
        assertTrue(stored.isPresent());
        assertEquals("evt-A1", stored.get().getEventId(), "the original event must not be overwritten");
    }

    @Test
    @DisplayName("write keeps the stream ordered across successive appends")
    void writeKeepsStreamOrdered() {
        writeInCommittedTransaction(event("evt-A1", "stageA", 1L, false));
        writeInCommittedTransaction(event("evt-A2", "stageA", 2L, false));
        writeInCommittedTransaction(event("evt-B1", "stageB", 1L, false));

        assertEquals("evt-A2", journalEventStore.getLastEventByStream("stageA").get().getEventId());
        assertEquals(Arrays.asList("evt-A1", "evt-A2", "evt-B1"),
                ids(journalEventStore.getUnacknowledgedEvents(10)));
    }

    @Test
    @DisplayName("getLastEventByStream returns the highest streamSequence for the stream")
    void getLastEventByStreamReturnsHighestSequence() {
        seed(Arrays.asList(
                event("evt-A1", "stageA", 1L, true),
                event("evt-A2", "stageA", 2L, false),
                event("evt-A3", "stageA", 3L, false),
                event("evt-B1", "stageB", 1L, false)));

        Optional<JournalEvent<AuditEntry>> last = journalEventStore.getLastEventByStream("stageA");
        assertTrue(last.isPresent());
        assertEquals("evt-A3", last.get().getEventId());
        assertEquals(3L, last.get().getStreamSequence());

        assertFalse(journalEventStore.getLastEventByStream("unknown-stream").isPresent());
    }

    @Test
    @DisplayName("getUnacknowledgedEvents returns only unacked, ordered by (streamId, streamSequence), capped by limit")
    void getUnacknowledgedEventsOrderedAndLimited() {
        seed(Arrays.asList(
                event("evt-A1", "stageA", 1L, true),
                event("evt-A2", "stageA", 2L, false),
                event("evt-A3", "stageA", 3L, false),
                event("evt-B1", "stageB", 1L, false)));

        List<String> all = ids(journalEventStore.getUnacknowledgedEvents(10));
        assertEquals(Arrays.asList("evt-A2", "evt-A3", "evt-B1"), all);

        List<String> firstTwo = ids(journalEventStore.getUnacknowledgedEvents(2));
        assertEquals(Arrays.asList("evt-A2", "evt-A3"), firstTwo);
    }

    @Test
    @DisplayName("acknowledgeEvents flips the matching events and removes them from the unacknowledged batch")
    void acknowledgeEventsRemovesFromBatch() {
        seed(Arrays.asList(
                event("evt-A2", "stageA", 2L, false),
                event("evt-A3", "stageA", 3L, false),
                event("evt-B1", "stageB", 1L, false)));

        long modified = journalEventStore.acknowledgeEvents(Arrays.asList("evt-A2", "evt-B1"));
        assertEquals(2L, modified);

        assertEquals(Collections.singletonList("evt-A3"), ids(journalEventStore.getUnacknowledgedEvents(10)));

        assertEquals(0L, journalEventStore.acknowledgeEvents(Collections.emptyList()));
    }

    // ----------------------------- helpers -----------------------------

    private Map<String, Document> listIndexesByName() {
        return database.getCollection(JOURNAL_COLLECTION).listIndexes()
                .into(new ArrayList<>())
                .stream()
                .filter(index -> index.getString("name") != null)
                .collect(Collectors.toMap(index -> index.getString("name"), index -> index));
    }

    /**
     * Exercises {@code write} the way the persistence does: inside a transaction the caller owns.
     */
    private void writeInCommittedTransaction(JournalEvent<AuditEntry> event) {
        try (ClientSession session = mongoClient.startSession()) {
            session.startTransaction();
            journalEventStore.write(session, event);
            session.commitTransaction();
        }
    }

    private void seed(List<JournalEvent<AuditEntry>> events) {
        MongoCollection<Document> collection = database.getCollection(JOURNAL_COLLECTION);
        List<Document> documents = events.stream().map(mapper::toDocument).collect(Collectors.toList());
        collection.insertMany(documents);
    }

    private static List<String> ids(List<JournalEvent<AuditEntry>> events) {
        return events.stream().map(JournalEvent::getEventId).collect(Collectors.toList());
    }

    private static JournalEvent<AuditEntry> event(String eventId, String streamId, long sequence, boolean acknowledged) {
        return new JournalEvent<>(
                eventId,
                JournalEventType.CHANGE_STATE,
                JournalEvent.DEFAULT_VERSION,
                streamId,
                sequence,
                Instant.now(),
                auditEntry(eventId),
                acknowledged);
    }

    private static AuditEntry auditEntry(String changeId) {
        return AuditEntryTestFactory.createTestAuditEntry(
                changeId, AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null);
    }
}
