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
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.dynamodb.entities.journal.DynamoDBJournalEventMapper;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventFieldConstants;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventEntity;
import io.flamingock.store.dynamodb.DynamoDBTestContainer;
import io.flamingock.targetsystem.dynamodb.DynamoDBTxWrapper;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY,
                pendingIndex.keySchema().get(0).attributeName());
        assertEquals(KeyType.RANGE, pendingIndex.keySchema().get(1).keyType());
        assertEquals(JournalEventFieldConstants.KEY_PENDING_ORDER_KEY,
                pendingIndex.keySchema().get(1).attributeName());
        assertEquals(software.amazon.awssdk.services.dynamodb.model.ProjectionType.ALL,
                pendingIndex.projection().projectionType());

        GlobalSecondaryIndexDescription eventIdIndex = indexes.stream()
                .filter(index -> JournalEventFieldConstants.EVENT_ID_INDEX.equals(index.indexName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("EventIdIndex not found"));
        assertEquals(1, eventIdIndex.keySchema().size());
        assertEquals(KeyType.HASH, eventIdIndex.keySchema().get(0).keyType());
        assertEquals(JournalEventFieldConstants.KEY_EVENT_ID, eventIdIndex.keySchema().get(0).attributeName());
        assertEquals(software.amazon.awssdk.services.dynamodb.model.ProjectionType.KEYS_ONLY,
                eventIdIndex.projection().projectionType());
    }

    @Test
    @DisplayName("reservation stream keys use a namespaced length-prefixed UTF-8 hex encoding")
    void reservationStreamIdUsesCollisionSafeEncoding() {
        assertEquals("__flamingock_reservation__:1:41",
                DynamoDBJournalEventStore.reservationStreamId("A"));
        assertEquals("__flamingock_reservation__:2:C3A9",
                DynamoDBJournalEventStore.reservationStreamId("é"));
        assertNotEquals(
                DynamoDBJournalEventStore.reservationStreamId("a:b"),
                DynamoDBJournalEventStore.reservationStreamId("a"));
    }

    @Test
    @DisplayName("reservation keys reject encoded values beyond DynamoDB partition-key size")
    void reservationStreamIdRejectsOversizedEncodedKey() {
        String oversizedEventId = new String(new char[1024]).replace('\0', 'a');

        assertThrows(IllegalArgumentException.class,
                () -> DynamoDBJournalEventStore.reservationStreamId(oversizedEventId));
    }

    @Test
    @DisplayName("malformed journal flags fail closed without initializing the table")
    void malformedJournalFlagDoesNotInitializeJournalTable() {
        try (MockedStatic<FeatureFlag> flags = Mockito.mockStatic(FeatureFlag.class)) {
            flags.when(() -> FeatureFlag.ifEnabled(
                            ArgumentMatchers.eq(Features.JOURNAL_EVENTS),
                            ArgumentMatchers.any(Runnable.class)))
                    .thenThrow(new IllegalArgumentException("malformed feature value"));
            flags.when(() -> FeatureFlag.isEnabled(Features.JOURNAL_EVENTS))
                    .thenThrow(new IllegalArgumentException("malformed feature value"));
            flags.when(() -> FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false))
                    .thenThrow(new IllegalArgumentException("malformed feature value"));

            assertDoesNotThrow(() -> store.initialize(true));
        }

        assertFalse(tableExists(TABLE_NAME));
    }

    @Test
    @DisplayName("unknown journal flags remain disabled")
    void unknownJournalFlagDoesNotInitializeJournalTable() {
        String unknownFlag = "unknown-journal-flag";
        FeatureFlag.enable(unknownFlag);
        try {
            store.initialize(true);
            assertFalse(tableExists(TABLE_NAME));
        } finally {
            FeatureFlag.remove(unknownFlag);
        }
    }

    @Test
    @DisplayName("auto-create validates an existing journal table before binding")
    void autoCreateValidatesExistingJournalSchema() {
        DynamoDBUtil dynamoDBUtil = new DynamoDBUtil(client);
        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions("wrongKey", null),
                dynamoDBUtil.getKeySchemas("wrongKey", null),
                dynamoDBUtil.getProvisionedThroughput(5L, 5L),
                TABLE_NAME,
                Collections.emptyList(),
                Collections.emptyList());
        FeatureFlag.enable(Features.JOURNAL_EVENTS);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.initialize(true));

        assertTrue(exception.getMessage().contains("invalid key or index schema"));
    }

    @Test
    @DisplayName("one-table reservations are permanent and invisible to journal indexes")
    void reservationIsStoredInJournalTableWithoutIndexAttributes() throws InterruptedException {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);

        commit(journalEvent("stage-1", 1L, "reserved-event"));

        assertEquals(1, client.listTables().tableNames().size());
        List<Map<String, AttributeValue>> reservationItems = client.scan(
                        ScanRequest.builder().tableName(TABLE_NAME).consistentRead(true).build())
                .items()
                .stream()
                .filter(item -> item.containsKey(JournalEventFieldConstants.KEY_STREAM_SEQUENCE)
                        && "0".equals(item.get(JournalEventFieldConstants.KEY_STREAM_SEQUENCE).n()))
                .collect(Collectors.toList());
        assertEquals(1, reservationItems.size());
        Map<String, AttributeValue> reservation = reservationItems.get(0);
        assertEquals(DynamoDBJournalEventStore.reservationStreamId("reserved-event"),
                reservation.get(JournalEventFieldConstants.KEY_STREAM_ID).s());
        assertFalse(reservation.containsKey(JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY));
        assertFalse(reservation.containsKey(JournalEventFieldConstants.KEY_PENDING_ORDER_KEY));
        assertFalse(reservation.containsKey(JournalEventFieldConstants.KEY_EVENT_ID));
        assertFalse(reservation.containsKey("payload"));

        assertEquals(1, store.getUnacknowledgedEvents(10).size());
        assertEquals(1L, store.acknowledgeEvents(Collections.singletonList("reserved-event")));
        assertFalse(store.getLastEventByStream(
                DynamoDBJournalEventStore.reservationStreamId("reserved-event")).isPresent());

        List<Map<String, AttributeValue>> retainedReservations = client.scan(
                        ScanRequest.builder().tableName(TABLE_NAME).consistentRead(true).build())
                .items()
                .stream()
                .filter(item -> item.containsKey(JournalEventFieldConstants.KEY_STREAM_SEQUENCE)
                        && "0".equals(item.get(JournalEventFieldConstants.KEY_STREAM_SEQUENCE).n()))
                .collect(Collectors.toList());
        assertEquals(1, retainedReservations.size());
    }

    @Test
    @DisplayName("journal-store reservation rejects duplicate event IDs at free positions")
    void journalStoreRejectsDuplicateEventIdWithoutSeparateStore() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);
        commit(journalEvent("stage-1", 1L, "duplicate-event-id"));

        assertThrows(DatabaseTransactionException.class,
                () -> commit(journalEvent("stage-1", 2L, "duplicate-event-id")));

        assertEquals(1, store.getUnacknowledgedEvents(10).size());
    }

    @Test
    @DisplayName("occupied positions roll back their reservation together with the failed event")
    void occupiedPositionRollsBackReservationAtomically() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);
        commit(journalEvent("stage-1", 1L, "first-event"));

        assertThrows(DatabaseTransactionException.class,
                () -> commit(journalEvent("stage-1", 1L, "retryable-event")));

        commit(journalEvent("stage-1", 2L, "retryable-event"));

        assertEquals(2, store.getUnacknowledgedEvents(10).size());
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
    @DisplayName("Duplicate eventId is rejected atomically even when the stream positions are free")
    void duplicateEventIdIsRejectedAtomicallyAtFreePositions() throws InterruptedException {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);

        String sharedEventId = "duplicate-event-id";
        commit(journalEvent("stage-1", 1L, sharedEventId));

        assertThrows(DatabaseTransactionException.class,
                () -> commit(journalEvent("stage-1", 2L, sharedEventId)));

        List<JournalEvent<AuditEntry>> events = awaitUnacknowledgedCount(1);
        assertEquals(1, events.size());
        assertEquals(1L, events.get(0).getStreamSequence());
        assertEquals(sharedEventId, events.get(0).getEventId());
    }

    @Test
    @DisplayName("distinct event IDs preserve deterministic pending position order")
    void distinctEventIdsPreservePendingPositionOrder() throws InterruptedException {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);
        commit(Arrays.asList(
                journalEvent("stage-2", 1L, "event-stage-2"),
                journalEvent("stage-1", 2L, "event-stage-1-second"),
                journalEvent("stage-1", 1L, "event-stage-1-first")));

        List<JournalEvent<AuditEntry>> events = awaitUnacknowledgedCount(3);

        assertEquals(3, events.size());
        assertEquals("stage-1", events.get(0).getStreamId());
        assertEquals(1L, events.get(0).getStreamSequence());
        assertEquals("stage-1", events.get(1).getStreamId());
        assertEquals(2L, events.get(1).getStreamSequence());
        assertEquals("stage-2", events.get(2).getStreamId());
        assertEquals(1L, events.get(2).getStreamSequence());
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
    @DisplayName("pending query uses the sparse partition and ascending order")
    void pendingQueryUsesSparsePartitionAndAscendingOrder() {
        QueryEnhancedRequest request = DynamoDBJournalEventStore.pendingEventsQuery(3);
        Expression keyCondition = request.queryConditional()
                .expression(TableSchema.fromBean(JournalEventEntity.class), JournalEventFieldConstants.PENDING_EVENTS_INDEX);

        assertTrue(request.scanIndexForward());
        assertEquals(3, request.limit());
        assertTrue(request.filterExpression() == null);
        assertTrue(keyCondition.expressionNames().containsValue(JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY));
        assertEquals(JournalEventFieldConstants.PENDING_PARTITION_VALUE,
                keyCondition.expressionValues().values().iterator().next().s());
    }

    @Test
    @DisplayName("mapper writes a constant pending partition and collision-safe position order key")
    void mapperWritesCollisionSafePendingOrderKey() {
        JournalEventEntity singleCharacter = DynamoDBJournalEventMapper.toEntity(
                journalEvent("A", 1L, "same-event-id"));
        JournalEventEntity longerPrefix = DynamoDBJournalEventMapper.toEntity(
                journalEvent("AA", 1L, "same-event-id"));

        assertEquals(JournalEventFieldConstants.PENDING_PARTITION_VALUE,
                singleCharacter.getPendingPartitionKey());
        assertEquals("0041!0000000000000001", singleCharacter.getPendingOrderKey());
        assertEquals("00410041!0000000000000001", longerPrefix.getPendingOrderKey());
        assertTrue(singleCharacter.getPendingOrderKey().compareTo(longerPrefix.getPendingOrderKey()) < 0);
    }

    @Test
    @DisplayName("mapper omits both pending keys for an acknowledged event")
    void mapperOmitsBothPendingKeysWhenAcknowledged() {
        JournalEvent<AuditEntry> source = journalEvent("stage-1", 1L, "event-1");
        JournalEvent<AuditEntry> acknowledged = new JournalEvent<>(
                source.getEventId(),
                source.getEventType(),
                source.getEventVersion(),
                source.getStreamId(),
                source.getStreamSequence(),
                source.getOccurredAt(),
                source.getData(),
                true);

        JournalEventEntity entity = DynamoDBJournalEventMapper.toEntity(acknowledged);

        assertNull(entity.getPendingPartitionKey());
        assertNull(entity.getPendingOrderKey());
    }

    @Test
    @DisplayName("pending order key keeps positive sequence ordering with fixed-width hexadecimal")
    void pendingOrderKeyKeepsSequenceOrdering() {
        String lowerSequence = DynamoDBJournalEventMapper.pendingOrderKey("stage-1", 2L);
        String higherSequence = DynamoDBJournalEventMapper.pendingOrderKey("stage-1", 16L);

        assertEquals("00730074006100670065002D0031!0000000000000002", lowerSequence);
        assertEquals("00730074006100670065002D0031!0000000000000010", higherSequence);
        assertTrue(lowerSequence.compareTo(higherSequence) < 0);
    }

    @Test
    @DisplayName("pending order key rejects values exceeding DynamoDB sort-key size")
    void pendingOrderKeyRejectsOversizedStreamId() {
        String oversizedStreamId = new String(new char[252]).replace('\0', 'a');

        assertThrows(IllegalArgumentException.class,
                () -> DynamoDBJournalEventMapper.pendingOrderKey(oversizedStreamId, 1L));
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
    @DisplayName("getUnacknowledgedEvents returns position-ordered bounded events")
    void getUnacknowledgedEventsQueriesPendingIndexSortedAndLimited() throws InterruptedException {
        // Given: flag on, store initialized, 3 events on stage-1 and 2 on stage-2
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        store.initialize(true);
        commit(journalEvent("stage-1", 1L, "event-1"));
        commit(journalEvent("stage-1", 2L, "event-2"));
        commit(journalEvent("stage-1", 3L, "event-3"));
        commit(journalEvent("stage-2", 1L, "event-4"));
        commit(journalEvent("stage-2", 2L, "event-5"));

        // When: a positive limit is requested
        List<JournalEvent<AuditEntry>> limited = store.getUnacknowledgedEvents(2);

        // Then: the native bounded query preserves pending-index order
        assertEquals(2, limited.size());
        assertEquals("stage-1", limited.get(0).getStreamId());
        assertEquals(1L, limited.get(0).getStreamSequence());
        assertEquals("stage-1", limited.get(1).getStreamId());
        assertEquals(2L, limited.get(1).getStreamSequence());
    }

    @Test
    @DisplayName("getUnacknowledgedEvents forwards every integer limit to the native query")
    void getUnacknowledgedEventsForwardsEveryIntegerLimit() throws Exception {
        DynamoDBJournalEventStore requestStore = new DynamoDBJournalEventStore(
                Mockito.mock(DynamoDbClient.class), TABLE_NAME, 5L, 5L);
        @SuppressWarnings("unchecked")
        software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex<JournalEventEntity> pendingIndex =
                Mockito.mock(software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex.class);
        Field pendingIndexField = DynamoDBJournalEventStore.class.getDeclaredField("pendingEventsIndex");
        pendingIndexField.setAccessible(true);
        pendingIndexField.set(requestStore, pendingIndex);

        List<QueryEnhancedRequest> requests = new java.util.ArrayList<>();
        Mockito.when(pendingIndex.query(ArgumentMatchers.any(QueryEnhancedRequest.class)))
                .thenAnswer(invocation -> {
                    requests.add(invocation.getArgument(0));
                    return PageIterable.create(() -> Collections.<Page<JournalEventEntity>>emptyList().iterator());
                });

        requestStore.getUnacknowledgedEvents(3);
        requestStore.getUnacknowledgedEvents(0);
        requestStore.getUnacknowledgedEvents(-1);

        assertEquals(Arrays.asList(3, 0, -1), requests.stream()
                .map(QueryEnhancedRequest::limit)
                .collect(Collectors.toList()));
        Mockito.verify(pendingIndex, Mockito.times(3))
                .query(ArgumentMatchers.any(QueryEnhancedRequest.class));
    }

    @Test
    @DisplayName("getUnacknowledgedEvents consumes only the native bounded page")
    void getUnacknowledgedEventsDoesNotFollowContinuationPages() throws Exception {
        DynamoDBJournalEventStore requestStore = new DynamoDBJournalEventStore(
                Mockito.mock(DynamoDbClient.class), TABLE_NAME, 5L, 5L);
        @SuppressWarnings("unchecked")
        software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex<JournalEventEntity> pendingIndex =
                Mockito.mock(software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex.class);
        Field pendingIndexField = DynamoDBJournalEventStore.class.getDeclaredField("pendingEventsIndex");
        pendingIndexField.setAccessible(true);
        pendingIndexField.set(requestStore, pendingIndex);

        Map<String, AttributeValue> continuation = Collections.singletonMap(
                JournalEventFieldConstants.KEY_STREAM_ID, AttributeValue.builder().s("stage-1").build());
        Page<JournalEventEntity> page = Page.create(Collections.singletonList(
                DynamoDBJournalEventMapper.toEntity(journalEvent("stage-1", 1L, "event-1"))), continuation);
        Mockito.when(pendingIndex.query(ArgumentMatchers.any(QueryEnhancedRequest.class)))
                .thenReturn(PageIterable.create(() -> Collections.singletonList(page).iterator()));

        List<JournalEvent<AuditEntry>> events = requestStore.getUnacknowledgedEvents(3);

        assertEquals(Collections.singletonList("event-1"), events.stream()
                .map(JournalEvent::getEventId)
                .collect(Collectors.toList()));
        Mockito.verify(pendingIndex, Mockito.times(1))
                .query(ArgumentMatchers.any(QueryEnhancedRequest.class));
    }

    @Test
    @DisplayName("acknowledgement de-duplicates lookup IDs and removes both pending keys")
    void acknowledgementDeDuplicatesIdsAndRemovesBothPendingKeys() throws Exception {
        DynamoDbClient mockedClient = Mockito.mock(DynamoDbClient.class);
        DynamoDBJournalEventStore requestStore = new DynamoDBJournalEventStore(
                mockedClient, TABLE_NAME, 5L, 5L);
        @SuppressWarnings("unchecked")
        software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex<JournalEventEntity> eventIdIndex =
                Mockito.mock(software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex.class);
        Field eventIdIndexField = DynamoDBJournalEventStore.class.getDeclaredField("eventIdIndex");
        eventIdIndexField.setAccessible(true);
        eventIdIndexField.set(requestStore, eventIdIndex);

        JournalEventEntity first = DynamoDBJournalEventMapper.toEntity(journalEvent("stage-1", 1L, "first-event"));
        JournalEventEntity second = DynamoDBJournalEventMapper.toEntity(journalEvent("stage-1", 2L, "second-event"));
        Page<JournalEventEntity> firstPage = Page.create(
                Collections.singletonList(first), Collections.singletonMap("eventId", AttributeValue.builder().s("first-event").build()));
        Page<JournalEventEntity> secondPage = Page.create(Collections.singletonList(second));
        int[] queryCount = {0};
        Mockito.when(eventIdIndex.query(ArgumentMatchers.any(QueryConditional.class)))
                .thenAnswer(invocation -> PageIterable.create(() -> Collections.singletonList(
                        queryCount[0]++ == 0 ? firstPage : secondPage).iterator()));

        long acknowledged = requestStore.acknowledgeEvents(Arrays.asList("first-event", "first-event", "second-event"));

        assertEquals(2L, acknowledged);
        Mockito.verify(eventIdIndex, Mockito.times(2)).query(ArgumentMatchers.any(QueryConditional.class));
        ArgumentCaptor<UpdateItemRequest> requests = ArgumentCaptor.forClass(UpdateItemRequest.class);
        Mockito.verify(mockedClient, Mockito.times(2)).updateItem(requests.capture());
        assertTrue(requests.getAllValues().stream()
                .allMatch(request -> request.updateExpression().contains(JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY)
                        && request.updateExpression().contains(JournalEventFieldConstants.KEY_PENDING_ORDER_KEY)
                        && request.conditionExpression().contains(JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY)
                        && request.conditionExpression().contains(JournalEventFieldConstants.KEY_PENDING_ORDER_KEY)));
    }

    @Test
    @DisplayName("acknowledgement skips blank IDs without issuing lookup queries")
    void acknowledgementSkipsBlankIdsWithoutReading() throws Exception {
        DynamoDbClient mockedClient = Mockito.mock(DynamoDbClient.class);
        DynamoDBJournalEventStore requestStore = new DynamoDBJournalEventStore(
                mockedClient, TABLE_NAME, 5L, 5L);
        @SuppressWarnings("unchecked")
        software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex<JournalEventEntity> eventIdIndex =
                Mockito.mock(software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex.class);
        Field eventIdIndexField = DynamoDBJournalEventStore.class.getDeclaredField("eventIdIndex");
        eventIdIndexField.setAccessible(true);
        eventIdIndexField.set(requestStore, eventIdIndex);

        assertEquals(0L, requestStore.acknowledgeEvents(Arrays.asList(null, "", " ")));
        Mockito.verifyNoInteractions(eventIdIndex);
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

        Map<String, AttributeValue> key = new HashMap<>();
        key.put(JournalEventFieldConstants.KEY_STREAM_ID,
                AttributeValue.builder().s("stage-1").build());
        key.put(JournalEventFieldConstants.KEY_STREAM_SEQUENCE,
                AttributeValue.builder().n("2").build());
        Map<String, AttributeValue> acknowledgedItem = client.getItem(GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .consistentRead(true)
                .build())
                .item();
        assertFalse(acknowledgedItem.containsKey(JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY));
        assertFalse(acknowledgedItem.containsKey(JournalEventFieldConstants.KEY_PENDING_ORDER_KEY));
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
                store.contributeToTransaction(builder, event);
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
        return awaitUnacknowledgedCount(10, expected);
    }

    private List<JournalEvent<AuditEntry>> awaitUnacknowledgedCount(int limit, int expected)
            throws InterruptedException {
        List<JournalEvent<AuditEntry>> events = Collections.emptyList();
        for (int attempt = 0; attempt < 50; attempt++) {
            events = store.getUnacknowledgedEvents(limit);
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
