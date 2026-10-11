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
package io.flamingock.internal.core.external.store.audit.community;

import java.util.Locale;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.external.store.AuditHistoryAppender;
import io.flamingock.internal.core.external.store.JournalHistoryAppender;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class CommunityAuditPersistenceDefaultsTest {
    private final CommunityAuditPersistence persistence = mock(CommunityAuditPersistence.class, CALLS_REAL_METHODS);
    private final AuditEntry entry = mock(AuditEntry.class);

    @BeforeEach
    @AfterEach
    void removeJournalFlag() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void auditHistoryAppendIsExplicitlyUnsupportedRegardlessOfJournalFlag(boolean enabled) {
        setJournalEnabled(enabled);

        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
                () -> ((AuditHistoryAppender) persistence).append(entry));

        assertUnsupportedMessage(failure, "audit");
        verify((AuditHistoryAppender) persistence).append(entry);
        verifyNoMoreInteractions(persistence, entry);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void journalAppendEventFromRejectsDisabledOrUnimplementedStorage(boolean enabled) {
        setJournalEnabled(enabled);

        Class<? extends RuntimeException> expectedFailure = enabled
                ? UnsupportedOperationException.class : IllegalStateException.class;
        RuntimeException failure = assertThrows(expectedFailure, () -> journalAppender().appendEventFrom(entry));

        assertJournalFailure(failure, enabled);
        verify(journalAppender()).appendEventFrom(entry);
        verifyNoMoreInteractions(persistence, entry);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryEntryWritesRemainUsable(boolean enabled) {
        setJournalEnabled(enabled);
        Result outcome = Result.OK();
        doReturn(outcome).when(persistence).writeEntry(entry);

        assertSame(outcome, persistence.writeEntry(entry));

        verify(persistence).writeEntry(entry);
        verifyNoMoreInteractions(persistence, entry);
    }

    private JournalHistoryAppender journalAppender() {
        return (JournalHistoryAppender) persistence;
    }

    private void setJournalEnabled(boolean enabled) {
        if (enabled) {
            FeatureFlag.enable(Features.JOURNAL_EVENTS);
        }
    }

    private void assertJournalFailure(RuntimeException failure, boolean enabled) {
        if (enabled) {
            assertUnsupportedMessage(failure, "journal");
        } else {
            assertEquals(IllegalStateException.class, failure.getClass());
            assertTrue(failure.getMessage().contains("JOURNAL_EVENTS"), "identify the feature that must be enabled");
        }
    }

    private void assertUnsupportedMessage(RuntimeException failure, String operation) {
        String message = failure.getMessage().toLowerCase(Locale.ROOT);
        assertTrue(message.contains(operation), "identify the unavailable history operation");
        assertTrue(message.contains("unsupported") || message.contains("not supported"), "explain lack of support");
        assertTrue(message.contains("backend") || message.contains("implement"), "indicate where support is required");
    }
}
