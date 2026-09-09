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
package io.flamingock.store.couchbase.internal;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.common.couchbase.CouchbaseJournalEventMapper;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.couchbase.CouchbaseTxWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
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
 * Covers the {@code Features.JOURNAL_EVENTS} gate in {@link CouchbaseAuditPersistence}, and the transaction
 * boundary the journal exists for: the audit entry and its event must commit or roll back together.
 * <p>
 * Drives the persistence directly rather than a full runner, because forcing a failure between the two writes
 * is only practical at this level.
 */
@Testcontainers
class CouchbaseAuditPersistenceJournalTest {

    private static final String BUCKET_NAME = "test";
    private static final String SCOPE_NAME = CollectionIdentifier.DEFAULT_SCOPE;
    private static final String AUDIT_COLLECTION = "flamingockAuditPersistenceJournalTest";
    private static final String JOURNAL_COLLECTION = "flamingockJournalPersistenceJournalTest";
    private static final String STREAM_ID = "stage-under-test";

    @Container
    static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:7.2.4")
            .withBucket(new BucketDefinition(BUCKET_NAME));

    private static Cluster cluster;
    private static Bucket bucket;

    private final CouchbaseJournalEventMapper mapper = new CouchbaseJournalEventMapper();

    private CouchbaseAuditor auditor;
    private CouchbaseJournalEventStore journalEventStore;
    private CouchbaseTxWrapper txWrapper;

    @BeforeAll
    static void beforeAll() {
        couchbaseContainer.start();
        // Default KV timeout (2.5s) is too tight for a just-created collection: Couchbase's KV service can
        // take a few seconds to pick up a brand-new collection's manifest entry, and the first KV op against
        // it can hit that gap and time out with UnambiguousTimeoutException/KV_COLLECTION_OUTDATED. Widening
        // it gives the SDK's own retry loop room to ride out that window.
        cluster = Cluster.connect(
                couchbaseContainer.getConnectionString(),
                ClusterOptions.clusterOptions(couchbaseContainer.getUsername(), couchbaseContainer.getPassword())
                        .environment(env -> env.timeoutConfig(timeouts -> timeouts.kvTimeout(Duration.ofSeconds(10)))));
        bucket = cluster.bucket(BUCKET_NAME);
        bucket.waitUntilReady(Duration.ofSeconds(10));
    }

    @BeforeEach
    void setUp() {
        auditor = new CouchbaseAuditor(cluster, bucket);
        journalEventStore = new CouchbaseJournalEventStore(cluster, bucket);
        txWrapper = new CouchbaseTxWrapper(cluster, new TransactionManager<>(() -> {
            throw new UnsupportedOperationException(
                    "Supplier is unused: the wrapper registers the TransactionAttemptContext itself");
        }));
    }

    @AfterEach
    void tearDown() {
        // The flag is process-global and every test class in this module shares one JVM, so leaving it on
        // would silently make later classes create and write the journal collection.
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        CouchbaseCollectionHelper.dropCollectionIfExists(cluster, BUCKET_NAME, SCOPE_NAME, AUDIT_COLLECTION);
        CouchbaseCollectionHelper.dropCollectionIfExists(cluster, BUCKET_NAME, SCOPE_NAME, JOURNAL_COLLECTION);
    }

    @Test
    @DisplayName("journal disabled: the audit entry is written, and the journal collection is never created")
    void journalDisabledWritesNoEventAndCreatesNoCollection() {
        CouchbaseAuditPersistence persistence = persistenceFor(auditor);

        persistence.writeEntry(auditEntry("change-1"));

        assertEquals(1, auditor.getAuditHistory().size(), "the audit entry must still be written");
        assertFalse(CouchbaseCollectionHelper.collectionExists(cluster, BUCKET_NAME, SCOPE_NAME, JOURNAL_COLLECTION),
                "the journal collection must not exist when the feature is disabled");
    }

    @Test
    @DisplayName("journal enabled: the event is written alongside the audit entry, on the persistence's stream")
    void journalEnabledWritesEventWithAuditEntry() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        CouchbaseAuditPersistence persistence = persistenceFor(auditor);

        persistence.writeEntry(auditEntry("change-1"));

        assertEquals(1, auditor.getAuditHistory().size());
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
    @DisplayName("journal enabled: successive states collapse to one audit document, the history staying in the journal")
    void journalEnabledKeepsOneRecordPerChange() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        CouchbaseAuditPersistence persistence = persistenceFor(auditor);

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        List<AuditEntry> auditRecords = auditor.getAuditHistory();
        assertEquals(1, auditRecords.size(), "the audit document is the change's current state, not a ledger");
        assertEquals(AuditEntry.Status.APPLIED, auditRecords.get(0).getState(), "and it holds the latest state");

