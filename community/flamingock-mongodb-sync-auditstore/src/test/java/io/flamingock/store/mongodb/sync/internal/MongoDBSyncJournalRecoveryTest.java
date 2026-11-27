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

import com.mongodb.MongoWriteException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.WriteError;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.DuplicateStreamSequenceException;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.common.mongodb.MongoDBJournalEventMapper;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTxWrapper;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MongoDBSyncJournalRecoveryTest {
    @AfterEach
    void resetJournalFlag() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sequenceIndexCollisionIsNormalizedAtTheJournalInsert(boolean transactional) {
        MongoCollection<Document> collection = journalCollection();
        MongoDBSyncJournalEventStore store = journalStore("journal", collection);
        MongoWriteException cause = driverCollision("unique_key_sequence", "{}");
        ClientSession session = mock(ClientSession.class);
        if (transactional) {
            doThrow(cause).when(collection).insertOne(eq(session), any(Document.class));
        } else {
            doThrow(cause).when(collection).insertOne(any(Document.class));
        }
        JournalEvent<AuditEntry> event = eventAt(42L);

        DuplicateStreamSequenceException error = assertThrows(DuplicateStreamSequenceException.class,
                () -> write(store, session, event, transactional));
        assertEquals("stream", error.getStreamId());
        assertEquals(42L, error.getStreamSequence());
        assertSame(cause, error.getCause());
    }

    @Test
    void eventIdCollisionIsNotMisidentifiedFromContentOfDuplicateKey() {
        MongoCollection<Document> collection = journalCollection();
        MongoDBSyncJournalEventStore store = journalStore("journal", collection);
        MongoWriteException failure = driverCollision("unique_event_id",
                "{eventId: \"index: unique_key_sequence dup key: {}\"}");
        doThrow(failure).when(collection).insertOne(any(Document.class));
        assertSame(failure, assertThrows(MongoWriteException.class, () -> store.write(eventAt(42L))));
    }

    @ParameterizedTest
    @CsvSource({
        "journal dup key: notes, unique_key_sequence, true",
        "journal index: unique_key_sequence dup key: notes, unique_event_id, false"
    })
    void classifiesTheActualIndexWhenTheCollectionNameContainsDelimiters(String collectionName,
                                                                          String index, boolean sequence) {
        MongoCollection<Document> collection = journalCollection();
        MongoDBSyncJournalEventStore store = journalStore(collectionName, collection);
        MongoWriteException failure = driverCollision(collectionName, index, "{}");
        doThrow(failure).when(collection).insertOne(any(Document.class));

        if (sequence) {
            DuplicateStreamSequenceException error = assertThrows(DuplicateStreamSequenceException.class,
                    () -> store.write(eventAt(42L)));
            assertSame(failure, error.getCause());
            assertEquals("stream", error.getStreamId());
            assertEquals(42L, error.getStreamSequence());
        } else {
            assertSame(failure, assertThrows(MongoWriteException.class, () -> store.write(eventAt(42L))));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void transactionCollisionRetriesInANewSessionOnlyAfterRollback(boolean journalOnly) {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncJournalEventStore journal = mock(MongoDBSyncJournalEventStore.class);
        MongoDBSyncAuditRepository audit = mock(MongoDBSyncAuditRepository.class);
        JournalEventSequencer sequencer = staleSequencer(journal);
        ClientSession rejected = mock(ClientSession.class);
        ClientSession retried = mock(ClientSession.class);
        @SuppressWarnings("unchecked")
        TransactionManager<ClientSession> manager = mock(TransactionManager.class);
        when(manager.startSession(any(String.class))).thenReturn(rejected, retried);
        when(journal.write(eq(rejected), any())).thenThrow(new DuplicateStreamSequenceException("stream", 42L));
        when(journal.write(eq(retried), any())).thenReturn(Result.OK());
        when(audit.save(eq(retried), any())).thenReturn(Result.OK());
        MongoDBSyncAuditPersistence persistence = persistence(journal, audit, sequencer, manager);
        AuditEntry payload = eventAt(42L).getData();

        Result result = journalOnly ? persistence.appendEventFrom(payload) : persistence.writeEntry(payload);

        assertFalse(result.isError());
        InOrder order = inOrder(rejected, journal, retried, audit);
        order.verify(journal).write(eq(rejected), argThat(event -> event.getStreamSequence() == 42L));
        order.verify(rejected).abortTransaction();
        order.verify(rejected).close();
        order.verify(journal).getMajorityCommittedTail("stream");
        order.verify(journal).write(eq(retried), argThat(event -> event.getStreamSequence() == 46L));
        if (journalOnly) {
            verifyNoInteractions(audit);
        } else {
            order.verify(audit).save(retried, payload);
            verifyNoMoreInteractions(audit);
        }
        order.verify(retried).commitTransaction();
        assertEquals(47L, sequencer.newEvent(payload).getStreamSequence());
    }

    @Test
    void rollbackFailureIsNotRetried() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncJournalEventStore journal = mock(MongoDBSyncJournalEventStore.class);
        MongoDBSyncAuditRepository audit = mock(MongoDBSyncAuditRepository.class);
        JournalEventSequencer sequencer = staleSequencer(journal);
        ClientSession session = mock(ClientSession.class);
        @SuppressWarnings("unchecked")
        TransactionManager<ClientSession> manager = mock(TransactionManager.class);
        when(manager.startSession(any(String.class))).thenReturn(session);
        DuplicateStreamSequenceException collision = new DuplicateStreamSequenceException("stream", 42L);
        when(journal.write(eq(session), any())).thenThrow(collision);
        doThrow(new IllegalStateException("abort failed")).when(session).abortTransaction();
        MongoDBSyncAuditPersistence persistence = persistence(journal, audit, sequencer, manager);

        DatabaseTransactionException failure = assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(eventAt(42L).getData()));
        assertEquals(DatabaseTransactionException.RollbackStatus.FAILED, failure.getRollbackStatus());
        assertSame(collision, failure.getCause());
        InOrder order = inOrder(manager, session, journal);
        order.verify(manager).startSession(any(String.class));
        order.verify(journal).write(eq(session), argThat(event -> event.getStreamSequence() == 42L));
        order.verify(session).abortTransaction();
        order.verify(session).close();
        order.verify(manager).closeSession(any(String.class));
        order.verifyNoMoreInteractions();
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void standaloneRetryOnlyRepeatsTheJournalInsert(boolean journalOnly) {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncJournalEventStore journal = mock(MongoDBSyncJournalEventStore.class);
        MongoDBSyncAuditRepository audit = mock(MongoDBSyncAuditRepository.class);
        JournalEventSequencer sequencer = staleSequencer(journal);
        when(journal.write(any())).thenThrow(new DuplicateStreamSequenceException("stream", 42L))
                .thenReturn(Result.OK());
        MongoDBSyncAuditPersistence persistence = persistence(journal, audit, sequencer, null);
        AuditEntry payload = eventAt(42L).getData();
        IllegalStateException auditFailure = new IllegalStateException("audit save failed");
        when(audit.save(payload)).thenThrow(auditFailure);

        if (journalOnly) {
            assertFalse(persistence.appendEventFrom(payload).isError());
        } else {
            assertSame(auditFailure, assertThrows(IllegalStateException.class, () -> persistence.writeEntry(payload)));
        }
        InOrder order = inOrder(journal, audit);
        order.verify(journal).write(argThat(event -> event.getStreamSequence() == 42L));
        order.verify(journal).getMajorityCommittedTail("stream");
        order.verify(journal).write(argThat(event -> event.getStreamSequence() == 46L));
        if (journalOnly) {
            verifyNoInteractions(audit);
        } else {
            order.verify(audit).save(payload);
        }
        order.verifyNoMoreInteractions();
        assertEquals(47L, sequencer.newEvent(payload).getStreamSequence());
    }

    @Test
    void doesNotRetryWhenOnlyTheConfiguredReadCanSeeTheRejectedPosition() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoCollection<Document> configured = journalCollection();
        MongoCollection<Document> primary = journalCollection();
        MongoCollection<Document> committed = journalCollection();
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.getName()).thenReturn("test");
        when(database.getCollection("journal")).thenReturn(configured);
        when(configured.withReadPreference(ReadPreference.primary())).thenReturn(primary);
        when(primary.withReadConcern(ReadConcern.MAJORITY)).thenReturn(committed);
        @SuppressWarnings("unchecked")
        FindIterable<Document> configuredTail = mock(FindIterable.class, RETURNS_DEEP_STUBS);
        when(configured.find(any(Bson.class))).thenReturn(configuredTail);
        when(configuredTail.sort(any()).limit(1).first())
                .thenReturn(null, new MongoDBJournalEventMapper().toDocument(eventAt(1L)));
        @SuppressWarnings("unchecked")
        FindIterable<Document> committedTail = mock(FindIterable.class, RETURNS_DEEP_STUBS);
        when(committed.find(any(Bson.class))).thenReturn(committedTail);
        MongoWriteException collision = driverCollision("unique_key_sequence", "{}");
        doThrow(collision).doReturn(null).when(configured).insertOne(any(Document.class));
        MongoDBSyncJournalEventStore journal = new MongoDBSyncJournalEventStore(database, "journal",
                ReadConcern.LOCAL, ReadPreference.secondaryPreferred(), WriteConcern.MAJORITY);
        JournalEventSequencer sequencer = new JournalEventSequencerFactory(journal).forStream("stream");
        MongoDBSyncAuditRepository audit = mock(MongoDBSyncAuditRepository.class);

        DuplicateStreamSequenceException error = assertThrows(DuplicateStreamSequenceException.class,
                () -> persistence(journal, audit, sequencer, null).writeEntry(eventAt(1L).getData()));

        assertSame(collision, error.getCause());
        verify(configured).find(any(Bson.class));
        verify(committed).find(any(Bson.class));
        verify(configured).insertOne(any(Document.class));
        verifyNoInteractions(audit);
    }

    private static JournalEventSequencer staleSequencer(MongoDBSyncJournalEventStore journal) {
        when(journal.getLastEventByStream("stream")).thenReturn(Optional.of(eventAt(41L)));
        when(journal.getMajorityCommittedTail("stream")).thenReturn(Optional.of(eventAt(45L)));
        return new JournalEventSequencerFactory(journal).forStream("stream");
    }

    private static MongoDBSyncAuditPersistence persistence(MongoDBSyncJournalEventStore journal,
                                                          MongoDBSyncAuditRepository audit,
                                                          JournalEventSequencer sequencer,
                                                          TransactionManager<ClientSession> manager) {
        return new MongoDBSyncAuditPersistence(new CommunityConfiguration(), audit, journal, sequencer,
                manager != null, manager == null ? null : new MongoDBSyncTxWrapper(manager), true);
    }

    @SuppressWarnings("unchecked")
    private static MongoCollection<Document> journalCollection() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(collection.withReadConcern(any())).thenReturn(collection);
        when(collection.withReadPreference(any())).thenReturn(collection);
        when(collection.withWriteConcern(any())).thenReturn(collection);
        return collection;
    }

    private static MongoDBSyncJournalEventStore journalStore(String name, MongoCollection<Document> collection) {
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.getName()).thenReturn("test");
        when(database.getCollection(name)).thenReturn(collection);
        return new MongoDBSyncJournalEventStore(
                database, name, ReadConcern.MAJORITY, ReadPreference.primary(), WriteConcern.MAJORITY);
    }

    private static MongoWriteException driverCollision(String index, String key) {
        return driverCollision("journal", index, key);
    }

    private static MongoWriteException driverCollision(String collection, String index, String key) {
        return new MongoWriteException(new WriteError(11000,
                "E11000 duplicate key error collection: test." + collection + " index: " + index + " dup key: " + key,
                new BsonDocument()), new ServerAddress());
    }

    private static void write(MongoDBSyncJournalEventStore store, ClientSession session,
                              JournalEvent<AuditEntry> event, boolean transactional) {
        if (transactional) {
            store.write(session, event);
        } else {
            store.write(event);
        }
    }

    private static JournalEvent<AuditEntry> eventAt(long sequence) {
        AuditEntry payload = AuditEntryTestFactory.createTestAuditEntry(
                "change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null);
        return new JournalEvent<>("event", "key", JournalEventType.CHANGE_STATE,
                "stream", sequence, Instant.now(), payload);
    }
}
