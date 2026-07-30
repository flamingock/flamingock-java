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
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTxWrapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Covers the {@code Features.JOURNAL_EVENTS} gate in {@link MongoDBSyncAuditPersistence}, and the transaction
 * boundary the journal exists for: the audit entry and its event must commit or roll back together.
 * <p>
 * Drives the persistence directly rather than a full runner, because forcing a failure between the two writes
 * is only practical at this level.
 */
@Testcontainers
class MongoDBSyncAuditPersistenceJournalTest {

    private static final String DB_NAME = "test";
    private static final String AUDIT_COLLECTION = "flamingockAuditLog";
    private static final String JOURNAL_COLLECTION = "flamingockJournalEvents";
    private static final String STREAM_ID = "stage-under-test";

    @Container
    public static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:6")).withReuse(true);

    private final MongoDBJournalEventMapper mapper = new MongoDBJournalEventMapper();

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoDBSyncAuditRepository auditRepository;
    private MongoDBSyncJournalEventStore journalEventStore;
    private MongoDBSyncTxWrapper txWrapper;

    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase(DB_NAME);
        auditRepository = new MongoDBSyncAuditRepository(
                database, AUDIT_COLLECTION,
                ReadConcern.MAJORITY, ReadPreference.primary(), WriteConcern.MAJORITY.withJournal(true));
        journalEventStore = new MongoDBSyncJournalEventStore(
                database, JOURNAL_COLLECTION,
                ReadConcern.MAJORITY, ReadPreference.primary(), WriteConcern.MAJORITY.withJournal(true));
        txWrapper = new MongoDBSyncTxWrapper(new TransactionManager<>(mongoClient::startSession));
    }

    @AfterEach
    void tearDown() {
        // The flag is process-global and every test class in this module shares one JVM, so leaving it on
        // would silently make later classes create and write the journal collection.
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        database.drop();
        mongoClient.close();
    }

    @Test
    @DisplayName("journal disabled: the audit entry is written, and the journal collection is never created")
    void journalDisabledWritesNoEventAndCreatesNoCollection() {
        MongoDBSyncAuditPersistence persistence = persistenceFor(auditRepository);

        persistence.writeEntry(auditEntry("change-1"));

        assertEquals(1, auditRepository.getAuditHistory().size(), "the audit entry must still be written");
        assertFalse(collectionExists(JOURNAL_COLLECTION),
                "the journal collection must not exist when the feature is disabled");
    }

    @Test
    @DisplayName("journal enabled: the event is written alongside the audit entry, on the persistence's stream")
    void journalEnabledWritesEventWithAuditEntry() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistenceFor(auditRepository);

        persistence.writeEntry(auditEntry("change-1"));

        assertEquals(1, auditRepository.getAuditHistory().size());
        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(1, events.size(), "exactly one event per audit write");

        JournalEvent<AuditEntry> event = events.get(0);
        assertEquals(STREAM_ID, event.getStreamId());
        assertEquals(1L, event.getStreamSequence(), "the first event on a fresh stream is sequence 1");
        assertEquals(JournalEventType.CHANGE_STATE, event.getEventType());
        assertFalse(event.isAcknowledged(), "a freshly appended event is pending synchronization");
        assertEquals("change-1", event.getData().getChangeId(), "the audit entry travels as the event payload");
    }

    @Test
    @DisplayName("journal enabled: successive states collapse to one audit record, the history staying in the journal")
    void journalEnabledKeepsOneRecordPerChange() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistenceFor(auditRepository);

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        List<AuditEntry> auditRecords = auditRepository.getAuditHistory();
        assertEquals(1, auditRecords.size(), "the audit record is the change's current state, not a ledger");
        assertEquals(AuditEntry.Status.APPLIED, auditRecords.get(0).getState(), "and it holds the latest state");

        assertEquals(2, storedEvents().size(), "while every transition is kept as an event");
    }

    @Test
    @DisplayName("journal disabled: successive states accumulate as separate audit records")
    void journalDisabledKeepsRecordPerStateTransition() {
        MongoDBSyncAuditPersistence persistence = persistenceFor(auditRepository);

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        assertEquals(2, auditRepository.getAuditHistory().size(),
                "without the journal the audit collection is itself the history");
    }

    @Test
    @DisplayName("journal enabled: a failing journal append rolls the audit entry back with it")
    void journalFailureRollsBackAuditEntry() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        // Built while the stream is empty, so its sequencer is seeded at 1. Seeding the collision first would
        // instead make forStream() seed at 2 and no conflict would happen.
        MongoDBSyncAuditPersistence persistence = persistenceFor(auditRepository);
        occupyStreamPosition(1L);

        assertThrows(DatabaseTransactionException.class, () -> persistence.writeEntry(auditEntry("change-1")));

        assertTrue(auditRepository.getAuditHistory().isEmpty(),
                "the audit entry must not survive a failed journal append");
        assertEquals(1, storedEvents().size(), "only the pre-existing event remains");
    }

    @Test
    @DisplayName("journal enabled: a failing audit write rolls the journal event back with it")
    void auditFailureRollsBackJournalEvent() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        // The audit write is an upsert on its only unique index, so it cannot be made to fail with a duplicate
        // key the way the journal can — the failure has to be injected.
        MongoDBSyncAuditRepository failingRepository = mock(MongoDBSyncAuditRepository.class);
        doThrow(new IllegalStateException("audit write failed"))
                .when(failingRepository).save(any(ClientSession.class), any(AuditEntry.class));
        MongoDBSyncAuditPersistence persistence = persistenceFor(failingRepository);

        assertThrows(DatabaseTransactionException.class, () -> persistence.writeEntry(auditEntry("change-1")));

        assertTrue(storedEvents().isEmpty(), "the journal event must not survive a failed audit write");
    }

    // ----------------------------- helpers -----------------------------

    /**
     * Builds a persistence over {@code STREAM_ID}. The sequencer has to come from the factory because
     * {@link JournalEventSequencer}'s constructor is package-private in another package.
     * <p>
     * Call only after deciding the flag state: {@code initialize} is where the journal collection setup is
     * gated.
     */
    private MongoDBSyncAuditPersistence persistenceFor(MongoDBSyncAuditRepository repository) {
        JournalEventSequencer sequencer = new JournalEventSequencerFactory(journalEventStore).forStream(STREAM_ID);
        MongoDBSyncAuditPersistence persistence = new MongoDBSyncAuditPersistence(
                new CommunityConfiguration(), repository, journalEventStore, sequencer, txWrapper, true);
        persistence.initialize(RunnerId.generate());
        return persistence;
    }

    /**
     * Takes a stream position directly, so the next append by a sequencer already seeded below it collides on
     * the unique {@code (streamId, streamSequence)} index.
     */
    private void occupyStreamPosition(long streamSequence) {
        JournalEvent<AuditEntry> squatter = new JournalEvent<>(
                "pre-existing-event", JournalEventType.CHANGE_STATE, JournalEvent.DEFAULT_VERSION,
                STREAM_ID, streamSequence, Instant.now(), auditEntry("pre-existing-change"), false);
        database.getCollection(JOURNAL_COLLECTION).insertOne(mapper.toDocument(squatter));
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        if (!collectionExists(JOURNAL_COLLECTION)) {
            return new ArrayList<>();
        }
        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        database.getCollection(JOURNAL_COLLECTION).find().forEach(document -> events.add(mapper.fromDocument(document)));
        return events;
    }

    private boolean collectionExists(String collectionName) {
        return database.listCollectionNames().into(new ArrayList<>()).contains(collectionName);
    }

    private static AuditEntry auditEntry(String changeId) {
        return auditEntry(changeId, AuditEntry.Status.APPLIED);
    }

    private static AuditEntry auditEntry(String changeId, AuditEntry.Status status) {
        return AuditEntryTestFactory.createTestAuditEntry(
                changeId, status, AuditTxType.NON_TX, (Class<?>) null);
    }
}
