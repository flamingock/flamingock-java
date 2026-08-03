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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventFieldConstants;
import io.flamingock.store.dynamodb.DynamoDBTestContainer;
import io.flamingock.targetsystem.dynamodb.DynamoDBTxWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.KeyType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class DynamoDBJournalEventStoreTest {

    private static final String TABLE_NAME = "flamingockJournalEvents";

    @Container
    static final GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDbClient client;
    private DynamoDBJournalEventStore store;

    @BeforeEach
    void setUp() {
        client = DynamoDBTestContainer.createClient(dynamoDBContainer);
        deleteTableIfExists(TABLE_NAME);
        store = new DynamoDBJournalEventStore(client, TABLE_NAME, 5L, 5L);
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        deleteTableIfExists(TABLE_NAME);
    }

    @Test
    @DisplayName("Flag OFF: initialize must not create the journal table")
    void flagOffInitializeDoesNotCreateJournalTable() {
        // Given: feature flag off
        FeatureFlag.remove(Features.JOURNAL_EVENTS);

        // When: store initializes with autoCreate
        store.initialize(true);

        // Then: no journal table exists
        assertFalse(tableExists(TABLE_NAME));
    }

    @Test
    @DisplayName("Flag ON: initialize creates the journal table with both GSIs")
    void flagOnInitializeCreatesTableWithBothIndexes() {
        // Given: feature flag on
        FeatureFlag.enable(Features.JOURNAL_EVENTS);

        // When: store initializes with autoCreate
        store.initialize(true);

        // Then: table exists
        assertTrue(tableExists(TABLE_NAME));

        // And: both GSIs exist with the expected key schemas
        DescribeTableResponse response = client.describeTable(
                DescribeTableRequest.builder().tableName(TABLE_NAME).build());
        List<GlobalSecondaryIndexDescription> indexes = response.table().globalSecondaryIndexes();
        assertEquals(2, indexes.size());

        GlobalSecondaryIndexDescription pendingIndex = indexes.stream()
                .filter(index -> JournalEventFieldConstants.PENDING_EVENTS_INDEX.equals(index.indexName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PendingEventsIndex not found"));
        assertEquals(2, pendingIndex.keySchema().size());
        assertEquals(KeyType.HASH, pendingIndex.keySchema().get(0).keyType());
        assertEquals(JournalEventFieldConstants.KEY_PENDING_STREAM_ID, pendingIndex.keySchema().get(0).attributeName());
        assertEquals(KeyType.RANGE, pendingIndex.keySchema().get(1).keyType());
        assertEquals(JournalEventFieldConstants.KEY_STREAM_SEQUENCE, pendingIndex.keySchema().get(1).attributeName());

        GlobalSecondaryIndexDescription eventIdIndex = indexes.stream()
                .filter(index -> JournalEventFieldConstants.EVENT_ID_INDEX.equals(index.indexName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("EventIdIndex not found"));
        assertEquals(1, eventIdIndex.keySchema().size());
        assertEquals(KeyType.HASH, eventIdIndex.keySchema().get(0).keyType());
        assertEquals(JournalEventFieldConstants.KEY_EVENT_ID, eventIdIndex.keySchema().get(0).attributeName());
    }

    @Test
    @DisplayName("Flag ON with autoCreate false: initialize asserts an existing table without throwing")
    void flagOnInitializeWithAutoCreateFalseAssertsExistingTable() {
        // Given: flag on and table already created
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);

        // When: a second store initializes with autoCreate=false
        DynamoDBJournalEventStore assertStore = new DynamoDBJournalEventStore(client, TABLE_NAME, 5L, 5L);

        // Then: existence assertion passes without throwing
        assertDoesNotThrow(() -> assertStore.initialize(false));
    }

    @Test
    @DisplayName("Appending to an occupied stream position cancels the transaction and surfaces DatabaseTransactionException")
    void occupiedStreamPositionCancelsTransaction() {
        // Given: flag on, store initialized, and an event committed at (stage-1, 1)
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);
        commit(journalEvent("stage-1", 1L, "event-1"));

        // When: another event is appended at the same position
        JournalEvent<AuditEntry> collision = journalEvent("stage-1", 1L, "event-2");

        // Then: the transaction cancels and DatabaseTransactionException surfaces
        assertThrows(DatabaseTransactionException.class, () -> commit(collision));
    }

    @Test
    @DisplayName("Duplicate eventId at free positions is accepted and resolves through the non-unique EventIdIndex")
    void duplicateEventIdAtFreePositionsIsAccepted() throws InterruptedException {
        // Given: flag on, store initialized
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);

        // When: two events sharing an eventId are appended at distinct positions in one transaction
        String sharedEventId = "duplicate-event-id";
        commit(Arrays.asList(
                journalEvent("stage-1", 1L, sharedEventId),
                journalEvent("stage-1", 2L, sharedEventId)));

        // Then: both are accepted and both resolve through the non-unique eventId GSI
        // (GSI reads are eventually consistent, so tolerate retry per the spec contract)
        assertEquals(2L, awaitAcknowledgedCount(Collections.singletonList(sharedEventId), 2L));
        assertTrue(awaitUnacknowledgedCount(0).isEmpty());
    }

    @Test
    @DisplayName("getLastEventByStream returns the highest sequence per stream, desc, with full payload")
    void getLastEventByStreamReturnsHighestSequence() throws InterruptedException {
        // Given: flag on, store initialized, events 1..3 on stage-1 and 1 on stage-2
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);
        commit(journalEvent("stage-1", 1L, "event-1"));
        commit(journalEvent("stage-1", 2L, "event-2"));
        commit(journalEvent("stage-1", 3L, "event-3"));
        commit(journalEvent("stage-2", 1L, "event-4"));

        // When/Then: last event of stage-1 is sequence 3 with the full AuditEntry payload
        Optional<JournalEvent<AuditEntry>> last = store.getLastEventByStream("stage-1");
        assertTrue(last.isPresent());
        assertEquals(3L, last.get().getStreamSequence());
        assertEquals("event-3", last.get().getEventId());
        assertEquals(JournalEventType.CHANGE_STATE, last.get().getEventType());
        assertEquals("change-3", last.get().getData().getChangeId());

        // And: last event of stage-2 is sequence 1
        Optional<JournalEvent<AuditEntry>> other = store.getLastEventByStream("stage-2");
        assertTrue(other.isPresent());
        assertEquals(1L, other.get().getStreamSequence());

        // And: a stream with no events returns empty
        Optional<JournalEvent<AuditEntry>> none = store.getLastEventByStream("empty-stream");
        assertFalse(none.isPresent());
    }

    @Test
    @DisplayName("last-event query is strongly consistent for sequencer reseeding")
    void lastEventQueryIsStronglyConsistent() {
        QueryEnhancedRequest request = DynamoDBJournalEventStore.lastEventQuery("stage-1");

        assertTrue(request.consistentRead());
        assertFalse(request.scanIndexForward());
        assertEquals(1, request.limit());
    }

    @Test
    @DisplayName("a new sequencer after a committed event starts at the next sequence")
    void newSequencerAfterCommittedEventStartsAtNextSequence() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);
        commit(journalEvent("stage-1", 1L, "event-1"));

        JournalEventSequencer sequencer = new JournalEventSequencerFactory(store).forStream("stage-1");
        JournalEvent<AuditEntry> nextEvent = sequencer.newEvent(journalEvent("stage-1", 2L, "event-2").getData());

        assertEquals(2L, nextEvent.getStreamSequence());
    }

    @Test
    @DisplayName("getUnacknowledgedEvents scans the sparse pending GSI, sorted by stream and sequence, then limits")
    void getUnacknowledgedEventsScansPendingIndexSortedAndLimited() throws InterruptedException {
        // Given: flag on, store initialized, 3 events on stage-1 and 2 on stage-2
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);
        commit(journalEvent("stage-1", 1L, "event-1"));
        commit(journalEvent("stage-1", 2L, "event-2"));
        commit(journalEvent("stage-1", 3L, "event-3"));
        commit(journalEvent("stage-2", 1L, "event-4"));
        commit(journalEvent("stage-2", 2L, "event-5"));

        // When: all unacknowledged events are fetched (GSI reads are eventually consistent, so tolerate retry)
        List<JournalEvent<AuditEntry>> events = awaitUnacknowledgedCount(5);

        // Then: all 5 events come back sorted by (streamId, streamSequence)
        assertEquals(5, events.size());
        assertEquals("stage-1", events.get(0).getStreamId());
        assertEquals(1L, events.get(0).getStreamSequence());
        assertEquals("stage-1", events.get(1).getStreamId());
        assertEquals(2L, events.get(1).getStreamSequence());
        assertEquals("stage-1", events.get(2).getStreamId());
        assertEquals(3L, events.get(2).getStreamSequence());
        assertEquals("stage-2", events.get(3).getStreamId());
        assertEquals(1L, events.get(3).getStreamSequence());
        assertEquals("stage-2", events.get(4).getStreamId());
        assertEquals(2L, events.get(4).getStreamSequence());

        // And: the limit is applied after sorting
        List<JournalEvent<AuditEntry>> limited = store.getUnacknowledgedEvents(2);
        assertEquals(2, limited.size());
        assertEquals("stage-1", limited.get(0).getStreamId());
        assertEquals(1L, limited.get(0).getStreamSequence());
        assertEquals("stage-1", limited.get(1).getStreamId());
        assertEquals(2L, limited.get(1).getStreamSequence());
    }

    @Test
    @DisplayName("acknowledgeEvents resolves via EventIdIndex and drops the pending attribute")
    void acknowledgeEventsRemovesPendingAttribute() throws InterruptedException {
        // Given: flag on, store initialized, 2 unacknowledged events on stage-1
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);
        commit(journalEvent("stage-1", 1L, "event-1"));
        commit(journalEvent("stage-1", 2L, "event-2"));

        // When: only event-2 is acknowledged (GSI reads are eventually consistent, so tolerate retry)
        long acknowledged = awaitAcknowledgedCount(Collections.singletonList("event-2"), 1L);

        // Then: exactly one event was updated
        assertEquals(1L, acknowledged);

        // And: event-2 is absent from the unacknowledged set once consistent
        List<JournalEvent<AuditEntry>> remaining = awaitUnacknowledgedCount(1);
        assertEquals("event-1", remaining.get(0).getEventId());

        // And: empty collection acknowledges nothing
        assertEquals(0L, store.acknowledgeEvents(Collections.emptyList()));

        // And: re-acknowledging an already acknowledged event updates nothing
        assertEquals(0L, store.acknowledgeEvents(Collections.singletonList("event-2")));
    }

    private void commit(JournalEvent<AuditEntry> event) {
        commit(Collections.singletonList(event));
    }

    private void commit(List<JournalEvent<AuditEntry>> events) {
        DynamoDBTxWrapper txWrapper = new DynamoDBTxWrapper(
                client,
                new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder));
        txWrapper.wrapInTransaction(new BasicRuntimeContext("session-" + UUID.randomUUID()), ctx -> {
            TransactWriteItemsEnhancedRequest.Builder builder = ctx.getContext()
                    .getRequiredDependencyValue(TransactWriteItemsEnhancedRequest.Builder.class);
            for (JournalEvent<AuditEntry> event : events) {
                store.write(builder, event);
            }
            return Result.OK();
        });
    }

    private JournalEvent<AuditEntry> journalEvent(String streamId, long sequence, String eventId) {
        AuditEntry auditEntry = new AuditEntry(
                "execution-1",
                streamId,
                "change-" + sequence,
                "author",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                AuditEntry.Status.APPLIED,
                AuditEntry.ChangeType.STANDARD_CODE,
                "com.example.Change",
                "apply",
                "Source.java",
                150L,
                "host-1",
                "metadata",
                false,
                "no-error",
                AuditTxType.NON_TX,
                "dynamodb",
                "1",
                RecoveryStrategy.MANUAL_INTERVENTION,
                true);
        return new JournalEvent<>(eventId, JournalEventType.CHANGE_STATE, streamId, sequence, Instant.now(), auditEntry);
    }

    private List<JournalEvent<AuditEntry>> awaitUnacknowledgedCount(int expected) throws InterruptedException {
        List<JournalEvent<AuditEntry>> events = Collections.emptyList();
        for (int attempt = 0; attempt < 50; attempt++) {
            events = store.getUnacknowledgedEvents(10);
            if (events.size() == expected) {
                return events;
            }
            Thread.sleep(100);
        }
        return events;
    }

    private long awaitAcknowledgedCount(Collection<String> eventIds, long expected) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            long acknowledged = store.acknowledgeEvents(eventIds);
            if (acknowledged == expected) {
                return acknowledged;
            }
            Thread.sleep(100);
        }
        return store.acknowledgeEvents(eventIds);
    }

    private boolean tableExists(String tableName) {
        return client.listTables().tableNames().contains(tableName);
    }

    private void deleteTableIfExists(String tableName) {
        if (tableExists(tableName)) {
            client.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
        }
    }
}
