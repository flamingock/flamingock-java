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

import com.mongodb.reactivestreams.client.ClientSession;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MongoDBReactiveHistoryAppenderTest {
    private final MongoDBReactiveAuditRepository audit = mock(MongoDBReactiveAuditRepository.class);
    private final MongoDBReactiveJournalEventStore journal = mock(MongoDBReactiveJournalEventStore.class);
    private final JournalEventSequencerFactory factory = new JournalEventSequencerFactory(journal);
    private final AuditEntry entry = AuditEntryTestFactory.createTestAuditEntry(
            "change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null);
    private final List<JournalEvent<AuditEntry>> durable = new ArrayList<>();
    private JournalEvent<AuditEntry> staged;
    private boolean failCommit;
    private int commits;
    private Result contribution = Result.OK();
    private final ClientSession session = mock(ClientSession.class);
    private final ExecutionWrapper wrapper = new ExecutionWrapper() {
        @Override
        public <C extends RuntimeContext, R> R wrapExecution(C context, Function<C, R> operation) {
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
    };

    @AfterEach
    void resetFlag() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    void historicalAppendPreservesEntryWithFlagOffAndOn() {
        MongoDBReactiveAuditPersistence persistence = persistence(false);
        when(audit.append(entry)).thenReturn(Result.OK());
        assertFalse(persistence.append(entry).isError());
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        assertFalse(persistence.append(entry).isError());
        verify(audit, org.mockito.Mockito.times(2)).append(entry);
        verifyNoInteractions(journal);
        assertEquals(0, commits);
    }

    @Test
    void disabledJournalRejectsBeforeStorageAccess() {
        MongoDBReactiveAuditPersistence persistence = persistence(true);
        assertEquals("Journal history requires JOURNAL_EVENTS to be enabled",
                assertThrows(IllegalStateException.class, () -> persistence.appendEventFrom(entry)).getMessage());
        verifyNoInteractions(journal, audit);
        assertEquals(0, commits);
        assertTrue(durable.isEmpty());
    }

    @Test
    void sourceEventUsesPersistenceStreamAndOnlyCommitsJournal() {
        MongoDBReactiveAuditPersistence persistence = persistence(true);
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEquals(1, commits);
        assertEvent(durable.get(0), "normal-stage", 1L);
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEvent(durable.get(1), "normal-stage", 2L);
        verifyNoInteractions(audit);
    }

    @Test
    void sourceNonTransactionalWriteIsDurableWithoutWrapper() {
        MongoDBReactiveAuditPersistence persistence = persistence(false);
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEvent(durable.get(0), "normal-stage", 1L);
        assertEquals(0, commits);
        verifyNoInteractions(audit);
    }

    @Test
    void failedContributionAndCommitDoNotSpendSequence() {
        MongoDBReactiveAuditPersistence persistence = persistence(true);
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        contribution = new Result.Error(new IllegalStateException("insert"));
        assertThrows(IllegalStateException.class, () -> persistence.appendEventFrom(entry));
        assertEquals(0, commits);
        assertTrue(durable.isEmpty());
        contribution = Result.OK();
        failCommit = true;
        assertThrows(IllegalStateException.class, () -> persistence.appendEventFrom(entry));
        assertEquals(0, commits);
        assertTrue(durable.isEmpty());
        failCommit = false;
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEvent(durable.get(0), "normal-stage", 1L);
        verifyNoInteractions(audit);
    }

    @Test
    void nonTransactionalFailureDoesNotSpendSequence() {
        MongoDBReactiveAuditPersistence persistence = persistence(false);
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        contribution = new Result.Error(new IllegalStateException("insert"));
        assertThrows(IllegalStateException.class, () -> persistence.appendEventFrom(entry));
        assertTrue(durable.isEmpty());
        contribution = Result.OK();
        assertFalse(persistence.appendEventFrom(entry).isError());
        assertEvent(durable.get(0), "normal-stage", 1L);
        verifyNoInteractions(audit);
    }

    @Test
    void normalJointWriteRetainsStageAndAbortsOnAuditError() {
        MongoDBReactiveAuditPersistence persistence = persistence(true);
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        when(audit.save(session, entry)).thenReturn(new Result.Error(new IllegalStateException("audit")));
        assertThrows(IllegalStateException.class, () -> persistence.writeEntry(entry));
        assertEquals(0, commits);
        assertTrue(durable.isEmpty());
        when(audit.save(session, entry)).thenReturn(Result.OK());
        assertFalse(persistence.writeEntry(entry).isError());
        assertEvent(durable.get(0), "normal-stage", 1L);
        assertEquals(1, commits);
    }

    private void assertEvent(JournalEvent<AuditEntry> event, String stream, long sequence) {
        assertSame(entry, event.getData());
        assertEquals(stream, event.getStreamId());
        assertEquals(sequence, event.getStreamSequence());
        assertEquals(JournalEventType.CHANGE_STATE, event.getEventType());
        assertEquals(JournalEvent.DEFAULT_VERSION, event.getEventVersion());
        assertFalse(event.isAcknowledged());
        assertFalse(event.getEventId().isEmpty());
        assertFalse(event.getIdempotencyKey().isEmpty());
    }

    private MongoDBReactiveAuditPersistence persistence(boolean transactional) {
        when(journal.getLastEventByStream(anyString())).thenAnswer(invocation -> {
            String stream = invocation.getArgument(0);
            return durable.stream().filter(event -> stream.equals(event.getStreamId())).reduce((a, b) -> b);
        });
        when(journal.append(any(ClientSession.class), any())).thenAnswer(invocation -> {
            assertSame(session, invocation.getArgument(0));
            staged = invocation.getArgument(1);
            return contribution;
        });
        when(journal.append(any(JournalEvent.class))).thenAnswer(invocation -> {
            if (!contribution.isError()) {
                durable.add(invocation.getArgument(0));
            }
            return contribution;
        });
        MongoDBReactiveAuditPersistence persistence = new MongoDBReactiveAuditPersistence(
                new CommunityConfiguration(), audit, journal, factory.forStream("normal-stage"),
                transactional, transactional ? wrapper : null, true);
        clearInvocations(journal);
        return persistence;
    }
}
