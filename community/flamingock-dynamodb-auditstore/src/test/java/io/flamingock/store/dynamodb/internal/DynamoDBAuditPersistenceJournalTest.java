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

import com.fasterxml.jackson.databind.JsonNode;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.external.store.AuditHistoryAppender;
import io.flamingock.internal.core.external.store.JournalHistoryAppender;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.JsonObjectMapper;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import io.flamingock.internal.util.dynamodb.entities.journal.DynamoDBJournalEventMapper;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventEntity;
import io.flamingock.store.dynamodb.DynamoDBTestContainer;
import io.flamingock.targetsystem.dynamodb.DynamoDBTxWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * Drives the DynamoDB persistence directly so the audit put and journal put can be verified at one transaction
 * boundary without depending on a full pipeline.
 */
@Testcontainers
class DynamoDBAuditPersistenceJournalTest {

    private static final String STREAM_ID = "stage-under-test";

    @Container
    static final GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDbClient client;
    private DynamoDBTxWrapper txWrapper;
    private DynamoDBJournalEventStore journalEventStore;
    private String auditTableName;
    private String journalTableName;

    @BeforeEach
    void setUp() {
        client = DynamoDBTestContainer.createClient(dynamoDBContainer);
        auditTableName = tableName("journalAudit");
        journalTableName = tableName("journalEvents");
        txWrapper = new DynamoDBTxWrapper(
                client,
                new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder));
        journalEventStore = spy(new DynamoDBJournalEventStore(client, journalTableName, 5L, 5L));
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        deleteTable(auditTableName);
        deleteTable(journalTableName);
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("journal disabled keeps append audit records and does not create the journal table")
    void journalDisabledKeepsAppendAuditPathAndCreatesNoJournalTable() {
        DynamoDBAuditPersistence persistence = persistenceFor();
        AuditEntry started = auditEntry("change-1", AuditEntry.Status.STARTED);
        AuditEntry applied = auditEntry("change-1", AuditEntry.Status.APPLIED);

        persistence.writeEntry(started);
        persistence.writeEntry(applied);

        List<AuditEntryEntity> records = storedAuditRecords();
        assertEquals(2, records.size(), "flag OFF must keep one append record per state transition");
        assertTrue(records.stream().map(AuditEntryEntity::getPartitionKey).collect(Collectors.toList())
                        .contains(AuditEntryEntity.partitionKey(started.getExecutionId(), started.getChangeId(), started.getState())));
        assertTrue(records.stream().map(AuditEntryEntity::getPartitionKey).collect(Collectors.toList())
                        .contains(AuditEntryEntity.partitionKey(applied.getExecutionId(), applied.getChangeId(), applied.getState())));
        assertFalse(tableExists(journalTableName), "flag OFF must not initialize the journal table");
    }

    @Test
    @DisplayName("journal enabled commits one event with the audit record on the persistence stream")
    void journalEnabledWritesEventAlongsideAuditRecord() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();
        AuditEntry entry = auditEntry("change-1", AuditEntry.Status.APPLIED);

        persistence.writeEntry(entry);

