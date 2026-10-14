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
package io.flamingock.internal.core.journal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditPersistence;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.external.store.JournalHistoryAppender;
import io.flamingock.internal.core.system.JournalBackfillChange;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;

import static io.flamingock.internal.common.core.pipeline.PipelineHelper.SYSTEM_STAGE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JournalBackfillChangeTest {
    private interface JournalPersistence extends AuditPersistence, JournalHistoryAppender {
    }

    private final AuditPersistence reader = mock(AuditPersistence.class);
    @SuppressWarnings("unchecked")
    private final AuditPersistenceFactory<AuditPersistence> factory = mock(AuditPersistenceFactory.class);
    private final JournalPersistence appender = mock(JournalPersistence.class);
    private final JournalBackfillChange change = new JournalBackfillChange();

    @BeforeEach
    void enableJournalEvents() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
    }

    @AfterEach
    void resetJournalEvents() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    void disabledJournalEventsFailBeforeReadingOrAppending() {
        FeatureFlag.disable(Features.JOURNAL_EVENTS);

        FlamingockException exception = assertThrows(FlamingockException.class, () -> change.journalBackfill(factory));

        assertEquals("Journal backfill requires journal events to be enabled."
                + " Enable journal events before backfilling audit history.", exception.getMessage());
        verifyNoInteractions(factory, reader, appender);
    }

    @Test
    void emptyHistoryDoesNotAppend() {
        when(factory.get(SYSTEM_STAGE_ID)).thenReturn(reader);
        when(reader.getAuditHistory()).thenReturn(Collections.emptyList());
        change.journalBackfill(factory);
        verifyNoInteractions(appender);
        verify(factory).get(SYSTEM_STAGE_ID);
    }

    @Test
    void mixedHistoryPreservesPayloadAndOrderAndSkipsOwnRecords() {
        AuditEntry standard = entry("other", "stage");
        AuditEntry own = entry(JournalBackfillChange.CHANGE_ID, SYSTEM_STAGE_ID);
        AuditEntry orderOnly = entry("00101", SYSTEM_STAGE_ID);
        AuditEntry legacy = entry("imported", null);
        JournalPersistence systemPersistence = mock(JournalPersistence.class);
        JournalPersistence legacyPersistence = mock(JournalPersistence.class);
        when(factory.get(SYSTEM_STAGE_ID)).thenReturn(systemPersistence);
        when(systemPersistence.getAuditHistory()).thenReturn(Arrays.asList(standard, own, orderOnly, legacy));
        when(factory.get("stage")).thenReturn(appender);
        when(factory.get(null)).thenReturn(legacyPersistence);
        when(appender.appendEventFrom(standard)).thenReturn(Result.OK());
        when(systemPersistence.appendEventFrom(orderOnly)).thenReturn(Result.OK());
        when(legacyPersistence.appendEventFrom(legacy)).thenReturn(Result.OK());

        change.journalBackfill(factory);

        InOrder order = inOrder(appender, systemPersistence, legacyPersistence);
        order.verify(systemPersistence).getAuditHistory();
        order.verify(appender).appendEventFrom(standard);
        order.verify(systemPersistence).appendEventFrom(orderOnly);
        order.verify(legacyPersistence).appendEventFrom(legacy);
        order.verifyNoMoreInteractions();
    }

    @Test
    void interleavedStagesReuseTheirPersistenceAndPreserveHistoryOrder() {
        AuditEntry first = entry("first", "stage-a");
        AuditEntry second = entry("second", "stage-b");
        AuditEntry third = entry("third", "stage-a");
        JournalPersistence otherAppender = mock(JournalPersistence.class);
        when(factory.get(SYSTEM_STAGE_ID)).thenReturn(reader);
        when(reader.getAuditHistory()).thenReturn(Arrays.asList(first, second, third));
        when(factory.get("stage-a")).thenReturn(appender);
        when(factory.get("stage-b")).thenReturn(otherAppender);
        when(appender.appendEventFrom(first)).thenReturn(Result.OK());
        when(otherAppender.appendEventFrom(second)).thenReturn(Result.OK());
        when(appender.appendEventFrom(third)).thenReturn(Result.OK());

        change.journalBackfill(factory);

        InOrder order = inOrder(appender, otherAppender);
        order.verify(appender).appendEventFrom(first);
        order.verify(otherAppender).appendEventFrom(second);
        order.verify(appender).appendEventFrom(third);
        order.verifyNoMoreInteractions();
        verify(factory).get("stage-a");
        verify(factory).get("stage-b");
    }

    @Test
    void retryWithNewInstanceRepeatsEntriesAndAppendFailureStopsProcessing() {
        AuditEntry first = entry("first", "stage");
        AuditEntry second = entry("second", "stage");
        AuditEntry third = entry("third", "stage");
        when(factory.get(SYSTEM_STAGE_ID)).thenReturn(reader);
        when(factory.get("stage")).thenReturn(appender);
        when(reader.getAuditHistory()).thenReturn(Arrays.asList(first, second, third));
        RuntimeException failure = new RuntimeException("write failed");
        when(appender.appendEventFrom(first)).thenReturn(Result.OK());
        when(appender.appendEventFrom(second)).thenReturn(new Result.Error(failure), Result.OK());
        when(appender.appendEventFrom(third)).thenReturn(Result.OK());

        FlamingockException exception = assertThrows(FlamingockException.class, () -> change.journalBackfill(factory));
        assertEquals("Cannot backfill journal entry second", exception.getMessage());
        assertSame(failure, exception.getCause());
        verify(appender).appendEventFrom(first);
        verify(appender).appendEventFrom(second);
        verify(appender, never()).appendEventFrom(third);

        new JournalBackfillChange().journalBackfill(factory);

        verify(factory, times(2)).get(SYSTEM_STAGE_ID);
        verify(factory, times(2)).get("stage");
        verify(appender, times(2)).appendEventFrom(first);
        verify(appender, times(2)).appendEventFrom(second);
        verify(appender).appendEventFrom(third);
    }

    @Test
    void nullAppendResultStopsBeforeNextEntry() {
        AuditEntry first = entry("first", "stage");
        AuditEntry second = entry("second", "stage");
        when(factory.get(SYSTEM_STAGE_ID)).thenReturn(reader);
        when(factory.get("stage")).thenReturn(appender);
        when(reader.getAuditHistory()).thenReturn(Arrays.asList(first, second));

        FlamingockException exception = assertThrows(FlamingockException.class, () -> change.journalBackfill(factory));
        assertEquals("Cannot backfill journal entry first", exception.getMessage());
        assertNull(exception.getCause());
        verify(appender).appendEventFrom(first);
        verify(appender, never()).appendEventFrom(second);
    }

    private AuditEntry entry(String id, String stage) {
        AuditEntry entry = mock(AuditEntry.class);
        when(entry.getChangeId()).thenReturn(id);
        when(entry.getStageId()).thenReturn(stage);
        return entry;
    }
}
