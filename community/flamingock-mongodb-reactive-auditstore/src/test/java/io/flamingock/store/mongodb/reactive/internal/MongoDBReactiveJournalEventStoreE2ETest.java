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
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.reactive.util.PublisherSync;
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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
class MongoDBReactiveJournalEventStoreE2ETest {

    private static final String DB_NAME = "test";
    private static final String JOURNAL_COLLECTION = "flamingockJournalEvents";

    @Container
    static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:6")).withReuse(true);

    private final MongoDBJournalEventMapper mapper = new MongoDBJournalEventMapper();

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoDBReactiveJournalEventStore journalEventStore;

    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase(DB_NAME);
        journalEventStore = new MongoDBReactiveJournalEventStore(
                database, JOURNAL_COLLECTION,
                ReadConcern.MAJORITY, ReadPreference.primary(), WriteConcern.MAJORITY.withJournal(true));
        journalEventStore.initialize(true);
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        PublisherSync.complete(database.drop());
        mongoClient.close();
    }

    @Test
    @DisplayName("initialize creates the unique, partial-unacknowledged and unique-eventId indexes")
    void createsExpectedIndexes() {
        Map<String, Document> byName = PublisherSync.collect(database.getCollection(JOURNAL_COLLECTION).listIndexes())
                .stream()
                .filter(index -> index.getString("name") != null)
                .collect(Collectors.toMap(index -> index.getString("name"), index -> index));

        Document unique = byName.get(MongoDBReactiveJournalEventStore.UNIQUE_INDEX_NAME);
        assertNotNull(unique);
        assertTrue(unique.getBoolean("unique", false));
        assertEquals(new Document("streamId", 1).append("streamSequence", 1), unique.get("key"));

        Document unacknowledged = byName.get(MongoDBReactiveJournalEventStore.UNACKNOWLEDGED_INDEX_NAME);
        assertNotNull(unacknowledged);
        assertFalse(unacknowledged.getBoolean("unique", false));
        assertEquals(new Document("acknowledged", 1).append("streamId", 1)
                .append("streamSequence", 1), unacknowledged.get("key"));
        assertEquals(new Document("acknowledged", false), unacknowledged.get("partialFilterExpression"));

        Document eventId = byName.get(MongoDBReactiveJournalEventStore.EVENT_ID_INDEX_NAME);
        assertNotNull(eventId);
        assertTrue(eventId.getBoolean("unique", false));
        assertEquals(new Document("eventId", 1), eventId.get("key"));
    }

    @Test
    @DisplayName("initialize is idempotent")
    void initializeIsIdempotent() {
        Map<String, Document> before = listIndexesByName();

        journalEventStore.initialize(true);
        MongoDBReactiveJournalEventStore secondInstance = new MongoDBReactiveJournalEventStore(
                database, JOURNAL_COLLECTION,
                ReadConcern.MAJORITY, ReadPreference.primary(), WriteConcern.MAJORITY.withJournal(true));
        secondInstance.initialize(true);

        assertEquals(before.keySet(), listIndexesByName().keySet());
    }

    @Test
    @DisplayName("append joins the caller transaction and abort discards the event")
    void appendJoinsCallerTransaction() {
        ClientSession session = PublisherSync.first(mongoClient.startSession());
        try {
            session.startTransaction();
            journalEventStore.append(session, event("evt-A1", "stageA", 1L, false));
			abortTransaction(session);
        } finally {
            session.close();
        }

        assertFalse(journalEventStore.getLastEventByStream("stageA").isPresent());
    }

	@Test
	@DisplayName("append confirms after commit and reads the committed event without conflating acknowledgement")
	void appendConfirmsAndReadsCommittedEvent() {
		JournalEvent<AuditEntry> event = fixedEvent("committed-event", "committed-stream", 1L, false);

		Result result = appendInCommittedTransaction(event);

		assertEquals(Result.OK(), result);
		assertFalse(event.isAcknowledged());
		JournalEvent<AuditEntry> stored = journalEventStore.getLastEventByStream("committed-stream").orElseThrow(AssertionError::new);
		assertEquals("committed-event", stored.getEventId());
		assertEquals(1L, stored.getStreamSequence());
		assertFalse(stored.isAcknowledged());
	}

	@Test
	@DisplayName("append round-trips every journal envelope and audit-entry field")
	void appendRoundTripsCompleteEventAndAuditEntry() {
		JournalEvent<AuditEntry> event = fixedEvent("mapping-event", "mapping-stream", 7L, true);

		assertEquals(Result.OK(), appendInCommittedTransaction(event));

		JournalEvent<AuditEntry> stored = journalEventStore.getLastEventByStream("mapping-stream").orElseThrow(AssertionError::new);
		assertEquals(event.getEventId(), stored.getEventId());
		assertEquals(event.getEventType(), stored.getEventType());
		assertEquals(event.getEventVersion(), stored.getEventVersion());
		assertEquals(event.getStreamId(), stored.getStreamId());
		assertEquals(event.getStreamSequence(), stored.getStreamSequence());
		assertEquals(toMillis(event.getOccurredAt()), toMillis(stored.getOccurredAt()));
		assertTrue(stored.isAcknowledged());

		assertAuditEntryEquals(event.getData(), stored.getData());
	}

	@Test
	@DisplayName("committed events order and resolve latest independently per stream")
	void committedEventsOrderAndResolveLatestPerStream() {
		appendInCommittedTransaction(fixedEvent("A1", "stream-A", 1L, false));
		appendInCommittedTransaction(fixedEvent("A2", "stream-A", 2L, false));
		appendInCommittedTransaction(fixedEvent("A3", "stream-A", 3L, false));
		appendInCommittedTransaction(fixedEvent("B1", "stream-B", 1L, false));

		Map<String, List<Long>> sequencesByStream = journalEventStore.getUnacknowledgedEvents(10).stream()
				.collect(Collectors.groupingBy(JournalEvent::getStreamId,
						Collectors.mapping(JournalEvent::getStreamSequence, Collectors.toList())));

		assertEquals(Arrays.asList(1L, 2L, 3L), sequencesByStream.get("stream-A"));
		assertEquals(Collections.singletonList(1L), sequencesByStream.get("stream-B"));
		assertEquals("A3", journalEventStore.getLastEventByStream("stream-A")
				.orElseThrow(AssertionError::new).getEventId());
		assertEquals("B1", journalEventStore.getLastEventByStream("stream-B")
				.orElseThrow(AssertionError::new).getEventId());
		assertFalse(journalEventStore.getLastEventByStream("missing-stream").isPresent());
	}

    @Test
    @DisplayName("append inserts immutable events and rejects duplicate stream positions")
    void appendRejectsDuplicateStreamPosition() {
        writeInCommittedTransaction(event("evt-A1", "stageA", 1L, false));

        assertThrows(RuntimeException.class,
                () -> writeInCommittedTransaction(event("evt-other", "stageA", 1L, false)));

        Optional<JournalEvent<AuditEntry>> stored = journalEventStore.getLastEventByStream("stageA");
        assertTrue(stored.isPresent());
        assertEquals("evt-A1", stored.get().getEventId());
    }

    @Test
    @DisplayName("append rejects a duplicate event id even on another stream")
    void appendRejectsDuplicateEventId() {
        writeInCommittedTransaction(event("evt-A1", "stageA", 1L, false));

        assertThrows(RuntimeException.class,
                () -> writeInCommittedTransaction(event("evt-A1", "stageB", 1L, false)));

        assertTrue(journalEventStore.getLastEventByStream("stageA").isPresent());
        assertFalse(journalEventStore.getLastEventByStream("stageB").isPresent());
    }

    @Test
    @DisplayName("reads and acknowledgement preserve native driver ordering")
    void readsAndAcknowledgementPreserveOrder() {
        seed(Arrays.asList(
                event("evt-A1", "stageA", 1L, true),
                event("evt-A2", "stageA", 2L, false),
                event("evt-A3", "stageA", 3L, false),
                event("evt-B1", "stageB", 1L, false)));

        assertEquals("evt-A3", journalEventStore.getLastEventByStream("stageA").get().getEventId());
        assertEquals(Arrays.asList("evt-A2", "evt-A3", "evt-B1"), ids(
                journalEventStore.getUnacknowledgedEvents(10)));
        assertEquals(Arrays.asList("evt-A2", "evt-A3"), ids(
                journalEventStore.getUnacknowledgedEvents(2)));

        assertEquals(2L, journalEventStore.acknowledgeEvents(Arrays.asList("evt-A2", "evt-B1")));
        assertEquals(Collections.singletonList("evt-A3"), ids(
                journalEventStore.getUnacknowledgedEvents(10)));
        assertEquals(0L, journalEventStore.acknowledgeEvents(Collections.emptyList()));
    }

    private Map<String, Document> listIndexesByName() {
        return PublisherSync.collect(database.getCollection(JOURNAL_COLLECTION).listIndexes())
                .stream()
                .filter(index -> index.getString("name") != null)
                .collect(Collectors.toMap(index -> index.getString("name"), index -> index));
    }

    private void writeInCommittedTransaction(JournalEvent<AuditEntry> event) {
		appendInCommittedTransaction(event);
	}

	private Result appendInCommittedTransaction(JournalEvent<AuditEntry> event) {
        ClientSession session = PublisherSync.first(mongoClient.startSession());
        try {
            session.startTransaction();
			Result result = journalEventStore.append(session, event);
            PublisherSync.complete(session.commitTransaction());
			return result;
        } catch (RuntimeException exception) {
            try {
				abortTransaction(session);
            } catch (RuntimeException ignored) {
                // The original duplicate-write failure is the assertion target.
            }
            throw exception;
        } finally {
            session.close();
        }
    }

	private static void abortTransaction(ClientSession session) {
		PublisherSync.complete(session.abortTransaction());
	}

    private void seed(List<JournalEvent<AuditEntry>> events) {
        MongoCollection<Document> collection = database.getCollection(JOURNAL_COLLECTION);
        PublisherSync.collect(collection.insertMany(events.stream()
                .map(mapper::toDocument)
                .collect(Collectors.toList())));
    }

    private static List<String> ids(List<JournalEvent<AuditEntry>> events) {
        return events.stream().map(JournalEvent::getEventId).collect(Collectors.toList());
    }

	private static void assertAuditEntryEquals(AuditEntry expected, AuditEntry actual) {
		assertEquals(expected.getExecutionId(), actual.getExecutionId());
		assertEquals(expected.getStageId(), actual.getStageId());
		assertEquals(expected.getChangeId(), actual.getChangeId());
		assertEquals(expected.getAuthor(), actual.getAuthor());
		assertEquals(toMillis(expected.getCreatedAt()), toMillis(actual.getCreatedAt()));
		assertEquals(expected.getState(), actual.getState());
		assertEquals(expected.getType(), actual.getType());
		assertEquals(expected.getClassName(), actual.getClassName());
		assertEquals(expected.getMethodName(), actual.getMethodName());
		assertEquals(expected.getSourceFile(), actual.getSourceFile());
		assertEquals(expected.getExecutionMillis(), actual.getExecutionMillis());
		assertEquals(expected.getExecutionHostname(), actual.getExecutionHostname());
		assertEquals(expected.getMetadata(), actual.getMetadata());
		assertEquals(expected.getSystemChange(), actual.getSystemChange());
		assertEquals(expected.getErrorTrace(), actual.getErrorTrace());
		assertEquals(expected.getTxType(), actual.getTxType());
		assertEquals(expected.getTargetSystemId(), actual.getTargetSystemId());
		assertEquals(expected.getOrder(), actual.getOrder());
		assertEquals(expected.getRecoveryStrategy(), actual.getRecoveryStrategy());
		assertEquals(expected.getTransactionFlag(), actual.getTransactionFlag());
	}

	private static Instant toMillis(Instant instant) {
		return instant.truncatedTo(ChronoUnit.MILLIS);
	}

	private static LocalDateTime toMillis(LocalDateTime dateTime) {
		return dateTime.truncatedTo(ChronoUnit.MILLIS);
	}

	private static JournalEvent<AuditEntry> fixedEvent(String eventId,
														String streamId,
														long sequence,
														boolean acknowledged) {
		return new JournalEvent<>(
				eventId,
				JournalEventType.CHANGE_STATE,
				3,
				streamId,
				sequence,
				Instant.parse("2025-02-03T04:05:06.789123456Z"),
				new AuditEntry(
						"execution-fixed", "stage-fixed", "change-fixed", "author-fixed",
						LocalDateTime.parse("2025-02-03T04:05:06.789123456"),
						AuditEntry.Status.FAILED, AuditEntry.ChangeType.STANDARD_TEMPLATE,
						"example.Change", "apply", "Change.java", 9876L, "host-fixed",
						new Document("metadata", "value").append("count", 2), true, "error-fixed",
						AuditTxType.TX_SEPARATE_WITH_MARKER, "target-fixed", "order-fixed",
						RecoveryStrategy.ALWAYS_RETRY, Boolean.TRUE),
				acknowledged);
	}

    private static JournalEvent<AuditEntry> event(String eventId,
                                                  String streamId,
                                                  long sequence,
                                                  boolean acknowledged) {
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