        assertEquals(1, persistence.getAuditHistory().size());
        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(1, events.size(), "exactly one event must be emitted per transition");
        JournalEvent<AuditEntry> event = events.get(0);
        assertEquals(STREAM_ID, event.getStreamId());
        assertEquals(1L, event.getStreamSequence());
        assertEquals(JournalEventType.CHANGE_STATE, event.getEventType());
        assertFalse(event.isAcknowledged());
        assertEquals(entry.getChangeId(), event.getData().getChangeId());
    }

    @Test
    @DisplayName("journal-enabled persistence creates the journal beside an existing audit table")
    void journalEnabledCreatesJournalFromAuditOnlyInstallation() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        new DynamoDBAuditRepository(client, auditTableName, 5L, 5L).initialize(true);

        DynamoDBAuditPersistence persistence = persistenceFor();
        persistence.writeEntry(auditEntry("audit-only-change", AuditEntry.Status.APPLIED));

        assertTrue(tableExists(auditTableName));
        assertTrue(tableExists(journalTableName));
        assertEquals(1, storedEvents().size());
        assertEquals("audit-only-change", storedEvents().get(0).getData().getChangeId());
    }

    @Test
    @DisplayName("persistence uses current envelope time and retains historical imported timestamp")
    void journalEnabledSeparatesEnvelopeAndImportedEntryTimes() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();
        LocalDateTime historical = LocalDateTime.of(2020, 1, 2, 3, 4, 5);
        AuditEntry imported = importedAuditEntry("legacy-timestamp", historical);

        persistence.writeEntry(imported);

        JournalEvent<AuditEntry> event = storedEvents().get(0);
        assertEquals(historical, event.getData().getCreatedAt());
        assertTrue(event.getOccurredAt().isAfter(historical.toInstant(ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("journal enabled collapses successive states to one current audit record while retaining both events")
    void journalEnabledKeepsCurrentStateAuditRecordAndJournalHistory() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        List<AuditEntry> auditRecords = persistence.getAuditHistory();
        assertEquals(1, auditRecords.size(), "flag ON must use the changeId-only current-state key");
        assertEquals(AuditEntry.Status.APPLIED, auditRecords.get(0).getState());

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(2, events.size(), "every state transition must remain in the journal");
        assertTrue(events.stream().anyMatch(event -> event.getStreamSequence() == 1L));
        assertTrue(events.stream().anyMatch(event -> event.getStreamSequence() == 2L));
    }

    @Test
    @DisplayName("journal enabled keeps imported audits as current state while retaining every legacy event")
    void journalEnabledKeepsImportedAuditAsCurrentStateAndJournalHistory() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();

        persistence.writeEntry(legacyAuditEntry("legacy-change", AuditEntry.Status.STARTED));
        persistence.writeEntry(legacyAuditEntry("legacy-change", AuditEntry.Status.APPLIED));

        List<AuditEntryEntity> auditRecords = storedAuditRecords();
        assertEquals(1, auditRecords.size(), "flag ON must retain one current audit record for an imported change");
        assertEquals("legacy-change", auditRecords.get(0).getPartitionKey());
        assertEquals(AuditEntry.Status.APPLIED.name(), auditRecords.get(0).getState());

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(2, events.size(), "every imported state must remain in the journal");
        assertTrue(events.stream().allMatch(event -> "legacy-change".equals(event.getData().getChangeId())));
    }

    @Test
    @DisplayName("a canceled transaction rolls back the audit record with the journal event")
    void canceledTransactionRollsBackAuditAndJournalWrites() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();
        conflictNextJournalContribution();

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED)));

        assertTrue(persistence.getAuditHistory().isEmpty(), "the audit put must roll back with the canceled transaction");
        assertEquals(1, storedEvents().size(), "only the pre-existing stream-position occupant may remain");
    }

    @Test
    @DisplayName("a canceled transaction does not confirm the sequence and the next retry reuses the position")
    void canceledTransactionLeavesNoGapForRetry() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();
        conflictNextJournalContribution();

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(auditEntry("failed-change", AuditEntry.Status.APPLIED)));
        deleteStreamPosition(1L);

        persistence.writeEntry(auditEntry("successful-change", AuditEntry.Status.APPLIED));

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(1, events.size());
        assertEquals(1L, events.get(0).getStreamSequence(),
                "confirm must be skipped after cancellation so the retry uses the unspent position");
        assertEquals("successful-change", events.get(0).getData().getChangeId());
    }

    @Test
    void historicalAuditAppendKeepsEveryHistoricalKeyEvenWithJournalEnabled() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();
        AuditHistoryAppender appender = (AuditHistoryAppender) persistence;
        AuditEntry started = legacyAuditEntry("historical-change", AuditEntry.Status.STARTED);
        AuditEntry applied = importedAuditEntry("historical-change", LocalDateTime.of(2019, 2, 3, 4, 5));

        assertFalse(appender.append(started).isError());
        assertFalse(appender.append(applied).isError());

        List<AuditEntryEntity> records = storedAuditRecords();
        assertEquals(2, records.size());
        for (AuditEntry entry : Arrays.asList(started, applied)) {
            assertTrue(records.stream().anyMatch(record -> record.getPartitionKey().equals(
                    AuditEntryEntity.partitionKey(entry.getExecutionId(), entry.getChangeId(), entry.getState()))));
        }
        AuditEntry stored = persistence.getAuditHistory().stream()
                .filter(record -> record.getState() == AuditEntry.Status.APPLIED).findFirst().get();
        assertEquals(applied.getExecutionId(), stored.getExecutionId());
        assertEquals(applied.getStageId(), stored.getStageId());
        assertEquals(applied.getCreatedAt(), stored.getCreatedAt());
        assertEquals(applied.getType(), stored.getType());
        assertEquals(applied.getAuthor(), stored.getAuthor());
        assertTrue(storedEvents().isEmpty());
    }

    @Test
    void publicSourceAppendGeneratesEnvelopePreservesHistoricalPayloadAndDoesNotMutateCurrentAudit() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();
        JournalHistoryAppender appender = persistence;
        AuditEntry current = auditEntry("current", AuditEntry.Status.STARTED);
        persistence.writeEntry(current);
        AuditEntry payload = importedAuditEntry("imported", LocalDateTime.of(2018, 1, 2, 3, 4));
        Instant before = Instant.now();

        assertFalse(appender.appendEventFrom(payload).isError());

        JournalEvent<AuditEntry> stored = storedEvents().stream()
                .filter(event -> payload.getStageId().equals(event.getStreamId())).findFirst().get();
        assertEquals(1L, stored.getStreamSequence());
        assertFalse(stored.getEventId().isEmpty());
        assertEquals(new JournalEventSequencerFactory(journalEventStore).forStream(payload.getStageId())
                .newEvent(payload).getIdempotencyKey(), stored.getIdempotencyKey());
        assertEquals(JournalEventType.CHANGE_STATE, stored.getEventType());
        assertEquals(JournalEvent.DEFAULT_VERSION, stored.getEventVersion());
        assertEquals(payload.getStageId(), stored.getStreamId());
        assertFalse(stored.getOccurredAt().isBefore(before));
        assertFalse(stored.getOccurredAt().isAfter(Instant.now()));
        assertFalse(stored.isAcknowledged());
        assertEquals(payload.getExecutionId(), stored.getData().getExecutionId());
        assertEquals(payload.getStageId(), stored.getData().getStageId());
        assertFalse(STREAM_ID.equals(payload.getStageId()), "payload stage must differ from the persistence stage");
        assertEquals(payload.getChangeId(), stored.getData().getChangeId());
        assertEquals(payload.getState(), stored.getData().getState());
        assertEquals(payload.getAuthor(), stored.getData().getAuthor());
        assertEquals(payload.getCreatedAt(), stored.getData().getCreatedAt());
        assertEquals(payload.getType(), stored.getData().getType());
        assertEquals(payload.getMetadata(), stored.getData().getMetadata());
        assertEquals(payload.getClassName(), stored.getData().getClassName());
        assertEquals(payload.getMethodName(), stored.getData().getMethodName());
        assertEquals(payload.getSourceFile(), stored.getData().getSourceFile());
        assertEquals(payload.getExecutionMillis(), stored.getData().getExecutionMillis());
        assertEquals(payload.getExecutionHostname(), stored.getData().getExecutionHostname());
        assertEquals(payload.getSystemChange(), stored.getData().getSystemChange());
        assertEquals(payload.getErrorTrace(), stored.getData().getErrorTrace());
        assertEquals(payload.getTxType(), stored.getData().getTxType());
        assertEquals(payload.getTargetSystemId(), stored.getData().getTargetSystemId());
        assertEquals(payload.getOrder(), stored.getData().getOrder());
        assertEquals(payload.getRecoveryStrategy(), stored.getData().getRecoveryStrategy());
        assertEquals(payload.getTransactionFlag(), stored.getData().getTransactionFlag());
        assertEquals(1, persistence.getAuditHistory().size());
        assertEquals(current.getChangeId(), persistence.getAuditHistory().get(0).getChangeId());
        assertEquals(current.getState(), persistence.getAuditHistory().get(0).getState());
        assertFalse(appender.appendEventFrom(payload).isError());
        assertTrue(storedEvents().stream().anyMatch(event -> payload.getStageId().equals(event.getStreamId())
                && event.getStreamSequence() == 2L));
        assertEquals(1, persistence.getAuditHistory().size());
    }

    @Test
    void publicSourceAppendPreservesStringMetadataAndItsStoredJsonRepresentation() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();
        String metadata = "{import-source=mongock}";
        AuditEntry payload = importedAuditEntry("string-metadata", LocalDateTime.of(2018, 1, 2, 3, 4), metadata);

        assertFalse(persistence.appendEventFrom(payload).isError());

        JournalEvent<AuditEntry> stored = storedEvents().get(0);
        assertEquals(metadata, stored.getData().getMetadata());
        assertEquals(payload.getStageId(), stored.getStreamId());
        assertEquals(1L, stored.getStreamSequence());
        JournalEventEntity entity = new DynamoDBUtil(client).getEnhancedClient()
                .table(journalTableName, TableSchema.fromBean(JournalEventEntity.class))
                .scan(ScanEnhancedRequest.builder().consistentRead(true).build()).items().iterator().next();
        JsonNode storedMetadata = JsonObjectMapper.DEFAULT_INSTANCE
                .readTree(entity.getPayload()).get("metadata");
        assertTrue(storedMetadata.isTextual(), "legacy string metadata remains a JSON string, not parsed as a map");
        assertEquals(metadata, storedMetadata.asText());
        assertTrue(persistence.getAuditHistory().isEmpty());
    }

    @Test
    void publicSourceConflictRollsBackAndRetryUsesDurableTailWithoutGap() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor();
        JournalHistoryAppender appender = persistence;
        AuditEntry payload = auditEntry("source", AuditEntry.Status.APPLIED);
        assertFalse(appender.appendEventFrom(payload).isError());
        conflictNextJournalContribution();

        assertThrows(DatabaseTransactionException.class, () -> appender.appendEventFrom(payload));
        assertEquals(2, storedEvents().size(), "only durable source and conflicting occupant remain");
        assertTrue(persistence.getAuditHistory().isEmpty());
        deleteStreamPosition(payload.getStageId(), 2L);
        assertFalse(appender.appendEventFrom(payload).isError());
        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(event -> event.getStreamSequence() == 1L));
        assertTrue(events.stream().anyMatch(event -> event.getStreamSequence() == 2L
                && payload.getChangeId().equals(event.getData().getChangeId())));
        assertTrue(persistence.getAuditHistory().isEmpty());
    }

    private void conflictNextJournalContribution() {
        // Insert after sequence allocation, before durable commit: works with cached or fresh sequencers.
        doAnswer(invocation -> {
            JournalEvent<AuditEntry> event = invocation.getArgument(1);
            occupyStreamPosition(event.getStreamId(), event.getStreamSequence());
            return invocation.callRealMethod();
        }).doCallRealMethod().when(journalEventStore).contributeToTransaction(any(), any());
    }

    private DynamoDBAuditPersistence persistenceFor() {
        DynamoDBAuditPersistence persistence = new DynamoDBAuditPersistence(
                new CommunityConfiguration(),
                new DynamoDBAuditRepository(client, auditTableName, 5L, 5L),
                journalEventStore,
                new JournalEventSequencerFactory(journalEventStore),
                STREAM_ID,
                txWrapper,
                true);
        persistence.initialize(io.flamingock.internal.util.id.RunnerId.generate());
        return persistence;
    }

    private void occupyStreamPosition(long sequence) {
        occupyStreamPosition(STREAM_ID, sequence);
    }

    private void occupyStreamPosition(String streamId, long sequence) {
        JournalEvent<AuditEntry> squatter = new JournalEvent<>(
                "pre-existing-event",
                "key-pre-existing-event",
                JournalEventType.CHANGE_STATE,
                JournalEvent.DEFAULT_VERSION,
                streamId,
                sequence,
                Instant.now(),
                auditEntry("pre-existing-change", AuditEntry.Status.APPLIED),
                false);
        new DynamoDBUtil(client)
                .getEnhancedClient()
                .table(journalTableName, TableSchema.fromBean(JournalEventEntity.class))
                .putItem(DynamoDBJournalEventMapper.toEntity(squatter));
    }

    private void deleteStreamPosition(long sequence) {
        deleteStreamPosition(STREAM_ID, sequence);
    }

    private void deleteStreamPosition(String streamId, long sequence) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("streamId", AttributeValue.builder().s(streamId).build());
        key.put("streamSequence", AttributeValue.builder().n(String.valueOf(sequence)).build());
        client.deleteItem(DeleteItemRequest.builder().tableName(journalTableName).key(key).build());
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        if (!tableExists(journalTableName)) {
            return new ArrayList<>();
        }
        return new DynamoDBUtil(client)
                .getEnhancedClient()
                .table(journalTableName, TableSchema.fromBean(JournalEventEntity.class))
                .scan(ScanEnhancedRequest.builder().consistentRead(true).build())
                .items()
                .stream()
                .map(DynamoDBJournalEventMapper::fromEntity)
                .collect(Collectors.toList());
    }

    private List<AuditEntryEntity> storedAuditRecords() {
        if (!tableExists(auditTableName)) {
            return new ArrayList<>();
        }
        return new DynamoDBUtil(client)
                .getEnhancedClient()
                .table(auditTableName, TableSchema.fromBean(AuditEntryEntity.class))
                .scan(ScanEnhancedRequest.builder().consistentRead(true).build())
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    private boolean tableExists(String tableName) {
        return client.listTables().tableNames().contains(tableName);
    }

    private void deleteTable(String tableName) {
        if (client != null && tableName != null && tableExists(tableName)) {
            client.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
        }
    }

    private static AuditEntry auditEntry(String changeId, AuditEntry.Status status) {
        return AuditEntryTestFactory.createTestAuditEntry(changeId, status, AuditTxType.NON_TX, (Class<?>) null);
    }

    private static AuditEntry legacyAuditEntry(String changeId, AuditEntry.Status status) {
        AuditEntry auditEntry = auditEntry(changeId, status);
        return new AuditEntry(
                auditEntry.getExecutionId(),
                auditEntry.getStageId(),
                auditEntry.getChangeId(),
                auditEntry.getAuthor(),
                auditEntry.getCreatedAt(),
                auditEntry.getState(),
                AuditEntry.ChangeType.MONGOCK_EXECUTION,
                auditEntry.getClassName(),
                auditEntry.getMethodName(),
                auditEntry.getSourceFile(),
                auditEntry.getExecutionMillis(),
                auditEntry.getExecutionHostname(),
                auditEntry.getMetadata(),
                auditEntry.getSystemChange(),
                auditEntry.getErrorTrace(),
                auditEntry.getTxType(),
                auditEntry.getTargetSystemId(),
                auditEntry.getOrder(),
                auditEntry.getRecoveryStrategy(),
                auditEntry.getTransactionFlag());
    }

    private static AuditEntry importedAuditEntry(String changeId, LocalDateTime createdAt) {
        return importedAuditEntry(changeId, createdAt, java.util.Collections.singletonMap("import-source", "mongock"));
    }

    private static AuditEntry importedAuditEntry(String changeId, LocalDateTime createdAt, Object metadata) {
        AuditEntry source = legacyAuditEntry(changeId, AuditEntry.Status.APPLIED);
        return new AuditEntry(
                source.getExecutionId(),
                source.getStageId(),
                source.getChangeId(),
                source.getAuthor(),
                createdAt,
                source.getState(),
                source.getType(),
                source.getClassName(),
                source.getMethodName(),
                source.getSourceFile(),
                source.getExecutionMillis(),
                source.getExecutionHostname(),
                metadata,
                source.getSystemChange(),
                source.getErrorTrace(),
                source.getTxType(),
                source.getTargetSystemId(),
                source.getOrder(),
                source.getRecoveryStrategy(),
                source.getTransactionFlag());
    }

    private static String tableName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