        assertEquals(2, storedEvents().size(), "while every transition is kept as an event");
    }

    @Test
    @DisplayName("journal disabled: successive states accumulate as separate audit documents")
    void journalDisabledKeepsRecordPerStateTransition() {
        CouchbaseAuditPersistence persistence = persistenceFor(auditor);

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        assertEquals(2, auditor.getAuditHistory().size(),
                "without the journal the audit collection is itself the history");
    }

    @Test
    @DisplayName("journal enabled: a failing journal append rolls the audit entry back with it")
    void journalFailureRollsBackAuditEntry() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        // Built while the stream is empty, so its sequencer is seeded at 1. Seeding the collision first would
        // instead make forStream() seed at 2 and no conflict would happen.
        CouchbaseAuditPersistence persistence = persistenceFor(auditor);
        occupyStreamPosition(1L);

        assertThrows(RuntimeException.class, () -> persistence.writeEntry(auditEntry("change-1")));

        assertTrue(auditor.getAuditHistory().isEmpty(),
                "the audit entry must not survive a failed journal append");
        assertEquals(1, storedEvents().size(), "only the pre-existing event remains");
    }

    @Test
    @DisplayName("journal enabled: a failing audit write rolls the journal event back with it")
    void auditFailureRollsBackJournalEvent() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        // The audit write is a get-then-replace/insert on a single key, so it cannot be made to fail with a
        // collision the way the journal can — the failure has to be injected.
        CouchbaseAuditor failingAuditor = mock(CouchbaseAuditor.class);
        doThrow(new IllegalStateException("audit write failed"))
                .when(failingAuditor).contributeToTransaction(any(TransactionAttemptContext.class), any(AuditEntry.class));
        CouchbaseAuditPersistence persistence = persistenceFor(failingAuditor);

        assertThrows(RuntimeException.class, () -> persistence.writeEntry(auditEntry("change-1")));

        assertTrue(storedEvents().isEmpty(), "the journal event must not survive a failed audit write");
    }

    @Test
    @DisplayName("journal enabled: a failed write leaves no gap — its stream position is handed out again")
    void failedWriteLeavesNoGapInTheStream() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        journalEventStore.initialize(true, SCOPE_NAME, JOURNAL_COLLECTION);
        JournalEventSequencer sequencer = new JournalEventSequencerFactory(journalEventStore).forStream(STREAM_ID);

        CouchbaseAuditor failingAuditor = mock(CouchbaseAuditor.class);
        doThrow(new IllegalStateException("audit write failed"))
                .when(failingAuditor).contributeToTransaction(any(TransactionAttemptContext.class), any(AuditEntry.class));
        CouchbaseAuditPersistence failing = persistenceFor(failingAuditor, sequencer);
        assertThrows(RuntimeException.class, () -> failing.writeEntry(auditEntry("change-1")));

        // Same sequencer: the aborted attempt must not have spent position 1.
        persistenceFor(auditor, sequencer).writeEntry(auditEntry("change-1"));

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(1, events.size());
        assertEquals(1L, events.get(0).getStreamSequence(),
                "the stream must stay contiguous, so consumers can tell in-flight from lost");
    }

    // ----------------------------- helpers -----------------------------

    private CouchbaseAuditPersistence persistenceFor(CouchbaseAuditor auditor) {
        return persistenceFor(auditor, new JournalEventSequencerFactory(journalEventStore).forStream(STREAM_ID));
    }

    private CouchbaseAuditPersistence persistenceFor(CouchbaseAuditor auditor, JournalEventSequencer sequencer) {
        CouchbaseAuditPersistence persistence = new CouchbaseAuditPersistence(
                new CommunityConfiguration(), auditor, journalEventStore, sequencer, txWrapper,
                SCOPE_NAME, AUDIT_COLLECTION, JOURNAL_COLLECTION, true);
        persistence.initialize(RunnerId.generate());
        return persistence;
    }

    /**
     * Takes a stream position directly, so the next append by a sequencer already seeded below it collides on
     * the {@code journal::<streamId>::<sequence>} document key.
     */
    private void occupyStreamPosition(long streamSequence) {
        journalEventStore.initialize(true, SCOPE_NAME, JOURNAL_COLLECTION);
        JournalEvent<AuditEntry> squatter = new JournalEvent<>(
                "pre-existing-event", JournalEventType.CHANGE_STATE, JournalEvent.DEFAULT_VERSION,
                STREAM_ID, streamSequence, Instant.now(), auditEntry("pre-existing-change"), false);
        bucket.scope(SCOPE_NAME).collection(JOURNAL_COLLECTION)
                .insert("journal::" + STREAM_ID + "::" + streamSequence, mapper.toDocument(squatter));
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        if (!CouchbaseCollectionHelper.collectionExists(cluster, BUCKET_NAME, SCOPE_NAME, JOURNAL_COLLECTION)) {
            return new ArrayList<>();
        }
        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        CouchbaseCollectionHelper.selectAllDocuments(cluster, BUCKET_NAME, SCOPE_NAME, JOURNAL_COLLECTION)
                .forEach(document -> events.add(mapper.fromDocument(document)));
        return events;
    }

    private static AuditEntry auditEntry(String changeId) {
        return auditEntry(changeId, AuditEntry.Status.APPLIED);
    }

    private static AuditEntry auditEntry(String changeId, AuditEntry.Status status) {
        return AuditEntryTestFactory.createTestAuditEntry(
                changeId, status, AuditTxType.NON_TX, (Class<?>) null);
    }
}
