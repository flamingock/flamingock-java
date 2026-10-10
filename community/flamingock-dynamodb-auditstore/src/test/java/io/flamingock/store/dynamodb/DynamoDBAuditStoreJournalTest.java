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
package io.flamingock.store.dynamodb;

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.store.AuditHistoryAppender;
import io.flamingock.internal.core.external.store.JournalHistoryAppender;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.dynamodb.entities.journal.DynamoDBJournalEventMapper;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventEntity;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class DynamoDBAuditStoreJournalTest {

    private static final String STAGE_ONE = "stage-one";
    private static final String STAGE_TWO = "stage-two";

    @Container
    static final GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDbClient client;
    private String auditTableName;
    private String lockTableName;
    private String journalTableName;

    @BeforeEach
    void setUp() {
        client = DynamoDBTestContainer.createClient(dynamoDBContainer);
        auditTableName = tableName("storeAudit");
        lockTableName = tableName("storeLock");
        journalTableName = tableName("storeJournal");
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        if (client != null) {
            client.listTables().tableNames().forEach(tableName -> client.deleteTable(
                    software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest.builder()
                            .tableName(tableName)
                            .build()));
            client.close();
        }
    }

    @Test
    @DisplayName("journal-enabled stores create independent persistence streams for each stage")
    void journalEnabledStoreCreatesPerStagePersistenceStreams() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SimpleContext context = newContext();
        DynamoDBAuditStore auditStore = initializeStore(context);

        CommunityAuditPersistence stageOnePersistence = auditStore.getPersistenceFactory().get(STAGE_ONE);
        CommunityAuditPersistence stageTwoPersistence = auditStore.getPersistenceFactory().get(STAGE_TWO);
        stageOnePersistence.writeEntry(auditEntry("change-one"));
        stageTwoPersistence.writeEntry(auditEntry("change-two"));

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(event -> STAGE_ONE.equals(event.getStreamId())
                && event.getStreamSequence() == 1L
                && "change-one".equals(event.getData().getChangeId())));
        assertTrue(events.stream().anyMatch(event -> STAGE_TWO.equals(event.getStreamId())
                && event.getStreamSequence() == 1L
                && "change-two".equals(event.getData().getChangeId())));
    }

    @Test
    @DisplayName("journal-enabled store reseeds each requested stage independently")
    void journalEnabledStoreReseedsEachRequestedStageIndependently() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SimpleContext context = newContext();
        DynamoDBAuditStore auditStore = initializeStore(context);

        auditStore.getPersistenceFactory().get(STAGE_ONE).writeEntry(auditEntry("stage-one-first"));
        auditStore.getPersistenceFactory().get(STAGE_TWO).writeEntry(auditEntry("stage-two-first"));
        auditStore.getPersistenceFactory().get(STAGE_ONE).writeEntry(auditEntry("stage-one-second"));

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(3, events.size());
        assertTrue(events.stream().anyMatch(event -> STAGE_ONE.equals(event.getStreamId())
                && event.getStreamSequence() == 1L
                && "stage-one-first".equals(event.getData().getChangeId())));
        assertTrue(events.stream().anyMatch(event -> STAGE_ONE.equals(event.getStreamId())
                && event.getStreamSequence() == 2L
                && "stage-one-second".equals(event.getData().getChangeId())));
        assertTrue(events.stream().anyMatch(event -> STAGE_TWO.equals(event.getStreamId())
                && event.getStreamSequence() == 1L
                && "stage-two-first".equals(event.getData().getChangeId())));
    }

    @Test
    @DisplayName("journal-enabled autoCreate completes an audit-only installation with a journal table")
    void journalEnabledAutoCreatesJournalForAuditOnlyInstallation() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SimpleContext context = newContext();
        DynamoDBAuditStore auditStore = initializeStore(context);

        assertTrue(auditStore.getAuditReader().getAuditHistory().isEmpty());
        assertTrue(client.listTables().tableNames().contains(auditTableName));

        auditStore.getPersistenceFactory().get(STAGE_ONE).writeEntry(auditEntry("audit-only-change"));
        auditStore.getPersistenceFactory().get(STAGE_TWO);

        assertTrue(client.listTables().tableNames().contains(journalTableName));
        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(1, events.size());
        assertEquals("audit-only-change", events.get(0).getData().getChangeId());
    }

    @Test
    @DisplayName("non-positive capacities are rejected during DynamoDB setup")
    void nonPositiveCapacitiesFailDynamoDbSetup() {
        SimpleContext context = newContext();
        DynamoDBAuditStore invalidReadCapacity = DynamoDBAuditStore.from(initializedTargetSystem(context))
                .withAuditRepositoryName(auditTableName)
                .withLockRepositoryName(lockTableName)
                .withJournalRepositoryName(journalTableName)
                .withReadCapacityUnits(0L);
        DynamoDBAuditStore invalidWriteCapacity = DynamoDBAuditStore.from(initializedTargetSystem(context))
                .withAuditRepositoryName(auditTableName)
                .withLockRepositoryName(lockTableName)
                .withJournalRepositoryName(journalTableName)
                .withWriteCapacityUnits(-1L);

        assertThrows(DynamoDbException.class, () -> invalidReadCapacity.initialize(context));
        assertThrows(DynamoDbException.class, () -> invalidWriteCapacity.initialize(context));
        assertTrue(client.listTables().tableNames().isEmpty());
    }

    @Test
    @DisplayName("manual setup validates the audit table before the journal table")
    void manualSetupValidatesAuditTableBeforeJournalSetup() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SimpleContext context = newContext();
        DynamoDBAuditStore auditStore = DynamoDBAuditStore.from(initializedTargetSystem(context))
                .withAuditRepositoryName(auditTableName)
                .withLockRepositoryName(lockTableName)
                .withJournalRepositoryName(journalTableName)
                .withAutoCreate(false);
        auditStore.initialize(context);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> auditStore.getPersistenceFactory().get(STAGE_ONE));

        assertTrue(exception.getMessage().contains("audit table"));
        assertFalse(client.listTables().tableNames().contains(journalTableName));
    }

    @Test
    @DisplayName("journal-enabled persistence remains stage-aware while the reader stays available")
    void journalEnabledPersistenceRemainsStageAwareAndKeepsReaderAvailable() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SimpleContext context = newContext();
        DynamoDBAuditStore auditStore = initializeStore(context);

        CommunityAuditPersistence persistence = auditStore.getPersistenceFactory().get(STAGE_ONE);
        persistence.writeEntry(auditEntry("deprecated-direct-write"));

        assertEquals(1, persistence.getAuditHistory().size());
        assertEquals(1, auditStore.getAuditReader().getAuditHistory().size(),
                "journal-enabled history reads must use the independent audit reader");
        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(1, events.size());
        assertEquals(STAGE_ONE, events.get(0).getStreamId());
        assertEquals(1L, events.get(0).getStreamSequence());
    }

    @Test
    @DisplayName("journal repository cannot reuse the audit or lock table name")
    void journalRepositoryNameMustBeUnique() {
        SimpleContext context = newContext();
        DynamoDBTargetSystem targetSystem = initializedTargetSystem(context);
        DynamoDBAuditStore auditStore = DynamoDBAuditStore.from(targetSystem)
                .withAuditRepositoryName(auditTableName)
                .withLockRepositoryName(lockTableName)
                .withJournalRepositoryName(auditTableName);

        assertThrows(FlamingockException.class, () -> auditStore.initialize(context));
    }

    @Test
    void sameStagePersistencesRouteNormalAndSourceWritesToTheirOwnDurableStreams() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditStore store = initializeStore(newContext());
        CommunityAuditPersistence first = store.getPersistenceFactory().get(STAGE_ONE);
        CommunityAuditPersistence second = store.getPersistenceFactory().get(STAGE_ONE);
        org.junit.jupiter.api.Assertions.assertNotSame(first, second);
        AuditEntry started = AuditEntryTestFactory.createTestAuditEntry(
                "normal", AuditEntry.Status.STARTED, AuditTxType.NON_TX, (Class<?>) null);
        AuditEntry applied = auditEntry("normal");
        AuditEntry source = auditEntry("historical-source");

        assertFalse(first.writeEntry(started).isError());
        JournalHistoryAppender appender = second;
        assertFalse(appender.appendEventFrom(source).isError());
        List<AuditEntry> afterJournalOnly = store.getAuditReader().getAuditHistory();
        assertEquals(1, afterJournalOnly.size());
        assertEquals(started.getChangeId(), afterJournalOnly.get(0).getChangeId());
        assertEquals(started.getState(), afterJournalOnly.get(0).getState());
        assertEquals(started.getExecutionId(), afterJournalOnly.get(0).getExecutionId());
        assertEquals(started.getCreatedAt(), afterJournalOnly.get(0).getCreatedAt());
        assertFalse(first.writeEntry(applied).isError());

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(3, events.size());
        List<JournalEvent<AuditEntry>> normalEvents = events.stream()
                .filter(event -> STAGE_ONE.equals(event.getStreamId()))
                .sorted(java.util.Comparator.comparingLong(JournalEvent::getStreamSequence))
                .collect(Collectors.toList());
        assertEquals(2, normalEvents.size());
        assertEquals(1L, normalEvents.get(0).getStreamSequence());
        assertEquals(2L, normalEvents.get(1).getStreamSequence());
        assertEquals(started.getState(), normalEvents.get(0).getData().getState());
        assertEquals(applied.getChangeId(), normalEvents.get(1).getData().getChangeId());
        assertEquals(applied.getState(), normalEvents.get(1).getData().getState());
        JournalEvent<AuditEntry> sourceEvent = events.stream()
                .filter(event -> source.getStageId().equals(event.getStreamId())).findFirst().get();
        assertEquals(1L, sourceEvent.getStreamSequence());
        assertEquals(source.getChangeId(), sourceEvent.getData().getChangeId());
        assertEquals(source.getStageId(), sourceEvent.getData().getStageId());
        assertFalse(STAGE_ONE.equals(source.getStageId()));
        List<AuditEntry> audit = store.getAuditReader().getAuditHistory();
        assertEquals(1, audit.size());
        assertEquals(applied.getState(), audit.get(0).getState());
    }

    @Test
    void journalOffAllowsNormalAndHistoricalAuditWithoutJournalTable() {
        DynamoDBAuditStore store = initializeStore(newContext());
        CommunityAuditPersistence persistence = store.getPersistenceFactory().get(STAGE_ONE);
        persistence.writeEntry(auditEntry("normal"));
        assertFalse(((AuditHistoryAppender) persistence).append(auditEntry("historical")).isError());
        JournalHistoryAppender journal = persistence;
        assertThrows(IllegalStateException.class, () -> journal.appendEventFrom(auditEntry("disabled")));
        assertEquals(2, persistence.getAuditHistory().size());
        assertFalse(client.listTables().tableNames().contains(journalTableName));
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        if (!client.listTables().tableNames().contains(journalTableName)) {
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

    private DynamoDBAuditStore initializeStore(SimpleContext context) {
        DynamoDBAuditStore auditStore = DynamoDBAuditStore.from(initializedTargetSystem(context))
                .withAuditRepositoryName(auditTableName)
                .withLockRepositoryName(lockTableName)
                .withJournalRepositoryName(journalTableName);
        auditStore.initialize(context);
        return auditStore;
    }

    private DynamoDBTargetSystem initializedTargetSystem(SimpleContext context) {
        DynamoDBTargetSystem targetSystem = new DynamoDBTargetSystem("dynamodb", client);
        targetSystem.initialize(context);
        return targetSystem;
    }

    private SimpleContext newContext() {
        SimpleContext context = new SimpleContext();
        context.addDependency(RunnerId.generate());
        context.addDependency(new CommunityConfiguration());
        return context;
    }

    private static AuditEntry auditEntry(String changeId) {
        return AuditEntryTestFactory.createTestAuditEntry(
                changeId,
                AuditEntry.Status.APPLIED,
                AuditTxType.NON_TX,
                (Class<?>) null);
    }

    private static String tableName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
