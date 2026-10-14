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

import com.mongodb.client.ClientSession;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MongoDBSyncHistoryAppenderTest {
    private static final String STAGE = "normal-stage";
    private final MongoDBSyncAuditRepository audit = mock(MongoDBSyncAuditRepository.class);
    private final MongoDBSyncJournalEventStore journal = mock(MongoDBSyncJournalEventStore.class);
    private final JournalEventSequencerFactory factory = new JournalEventSequencerFactory(journal);
    private final Boundary wrapper = new Boundary();
    private final AuditEntry entry = AuditEntryTestFactory.createTestAuditEntry(
            "change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null);
    private final List<JournalEvent<AuditEntry>> durable = new ArrayList<>();
    private JournalEvent<AuditEntry> staged;
    private Result journalResult = Result.OK();
    private RuntimeException journalFailure;

    @AfterEach
    void resetFlag() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    void auditAppendPreservesHistoricalEntryRegardlessOfJournalFlag() {
        for (boolean enabled : new boolean[]{false, true}) {
            if (enabled) {
                FeatureFlag.enable(Features.JOURNAL_EVENTS);
            }
            MongoDBSyncAuditPersistence persistence = persistence(true);
            Result expected = Result.OK();
            when(audit.append(entry)).thenReturn(expected);
            assertSame(expected, persistence.append(entry));
            verify(audit).append(same(entry));
            verifyNoInteractions(journal);
            assertEquals(0, wrapper.commits);
            clearInvocations(audit);
        }
    }

    @Test
    void journalOffRejectsTransactionalSourceAppendWithoutSpendingSequence() {
        assertJournalOffRejectsSourceAppend(true);
    }

    @Test
    void journalOffRejectsStandaloneSourceAppendWithoutSpendingSequence() {
        assertJournalOffRejectsSourceAppend(false);
    }

    private void assertJournalOffRejectsSourceAppend(boolean transactional) {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistence(transactional);
        FeatureFlag.disable(Features.JOURNAL_EVENTS);
        assertEquals("Journal history requires JOURNAL_EVENTS to be enabled",
                assertThrows(IllegalStateException.class, () -> persistence.appendEventFrom(entry)).getMessage());
        verifyNoInteractions(journal, audit);
        assertTrue(durable.isEmpty());
        assertEquals(0, wrapper.commits);

        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEquals(1, durable.size());
        assertEquals(STAGE, durable.get(0).getStreamId());
        assertEquals(1L, durable.get(0).getStreamSequence());
        assertSame(entry, durable.get(0).getData());
        assertEquals(transactional ? 1 : 0, wrapper.commits);
        verifyNoInteractions(audit);
    }

    @Test
    void sourceAppendUsesPersistenceStreamAndCommitsOnlyJournal() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistence(true);
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEquals(1, wrapper.commits);
        assertEquals(1, durable.size());
        JournalEvent<AuditEntry> event = durable.get(0);
        assertSame(entry, event.getData());
        assertEquals(STAGE, event.getStreamId());
        assertEquals(1L, event.getStreamSequence());
        assertEquals(JournalEventType.CHANGE_STATE, event.getEventType());
        assertEquals(JournalEvent.DEFAULT_VERSION, event.getEventVersion());
        assertFalse(event.isAcknowledged());
        assertFalse(event.getEventId().isEmpty());
        assertFalse(event.getIdempotencyKey().isEmpty());
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEquals(2L, durable.get(1).getStreamSequence());
        assertNotEquals(event.getEventId(), durable.get(1).getEventId());
        assertNotEquals(wrapper.sessionIds.get(0), wrapper.sessionIds.get(1));
        verifyNoInteractions(audit);
    }

    @Test
    void sourceAppendWithoutTransactionsWritesOnlyJournal() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistence(false);
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEquals(0, wrapper.commits);
        assertEquals(1, durable.size());
        assertEquals(STAGE, durable.get(0).getStreamId());
        assertSame(entry, durable.get(0).getData());
        verifyNoInteractions(audit);
    }

    @Test
    void failedSourceContributionAbortsCommitAndRetryLeavesNoGap() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistence(true);
        journalResult = new Result.Error(new IllegalArgumentException("journal"));
        assertThrows(IllegalStateException.class, () -> persistence.appendEventFrom(entry));
        assertEquals(0, wrapper.commits);
        assertTrue(durable.isEmpty());
        journalResult = Result.OK();
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEquals(1L, durable.get(0).getStreamSequence());
        verifyNoInteractions(audit);
    }

    @Test
    void sourceWriteAndCommitExceptionsLeaveNoDurableEvent() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistence(true);
        journalFailure = new IllegalStateException("journal");
        assertSame(journalFailure, assertThrows(IllegalStateException.class,
                () -> persistence.appendEventFrom(entry)));
        journalFailure = null;
        wrapper.failCommit = true;
        assertEquals("commit", assertThrows(IllegalStateException.class,
                () -> persistence.appendEventFrom(entry)).getMessage());
        assertTrue(durable.isEmpty());
        assertEquals(0, wrapper.commits);
        wrapper.failCommit = false;
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEquals(1L, durable.get(0).getStreamSequence());
        verifyNoInteractions(audit);
    }

    @Test
    void failedNonTransactionalSourceWriteDoesNotAdvanceTail() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistence(false);
        journalResult = new Result.Error(new IllegalArgumentException("journal"));
        assertThrows(IllegalStateException.class, () -> persistence.appendEventFrom(entry));
        assertTrue(durable.isEmpty());
        journalResult = Result.OK();
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEquals(1L, durable.get(0).getStreamSequence());
        verifyNoInteractions(audit);
    }

    @Test
    void normalWritesRetainPersistenceStageAndJointTransaction() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistence(true);
        assertFalse(persistence.writeEntry(entry).isError());
        verify(audit).save(same(wrapper.session), same(entry));
        assertEquals(STAGE, durable.get(0).getStreamId());
        assertEquals(1, wrapper.commits);
    }

    @Test
    void auditContributionErrorAbortsJointWrite() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBSyncAuditPersistence persistence = persistence(true);
        when(audit.save(any(ClientSession.class), any())).thenReturn(new Result.Error(new RuntimeException("audit")));
        assertThrows(IllegalStateException.class, () -> persistence.writeEntry(entry));
        assertTrue(durable.isEmpty());
        assertEquals(0, wrapper.commits);
        when(audit.save(any(ClientSession.class), any())).thenReturn(Result.OK());
        assertFalse(persistence.writeEntry(entry).isError());
        assertEquals(1L, durable.get(0).getStreamSequence());
    }

    private MongoDBSyncAuditPersistence persistence(boolean transactional) {
        when(journal.getLastEventByStream(anyString())).thenAnswer(invocation -> {
            String stream = invocation.getArgument(0);
            return durable.stream().filter(event -> stream.equals(event.getStreamId()))
                    .reduce((first, last) -> last);
        });
        when(journal.write(any(ClientSession.class), any())).thenAnswer(invocation -> {
            assertSame(wrapper.session, invocation.getArgument(0));
            staged = invocation.getArgument(1);
            return writeResult();
        });
        when(journal.write(any(JournalEvent.class))).thenAnswer(invocation -> {
            staged = invocation.getArgument(0);
            Result result = writeResult();
            if (!result.isError()) {
                durable.add(staged);
            }
            return result;
        });
        when(audit.save(any(ClientSession.class), any())).thenReturn(Result.OK());
        MongoDBSyncAuditPersistence persistence = new MongoDBSyncAuditPersistence(
                new CommunityConfiguration(), audit, journal, factory.forStream(STAGE), transactional,
                transactional ? wrapper : null, true);
        persistence.initialize(RunnerId.generate());
        clearInvocations(audit, journal);
        return persistence;
    }

    private Result writeResult() {
        if (journalFailure != null) {
            throw journalFailure;
        }
        return journalResult;
    }

    private class Boundary implements ExecutionWrapper {
        private final ClientSession session = mock(ClientSession.class);
        private final List<String> sessionIds = new ArrayList<>();
        private int commits;
        private boolean failCommit;

        @Override
        public <C extends RuntimeContext, R> R wrapExecution(C context, Function<C, R> operation) {
            sessionIds.add(context.getSessionId());
            context.addDependency(session);
            staged = null;
            R result = operation.apply(context);
            if (failCommit) {
                throw new IllegalStateException("commit");
            }
            durable.add(staged);
            commits++;
            return result;
        }
    }
}
