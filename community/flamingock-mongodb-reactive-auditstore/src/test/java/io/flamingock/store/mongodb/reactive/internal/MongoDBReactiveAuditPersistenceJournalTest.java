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
import com.mongodb.reactivestreams.client.MongoDatabase;
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
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.reactive.util.PublisherSync;
import io.flamingock.targetsystem.mongodb.reactive.MongoDBReactiveTxWrapper;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Verifies the feature gate and the atomic native {@code ClientSession} boundary between audit state and
 * journal events.
 */
@Testcontainers
class MongoDBReactiveAuditPersistenceJournalTest {

    private static final String DB_NAME = "test";
    private static final String AUDIT_COLLECTION = "flamingockAuditLog";
    private static final String JOURNAL_COLLECTION = "flamingockJournalEvents";
    private static final String STREAM_ID = "stage-under-test";

    @Container
    static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:6")).withReuse(true);

    private final io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper mapper =
            new io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper();

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoDBReactiveAuditRepository auditRepository;
    private MongoDBReactiveJournalEventStore journalEventStore;
    private MongoDBReactiveTxWrapper txWrapper;

    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase(DB_NAME);
        auditRepository = new MongoDBReactiveAuditRepository(
                database, AUDIT_COLLECTION,
                ReadConcern.MAJORITY, ReadPreference.primary(), WriteConcern.MAJORITY.withJournal(true));
        journalEventStore = new MongoDBReactiveJournalEventStore(
                database, JOURNAL_COLLECTION,
                ReadConcern.MAJORITY, ReadPreference.primary(), WriteConcern.MAJORITY.withJournal(true));
        txWrapper = new MongoDBReactiveTxWrapper(
                new TransactionManager<>(() -> PublisherSync.first(mongoClient.startSession())));
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        PublisherSync.complete(database.drop());
        mongoClient.close();
    }

    @Test
    @DisplayName("journal disabled preserves append records and creates no journal collection")
    void journalDisabledPreservesHistoricalAuditPath() {
        MongoDBReactiveAuditPersistence persistence = persistenceFor(auditRepository);

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        assertEquals(2, auditRepository.getAuditHistory().size());
        assertFalse(collectionExists(JOURNAL_COLLECTION));
    }

    @Test
    @DisplayName("journal enabled writes a compatible event with the current audit record")
    void journalEnabledWritesEventWithAuditEntry() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBReactiveAuditPersistence persistence = persistenceFor(auditRepository);
        AuditEntry entry = auditEntry("change-1", AuditEntry.Status.APPLIED);

        persistence.writeEntry(entry);

        assertEquals(1, auditRepository.getAuditHistory().size());
        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(1, events.size());
        assertEquals(STREAM_ID, events.get(0).getStreamId());
        assertEquals(1L, events.get(0).getStreamSequence());
        assertEquals(JournalEventType.CHANGE_STATE, events.get(0).getEventType());
        assertFalse(events.get(0).isAcknowledged());
        assertEquals(entry.getChangeId(), events.get(0).getData().getChangeId());
    }

    @Test
    @DisplayName("journal enabled keeps only the final audit state and retains every event")
    void journalEnabledSeparatesCurrentStateFromHistory() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBReactiveAuditPersistence persistence = persistenceFor(auditRepository);

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        List<AuditEntry> auditRecords = auditRepository.getAuditHistory();
        assertEquals(1, auditRecords.size());
        assertEquals(AuditEntry.Status.APPLIED, auditRecords.get(0).getState());
        assertEquals(2, storedEvents().size());
    }

    @Test
    @DisplayName("a journal append failure rolls back the audit record")
    void journalFailureRollsBackAuditEntry() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBReactiveAuditPersistence persistence = persistenceFor(auditRepository);
        occupyStreamPosition(1L);

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED)));

        assertTrue(auditRepository.getAuditHistory().isEmpty());
        assertEquals(1, storedEvents().size());
    }

    @Test
    @DisplayName("an audit write failure rolls back the journal event")
    void auditFailureRollsBackJournalEvent() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBReactiveAuditRepository failingRepository = mock(MongoDBReactiveAuditRepository.class);
        doThrow(new IllegalStateException("audit write failed"))
                .when(failingRepository).save(ArgumentMatchers.any(ClientSession.class), ArgumentMatchers.any(AuditEntry.class));
        MongoDBReactiveAuditPersistence persistence = persistenceFor(failingRepository);

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED)));

        assertTrue(storedEvents().isEmpty());
    }

    @Test
    @DisplayName("a failed write does not consume its stream position")
    void failedWriteLeavesNoGap() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        JournalEventSequencer sequencer = new JournalEventSequencerFactory(journalEventStore).forStream(STREAM_ID);
        MongoDBReactiveAuditRepository failingRepository = mock(MongoDBReactiveAuditRepository.class);
        doThrow(new IllegalStateException("audit write failed"))
                .when(failingRepository).save(ArgumentMatchers.any(ClientSession.class), ArgumentMatchers.any(AuditEntry.class));

        assertThrows(DatabaseTransactionException.class,
                () -> persistenceFor(failingRepository, sequencer)
                        .writeEntry(auditEntry("failed-change", AuditEntry.Status.APPLIED)));

        persistenceFor(auditRepository, sequencer)
                .writeEntry(auditEntry("successful-change", AuditEntry.Status.APPLIED));

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(1, events.size());
        assertEquals(1L, events.get(0).getStreamSequence());
        assertEquals("successful-change", events.get(0).getData().getChangeId());
    }

    private MongoDBReactiveAuditPersistence persistenceFor(MongoDBReactiveAuditRepository repository) {
        return persistenceFor(repository, new JournalEventSequencerFactory(journalEventStore).forStream(STREAM_ID));
    }

    private MongoDBReactiveAuditPersistence persistenceFor(MongoDBReactiveAuditRepository repository,
                                                           JournalEventSequencer sequencer) {
        MongoDBReactiveAuditPersistence persistence = new MongoDBReactiveAuditPersistence(
                new CommunityConfiguration(), repository, journalEventStore, sequencer, txWrapper, true);
        persistence.initialize(RunnerId.generate());
        return persistence;
    }

    private void occupyStreamPosition(long streamSequence) {
        JournalEvent<AuditEntry> event = new JournalEvent<>(
                "pre-existing-event", JournalEventType.CHANGE_STATE, JournalEvent.DEFAULT_VERSION,
                STREAM_ID, streamSequence, Instant.now(), auditEntry("pre-existing-change", AuditEntry.Status.APPLIED), false);
        PublisherSync.first(database.getCollection(JOURNAL_COLLECTION).insertOne(mapper.toDocument(event)));
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        if (!collectionExists(JOURNAL_COLLECTION)) {
            return new ArrayList<>();
        }
        return PublisherSync.collect(database.getCollection(JOURNAL_COLLECTION).find())
                .stream()
                .map(mapper::fromDocument)
                .collect(Collectors.toList());
    }

    private boolean collectionExists(String collectionName) {
        return PublisherSync.collect(database.listCollectionNames()).contains(collectionName);
    }

    private static AuditEntry auditEntry(String changeId, AuditEntry.Status status) {
        return AuditEntryTestFactory.createTestAuditEntry(changeId, status, AuditTxType.NON_TX, (Class<?>) null);
    }
}
