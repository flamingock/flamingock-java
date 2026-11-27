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
import io.flamingock.internal.common.core.journal.DuplicateStreamSequenceException;
import io.flamingock.internal.common.core.journal.JournalEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void successfulAttemptDoesNotReadOrConfirm() {
        JournalEventSequencer sequencer = new JournalEventSequencer("stage-1", 42);
        JournalEventReader reader = mock(JournalEventReader.class);
        AuditEntry payload = auditEntry("execution", "change", AuditEntry.Status.APPLIED);
        assertEquals(42L, retry(sequencer, reader, () -> sequencer.newEvent(payload).getStreamSequence()));
        assertEquals(42L, sequencer.newEvent(payload).getStreamSequence());
        verify(reader, never()).getLastEventByStream("stage-1");
    }

    @Test
    void collisionRefreshesOnceAndRequiresConfirmation() {
        JournalEventSequencer sequencer = new JournalEventSequencer("stage-1", 42);
        JournalEventReader reader = mock(JournalEventReader.class);
        AuditEntry payload = auditEntry("execution", "change", AuditEntry.Status.APPLIED);
        when(reader.getLastEventByStream("stage-1")).thenReturn(Optional.of(
                new JournalEventSequencer("stage-1", 45).newEvent(payload)));
        AtomicInteger attempts = new AtomicInteger();
        assertEquals(46L, retry(sequencer, reader, () -> {
            long sequence = sequencer.newEvent(payload).getStreamSequence();
            if (attempts.getAndIncrement() == 0) {
                assertEquals(42L, sequence);
                throw new DuplicateStreamSequenceException("stage-1", sequence);
            }
            return sequence;
        }));
        assertEquals(2, attempts.get());
        assertEquals(46L, sequencer.newEvent(payload).getStreamSequence());
        sequencer.confirm();
        assertEquals(47L, sequencer.newEvent(payload).getStreamSequence());
        verify(reader).getLastEventByStream("stage-1");
    }

    @Test
    void mismatchedOrAbsentPendingCollisionNeverReads() {
        JournalEventReader reader = mock(JournalEventReader.class);
        AuditEntry payload = auditEntry("execution", "change", AuditEntry.Status.APPLIED);
        for (int scenario = 0; scenario < 3; scenario++) {
            JournalEventSequencer sequencer = new JournalEventSequencer("stage-1", 42);
            if (scenario != 0) {
                sequencer.newEvent(payload);
            }
            DuplicateStreamSequenceException collision = new DuplicateStreamSequenceException(
                    scenario == 1 ? "other" : "stage-1", scenario == 2 ? 41 : 42);
            assertSame(collision, assertThrows(DuplicateStreamSequenceException.class,
                    () -> retry(sequencer, reader, () -> { throw collision; })));
            assertEquals(42L, sequencer.newEvent(payload).getStreamSequence());
        }
        verify(reader, never()).getLastEventByStream("stage-1");
    }

    @Test
    void missingWrongStreamOrStaleTailPreservesPendingAndCollision() {
        AuditEntry payload = auditEntry("execution", "change", AuditEntry.Status.APPLIED);
        for (int scenario = 0; scenario < 3; scenario++) {
            JournalEventSequencer sequencer = new JournalEventSequencer("stage-1", 42);
            JournalEventReader reader = mock(JournalEventReader.class);
            Optional<JournalEvent<AuditEntry>> tail = scenario == 0 ? Optional.empty() : Optional.of(
                    new JournalEventSequencer(scenario == 1 ? "other" : "stage-1", scenario == 1 ? 45 : 41)
                            .newEvent(payload));
            when(reader.getLastEventByStream("stage-1")).thenReturn(tail);
            DuplicateStreamSequenceException collision = new DuplicateStreamSequenceException("stage-1", 42);
            assertSame(collision, assertThrows(DuplicateStreamSequenceException.class,
                    () -> retry(sequencer, reader, () -> {
                        sequencer.newEvent(payload);
                        throw collision;
                    })));
            sequencer.confirm();
            assertEquals(43L, sequencer.newEvent(payload).getStreamSequence());
        }
    }

    @Test
    void readerFailureIsSuppressedOnOriginalCollision() {
        JournalEventSequencer sequencer = new JournalEventSequencer("stage-1", 42);
        JournalEventReader reader = mock(JournalEventReader.class);
        RuntimeException readFailure = new IllegalStateException("read failed");
        when(reader.getLastEventByStream("stage-1")).thenThrow(readFailure);
        DuplicateStreamSequenceException collision = new DuplicateStreamSequenceException("stage-1", 42);
        AuditEntry payload = auditEntry("execution", "change", AuditEntry.Status.APPLIED);
        assertSame(collision, assertThrows(DuplicateStreamSequenceException.class,
                () -> retry(sequencer, reader, () -> { sequencer.newEvent(payload); throw collision; })));
        assertEquals(1, collision.getSuppressed().length);
        assertSame(readFailure, collision.getSuppressed()[0]);
        sequencer.confirm();
        assertEquals(43L, sequencer.newEvent(payload).getStreamSequence());
    }

    @Test
    void retryIsBoundedAndGenericFailuresPropagate() {
        JournalEventSequencer sequencer = new JournalEventSequencer("stage-1", 42);
        JournalEventReader reader = mock(JournalEventReader.class);
        AuditEntry payload = auditEntry("execution", "change", AuditEntry.Status.APPLIED);
        when(reader.getLastEventByStream("stage-1")).thenReturn(Optional.of(
                new JournalEventSequencer("stage-1", 45).newEvent(payload)));
        DuplicateStreamSequenceException second = new DuplicateStreamSequenceException("stage-1", 46);
        AtomicInteger attempts = new AtomicInteger();
        assertSame(second, assertThrows(DuplicateStreamSequenceException.class, () -> retry(sequencer, reader, () -> {
            long sequence = sequencer.newEvent(payload).getStreamSequence();
            if (attempts.getAndIncrement() == 0) {
                throw new DuplicateStreamSequenceException("stage-1", sequence);
            }
            throw second;
        })));
        assertEquals(2, attempts.get());
        verify(reader).getLastEventByStream("stage-1");
        RuntimeException failure = new IllegalStateException("write failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> retry(sequencer, reader, () -> { throw failure; })));
    }

    private static <T> T retry(JournalEventSequencer sequencer, JournalEventReader reader, Supplier<T> write) {
        return sequencer.retryOnceOnCollision(reader::getLastEventByStream, write);
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
