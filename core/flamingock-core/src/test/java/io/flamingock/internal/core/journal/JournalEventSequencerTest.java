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

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.journal.JournalEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JournalEventSequencerTest {

    @Test
    @DisplayName("derives a stable idempotency key from the CHANGE_STATE identity")
    void derivesStableIdempotencyKeyFromChangeStateIdentity() {
        AuditEntry source = auditEntry("execution-1", "change-1", AuditEntry.Status.APPLIED);
        AuditEntry differentNonIdentityFields = auditEntry(
                "execution-1", "change-1", AuditEntry.Status.APPLIED, "author-2", LocalDateTime.of(2026, 2, 1, 0, 0));
        JournalEvent<AuditEntry> first = new JournalEventSequencer("stage-1", 1L).newEvent(source);
        JournalEvent<AuditEntry> second = new JournalEventSequencer("stage-1", 1L).newEvent(differentNonIdentityFields);
        JournalEvent<AuditEntry> differentStream = new JournalEventSequencer("stage-2", 1L).newEvent(source);
        JournalEvent<AuditEntry> differentExecution = new JournalEventSequencer("stage-1", 1L)
                .newEvent(auditEntry("execution-2", "change-1", AuditEntry.Status.APPLIED));
        JournalEvent<AuditEntry> differentChange = new JournalEventSequencer("stage-1", 1L)
                .newEvent(auditEntry("execution-1", "change-2", AuditEntry.Status.APPLIED));
        JournalEvent<AuditEntry> differentState = new JournalEventSequencer("stage-1", 1L)
                .newEvent(auditEntry("execution-1", "change-1", AuditEntry.Status.FAILED));

        assertNotNull(first.getIdempotencyKey());
        assertEquals(first.getIdempotencyKey(), second.getIdempotencyKey());
        assertNotEquals(first.getIdempotencyKey(), differentStream.getIdempotencyKey());
        assertNotEquals(first.getIdempotencyKey(), differentExecution.getIdempotencyKey());
        assertNotEquals(first.getIdempotencyKey(), differentChange.getIdempotencyKey());
        assertNotEquals(first.getIdempotencyKey(), differentState.getIdempotencyKey());
    }

    private static AuditEntry auditEntry(String executionId, String changeId, AuditEntry.Status state) {
        return auditEntry(executionId, changeId, state, "author-1", LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private static AuditEntry auditEntry(
            String executionId, String changeId, AuditEntry.Status state, String author, LocalDateTime createdAt) {
        return new AuditEntry(
                executionId, "stage-1", changeId, author, createdAt, state,
                AuditEntry.ChangeType.STANDARD_CODE, "example.Change", "apply", "Change.java", 1L, "host-1",
                null, false, null, AuditTxType.NON_TX, "mongodb", "001", RecoveryStrategy.MANUAL_INTERVENTION, true);
    }
}
