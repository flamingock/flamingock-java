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
import io.flamingock.internal.common.core.journal.JournalEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalEventSequencerTest {

    @Test
    @DisplayName("reserves without a payload and reuses the position until durable confirmation")
    void reservesWithoutPayloadUntilConfirmed() {
        JournalEventSequencer sequencer = new JournalEventSequencer("history-stream", 7L);

        assertEquals(7L, sequencer.reserveSequence());
        assertEquals(7L, sequencer.reserveSequence());

        sequencer.confirm(); // caller confirms only after durable completion

        assertEquals(8L, sequencer.reserveSequence());
        assertEquals(8L, sequencer.reserveSequence());
    }

    @Test
    @DisplayName("confirmation without an outstanding reservation does not spend a position")
    void confirmationWithoutReservationIsNoOp() {
        JournalEventSequencer sequencer = new JournalEventSequencer("history-stream", 7L);

        sequencer.confirm();
        assertEquals(7L, sequencer.reserveSequence());
        sequencer.confirm();
        sequencer.confirm();

        assertEquals(8L, sequencer.reserveSequence());
    }

    @Test
    @DisplayName("normal audit events share the reservation lifecycle and retain their metadata")
    void normalAuditEventsUseSameReservation() {
        JournalEventSequencer sequencer = new JournalEventSequencer("stage-1", 7L);
        AuditEntry payload = auditEntry("execution-1", "change-1", AuditEntry.Status.APPLIED);

        assertEquals(7L, sequencer.reserveSequence());
        JournalEvent<AuditEntry> event = sequencer.newEvent(payload);

        assertEquals(7L, event.getStreamSequence());
        assertEquals(7L, sequencer.reserveSequence());
        assertEquals("stage-1", event.getStreamId());
        assertEquals(JournalEventType.CHANGE_STATE, event.getEventType());
        assertEquals(JournalEvent.DEFAULT_VERSION, event.getEventVersion());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
        assertSame(payload, event.getData());
        assertEquals(new JournalEventSequencer("stage-1", 7L).newEvent(payload).getIdempotencyKey(),
                event.getIdempotencyKey());

        sequencer.confirm();
        JournalEvent<AuditEntry> next = sequencer.newEvent(payload);
        assertEquals(8L, next.getStreamSequence());
        assertEquals(8L, sequencer.reserveSequence());
        assertNotEquals(event.getEventId(), next.getEventId());
        assertEquals(event.getIdempotencyKey(), next.getIdempotencyKey());
    }

    @Test
    @DisplayName("caller can construct a complete generic event with its own metadata and payload")
    void callerBuildsCompleteGenericEvent() {
        String streamId = "historical-stage";
        JournalEventSequencer sequencer = new JournalEventSequencer(streamId, 12L);
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");
        JournalEvent<String> event = new JournalEvent<>(
                "historical-event", "caller-key", JournalEventType.CHANGE_STATE, 3,
                streamId, sequencer.reserveSequence(), occurredAt, "historical-payload", true);

        assertEquals("historical-event", event.getEventId());
        assertEquals("caller-key", event.getIdempotencyKey());
        assertEquals(JournalEventType.CHANGE_STATE, event.getEventType());
        assertEquals(3, event.getEventVersion());
        assertEquals(streamId, event.getStreamId());
        assertEquals(12L, event.getStreamSequence());
        assertEquals(occurredAt, event.getOccurredAt());
        assertEquals("historical-payload", event.getData());
        assertTrue(event.isAcknowledged());
        assertEquals(12L, sequencer.reserveSequence());

        sequencer.confirm();

        assertEquals(13L, sequencer.reserveSequence());
    }

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
