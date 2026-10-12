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

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CouchbaseHistoryAppenderTest {
    private final CouchbaseAuditor auditor = mock(CouchbaseAuditor.class);
    private final CouchbaseJournalEventStore journal = mock(CouchbaseJournalEventStore.class);
    private final ExecutionWrapper wrapper = mock(ExecutionWrapper.class);
    private final CouchbaseAuditPersistence persistence = new CouchbaseAuditPersistence(
            new CommunityConfiguration(), auditor, journal,
            new JournalEventSequencerFactory(journal).forStream("destination"), wrapper,
            "_default", "audit", "journal", true);

    @AfterEach
    void clearFlag() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    void historicalAppendPreservesEntryAndDoesNotWriteJournalWhenDisabled() {
        verifyHistoricalAppend();
    }

    @Test
    void historicalAppendPreservesEntryAndDoesNotWriteJournalWhenEnabled() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        verifyHistoricalAppend();
    }

    @Test
    void journalAppendRequiresFlagBeforeStorageAccess() {
        // Construction seeds the existing persistence stream; the appender itself must not access storage.
        org.mockito.Mockito.clearInvocations(journal);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> persistence.appendEventFrom(entry()));
        org.junit.jupiter.api.Assertions.assertEquals(
                "Journal history requires JOURNAL_EVENTS to be enabled", error.getMessage());
        verifyNoInteractions(auditor, journal, wrapper);
    }

    @Test
    void journalOnlyUsesPersistenceStreamAndConfirmsOnlyAfterDurableCompletion() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        com.couchbase.client.java.transactions.TransactionAttemptContext attempt =
                mock(com.couchbase.client.java.transactions.TransactionAttemptContext.class);
        java.util.List<io.flamingock.internal.common.core.journal.JournalEvent<AuditEntry>> events =
                new java.util.ArrayList<>();
        when(journal.contributeToTransaction(org.mockito.ArgumentMatchers.same(attempt),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                    events.add(invocation.getArgument(1));
                    return Result.OK();
                });
        java.util.List<io.flamingock.internal.common.core.journal.JournalEvent<AuditEntry>> durable =
                new java.util.ArrayList<>();
        when(journal.getLastEventByStream("destination")).thenAnswer(invocation ->
                durable.isEmpty() ? java.util.Optional.empty()
                        : java.util.Optional.of(durable.get(durable.size() - 1)));
        boolean[] failCommit = {true};
        ExecutionWrapper ownedWrapper = new ExecutionWrapper() {
            @Override
            public <C extends io.flamingock.internal.common.core.context.RuntimeContext, R> R wrapExecution(
                    C context, java.util.function.Function<C, R> operation) {
                context.addDependency(new io.flamingock.internal.common.core.context.Dependency(attempt));
                R result = operation.apply(context);
                if (failCommit[0]) {
                    throw new IllegalStateException("durable completion failed");
                }
                durable.add(events.get(events.size() - 1));
                return result;
            }
        };
        CouchbaseAuditPersistence appender = new CouchbaseAuditPersistence(
                new CommunityConfiguration(), auditor, journal, new JournalEventSequencerFactory(journal).forStream("destination"),
                 ownedWrapper, "_default", "audit", "journal", true);
        AuditEntry payload = entry();
        assertThrows(IllegalStateException.class, () -> appender.appendEventFrom(payload));
        failCommit[0] = false;
        appender.appendEventFrom(payload);
        appender.appendEventFrom(payload);
        org.junit.jupiter.api.Assertions.assertEquals(3, events.size());
        org.junit.jupiter.api.Assertions.assertEquals(1L, events.get(0).getStreamSequence());
        org.junit.jupiter.api.Assertions.assertEquals(1L, events.get(1).getStreamSequence());
        org.junit.jupiter.api.Assertions.assertEquals(2L, events.get(2).getStreamSequence());
        for (io.flamingock.internal.common.core.journal.JournalEvent<AuditEntry> event : events) {
            assertSame(payload, event.getData());
            org.junit.jupiter.api.Assertions.assertEquals("destination", event.getStreamId());
            org.junit.jupiter.api.Assertions.assertEquals(
                    io.flamingock.internal.common.core.journal.JournalEventType.CHANGE_STATE, event.getEventType());
            org.junit.jupiter.api.Assertions.assertFalse(event.isAcknowledged());
        }
        verify(journal, org.mockito.Mockito.times(2)).getLastEventByStream("destination");
        verifyNoInteractions(auditor);
    }

    private void verifyHistoricalAppend() {
        AuditEntry entry = entry();
        Result expected = Result.OK();
        when(auditor.append(entry)).thenReturn(expected);
        org.mockito.Mockito.clearInvocations(journal);
        assertSame(expected, persistence.append(entry));
        verify(auditor).append(entry);
        verifyNoInteractions(journal, wrapper);
    }

    private static AuditEntry entry() {
        return AuditEntryTestFactory.createTestAuditEntry(
                "imported-change", AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null);
    }
}
