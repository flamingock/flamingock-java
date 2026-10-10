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
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.external.store.AuditHistoryAppender;
import io.flamingock.internal.core.external.store.JournalHistoryAppender;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DynamoDBHistoryAppenderTest {
    private static final String DESTINATION = "normal-stage";
    private final DynamoDBAuditRepository audit = mock(DynamoDBAuditRepository.class);
    private final DynamoDBJournalEventStore journal = mock(DynamoDBJournalEventStore.class);
    private final JournalEventSequencerFactory factory = new JournalEventSequencerFactory(journal);
    private final Boundary wrapper = new Boundary();
    private final AuditEntry entry = AuditEntryTestFactory.createTestAuditEntry(
            "change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null);
    private final List<JournalEvent<AuditEntry>> attempts = new ArrayList<>();
    private final List<JournalEvent<AuditEntry>> durable = new ArrayList<>();
    private JournalEvent<AuditEntry> staged;
    private Result contribution = Result.OK();
    private RuntimeException contributionFailure;
    private boolean journalInitialized;

    @AfterEach
    void resetFlag() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    void auditHistoryAppendRemainsAvailableWithJournalOff() {
        Result expected = Result.OK();
        when(audit.writeEntry(entry)).thenReturn(expected);
        assertSame(expected, ((AuditHistoryAppender) persistence()).append(entry));
        verify(audit).writeEntry(same(entry));
        verifyNoInteractions(journal);
        assertEquals(0, wrapper.commits);
    }

    @Test
    void journalOffRejectsSourceBeforeStorageAccess() {
        JournalHistoryAppender appender = persistence();
        assertThrows(IllegalStateException.class, () -> appender.appendEventFrom(entry));
        verifyNoInteractions(journal);
        assertEquals(0, wrapper.commits);
    }

    @Test
    void sourceAppendBuildsStandardEnvelopeForPayloadStageAndCommitsOnlyJournal() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        JournalHistoryAppender appender = persistence();
        Instant before = Instant.now();
        assertFalse(appender.appendEventFrom(entry).isError());
        assertEquals(1, wrapper.commits);
        assertEquals(1, durable.size());
        JournalEvent<AuditEntry> event = durable.get(0);
        assertSame(entry, event.getData(), "the source payload must not be rewritten");
        assertNotEquals(entry.getStageId(), DESTINATION);
        assertEquals(entry.getStageId(), event.getStreamId());
        assertEquals(1L, event.getStreamSequence());
        assertEquals(JournalEventType.CHANGE_STATE, event.getEventType());
        assertEquals(JournalEvent.DEFAULT_VERSION, event.getEventVersion());
        assertFalse(event.isAcknowledged());
        assertFalse(event.getEventId().isEmpty());
        assertEquals(factory.forStream(entry.getStageId()).newEvent(entry).getIdempotencyKey(),
                event.getIdempotencyKey());
        assertFalse(event.getOccurredAt().isBefore(before));
        assertFalse(event.getOccurredAt().isAfter(Instant.now()));
        verifyNoInteractions(audit);
        assertFalse(appender.appendEventFrom(entry).isError());
        assertEquals(2L, durable.get(1).getStreamSequence());
        assertNotEquals(event.getEventId(), durable.get(1).getEventId());
        verifyNoInteractions(audit);
    }

    @Test
    void sourceAppendInitializesJournalBeforeReadingTailWhenEnabledAfterPersistenceInitialization() {
        JournalHistoryAppender appender = persistence();
        verifyNoInteractions(journal);
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        assertFalse(appender.appendEventFrom(entry).isError());
        verify(journal).initialize(true);
        assertEquals(1L, durable.get(0).getStreamSequence());
        verifyNoInteractions(audit);
    }

    @Test
    void sourceContributionErrorAbortsCommitAndRetryUsesDurableTailWithoutGap() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        JournalHistoryAppender appender = persistence();
        assertFalse(appender.appendEventFrom(entry).isError());
        IllegalArgumentException cause = new IllegalArgumentException("put");
        contribution = new Result.Error(cause);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> appender.appendEventFrom(entry));
        assertSame(cause, failure.getCause());
        assertEquals(1, wrapper.commits);
        assertEquals(1, durable.size());
        contribution = Result.OK();
        assertFalse(appender.appendEventFrom(entry).isError());
        assertEquals(2L, attempts.get(1).getStreamSequence());
        assertEquals(2L, durable.get(1).getStreamSequence());
        verifyNoInteractions(audit);
    }

    @Test
    void sourceThrownContributionAndCommitFailurePropagateAndRetryLeavesNoGap() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        JournalHistoryAppender appender = persistence();
        contributionFailure = new IllegalStateException("contribution");
        assertSame(contributionFailure, assertThrows(IllegalStateException.class,
                () -> appender.appendEventFrom(entry)));
        assertEquals(0, wrapper.commits);
        contributionFailure = null;
        wrapper.failCommit = true;
        assertEquals("commit", assertThrows(IllegalStateException.class,
                () -> appender.appendEventFrom(entry)).getMessage());
        assertTrue(durable.isEmpty());
        wrapper.failCommit = false;
        assertFalse(appender.appendEventFrom(entry).isError());
        assertEquals(1L, durable.get(0).getStreamSequence());
        assertEquals(1, wrapper.commits);
        verifyNoInteractions(audit);
    }

    @Test
    void failedJournalContributionAbortsNormalCommitAndRetryReusesPosition() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistence();
        contribution = new Result.Error(new IllegalArgumentException("put"));
        assertThrows(IllegalStateException.class, () -> persistence.writeEntry(entry));
        assertEquals(0, wrapper.commits);
        verify(audit, never()).contributeToTransaction(any(), any());
        contribution = Result.OK();
        assertFalse(persistence.writeEntry(entry).isError());
        assertEquals(1L, durable.get(0).getStreamSequence());
    }

    @Test
    void failedAuditContributionAbortsJointCommitAndRetryReusesPosition() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistence();
        when(audit.contributeToTransaction(any(), any())).thenReturn(new Result.Error(new RuntimeException("audit")));
        assertThrows(IllegalStateException.class, () -> persistence.writeEntry(entry));
        assertEquals(0, wrapper.commits);
        assertTrue(durable.isEmpty());
        when(audit.contributeToTransaction(any(), any())).thenReturn(Result.OK());
        assertFalse(persistence.writeEntry(entry).isError());
        assertEquals(1L, durable.get(0).getStreamSequence());
    }

    @Test
    void normalThrownContributionAndCommitFailuresLeaveNoGap() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistence();
        contributionFailure = new IllegalStateException("contribution");
        assertSame(contributionFailure, assertThrows(IllegalStateException.class, () -> persistence.writeEntry(entry)));
        contributionFailure = null;
        wrapper.failCommit = true;
        assertThrows(IllegalStateException.class, () -> persistence.writeEntry(entry));
        assertTrue(durable.isEmpty());
        wrapper.failCommit = false;
        assertFalse(persistence.writeEntry(entry).isError());
        assertEquals(1L, durable.get(0).getStreamSequence());
    }

    @Test
    void normalWritesContributeToSameBuilderAndAdvanceOnlyAfterCommit() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        DynamoDBAuditPersistence persistence = persistence();
        assertFalse(persistence.writeEntry(entry).isError());
        verify(audit).contributeToTransaction(same(wrapper.builder), same(entry));
        assertFalse(persistence.writeEntry(entry).isError());
        assertEquals(2, wrapper.commits);
        assertEquals(DESTINATION, durable.get(0).getStreamId());
        assertEquals(DESTINATION, durable.get(1).getStreamId());
        assertEquals(1L, durable.get(0).getStreamSequence());
        assertEquals(2L, durable.get(1).getStreamSequence());
    }

    private DynamoDBAuditPersistence persistence() {
        doAnswer(invocation -> {
            journalInitialized = true;
            return null;
        }).when(journal).initialize(true);
        when(journal.getLastEventByStream(anyString())).thenAnswer(invocation -> {
            assertTrue(journalInitialized, "initialize journal before reading its durable tail");
            return durable.isEmpty() ? Optional.empty() : Optional.of(durable.get(durable.size() - 1));
        });
        when(journal.contributeToTransaction(any(), any())).thenAnswer(invocation -> {
            assertSame(wrapper.builder, invocation.getArgument(0));
            staged = invocation.getArgument(1);
            attempts.add(staged);
            if (contributionFailure != null) {
                throw contributionFailure;
            }
            return contribution;
        });
        when(audit.contributeToTransaction(any(), any())).thenReturn(Result.OK());
        DynamoDBAuditPersistence persistence = new DynamoDBAuditPersistence(
                new CommunityConfiguration(), audit, journal, factory, DESTINATION, wrapper, true);
        persistence.initialize(RunnerId.generate());
        clearInvocations(audit, journal);
        return persistence;
    }

    private class Boundary implements ExecutionWrapper {
        private int commits;
        private boolean failCommit;
        private TransactWriteItemsEnhancedRequest.Builder builder;

        @Override
        public <C extends RuntimeContext, R> R wrapExecution(C context, Function<C, R> operation) {
            builder = TransactWriteItemsEnhancedRequest.builder();
            context.addDependency(builder);
            staged = null;
            R result = operation.apply(context);
            assertEquals(durable.size() + 1L, staged.getStreamSequence(), "use the next durable stream position");
            if (failCommit) {
                throw new IllegalStateException("commit");
            }
            durable.add(staged);
            commits++;
            return result;
        }
    }
}
