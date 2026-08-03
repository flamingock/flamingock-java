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

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.FeatureFlag;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        journalEventStore = new DynamoDBJournalEventStore(client, journalTableName, 5L, 5L);
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
        DynamoDBAuditPersistence persistence = persistenceFor(newSequencer());
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
        DynamoDBAuditPersistence persistence = persistenceFor(newSequencer());
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
    @DisplayName("journal enabled collapses successive states to one current audit record while retaining both events")
    void journalEnabledKeepsCurrentStateAuditRecordAndJournalHistory() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistenceFor(newSequencer());

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
        DynamoDBAuditPersistence persistence = persistenceFor(newSequencer());

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
        DynamoDBAuditPersistence persistence = persistenceFor(newSequencer());
        occupyStreamPosition(1L);

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED)));

        assertTrue(persistence.getAuditHistory().isEmpty(), "the audit put must roll back with the canceled transaction");
        assertEquals(1, storedEvents().size(), "only the pre-existing stream-position occupant may remain");
    }

    @Test
    @DisplayName("a canceled transaction does not confirm the sequence and the next retry reuses the position")
    void canceledTransactionLeavesNoGapForRetry() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        JournalEventSequencer sequencer = newSequencer();
        DynamoDBAuditPersistence persistence = persistenceFor(sequencer);
        occupyStreamPosition(1L);

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

    private DynamoDBAuditPersistence persistenceFor(JournalEventSequencer sequencer) {
        DynamoDBAuditPersistence persistence = new DynamoDBAuditPersistence(
                client,
                txWrapper,
                journalEventStore,
                sequencer,
                auditTableName,
                5L,
                5L,
                true,
                new CommunityConfiguration());
        persistence.initialize(io.flamingock.internal.util.id.RunnerId.generate());
        return persistence;
    }

    private JournalEventSequencer newSequencer() {
        return new JournalEventSequencerFactory(journalEventStore).forStream(STREAM_ID);
    }

    private void occupyStreamPosition(long sequence) {
        JournalEvent<AuditEntry> squatter = new JournalEvent<>(
                "pre-existing-event",
                JournalEventType.CHANGE_STATE,
                JournalEvent.DEFAULT_VERSION,
                STREAM_ID,
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
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("streamId", AttributeValue.builder().s(STREAM_ID).build());
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

    private static String tableName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
