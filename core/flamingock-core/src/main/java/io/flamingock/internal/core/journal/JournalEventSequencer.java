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
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * Hands out stream positions for a single stream, in memory — safe because the stage lock guarantees one
 * writer per stream.
 * <p>
 * A position is only spent once the caller confirms the event was durably written. Until then the same
 * position is handed out again, so a write that fails and aborts leaves no hole in the stream. That matters
 * beyond tidiness: a contiguous {@code streamSequence} is what lets a consumer reconstruct order and tell
 * "still in flight" from "lost", so gaps must never be produced by ordinary failure handling.
 * <p>
 * If the write outcome is uncertain, the caller must mark it before the next allocation. The sequencer
 * then reads the durable tail and will not allocate if that read fails. A caller that forgets both confirmation
 * and uncertainty marking can still collide with the unique {@code (streamId, streamSequence)} index.
 */
public class JournalEventSequencer {
    private final String streamId;
    private long nextSequence;
    private boolean pendingConfirmation;
    private boolean uncertainWriteOutcome;
    private final JournalEventReader journalEventReader;

    JournalEventSequencer(String streamId, long initialSequence) {
        this(streamId, initialSequence, null);
    }

    JournalEventSequencer(String streamId, long initialSequence, JournalEventReader journalEventReader) {
        this.streamId = streamId;
        this.nextSequence = initialSequence;   // seeded from outside
        this.journalEventReader = journalEventReader;
    }

    /**
     * Builds the next event <em>without</em> spending its stream position; call {@link #confirm()} once the
     * event is durably written.
     */
    public synchronized JournalEvent<AuditEntry> newEvent(AuditEntry payload) {
        if (uncertainWriteOutcome) {
            if (journalEventReader == null) {
                throw new IllegalStateException("Cannot reconcile stream without a journal reader");
            }
            long durableNext = journalEventReader.getLastEventByStream(streamId)
                    .map(event -> Math.addExact(event.getStreamSequence(), 1L))
                    .orElse(1L);
            nextSequence = Math.max(nextSequence, durableNext);
            pendingConfirmation = false;
            uncertainWriteOutcome = false;
        }
        return getAuditEntryJournalEvent(payload, JournalEventType.CHANGE_STATE);
    }

    /**
     * Marks a write outcome as uncertain. The next allocation rereads the durable tail before issuing a position.
     * Do not confirm an uncertain write: only the caller can confirm a known successful commit.
     */
    public synchronized void markWriteOutcomeUncertain() {
        uncertainWriteOutcome = true;
        pendingConfirmation = false;
    }

    /**
     * Marks the position handed out by the last {@link #newEvent} as durably written, moving the stream on.
     * A no-op if nothing is outstanding.
     */
    public synchronized void confirm() {
        if (pendingConfirmation && !uncertainWriteOutcome) {
            nextSequence++;
            pendingConfirmation = false;
        }
    }

    @NotNull
    private JournalEvent<AuditEntry> getAuditEntryJournalEvent(AuditEntry payload, JournalEventType type) {
        pendingConfirmation = true;
        return new JournalEvent<>(
                UUID.randomUUID().toString(),   // eventId
                deriveIdempotencyKey(type, payload),
                type,
                streamId,
                nextSequence,                   // spent only on confirm(), so a failed write leaves no gap
                Instant.now(),                  // occurredAt
                payload);
    }

    private String deriveIdempotencyKey(JournalEventType eventType, AuditEntry payload) {
        if (eventType != JournalEventType.CHANGE_STATE) {
            throw new UnsupportedOperationException("No idempotency-key derivation is defined for " + eventType);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateCanonicalField(digest, streamId);
            updateCanonicalField(digest, payload.getExecutionId());
            updateCanonicalField(digest, payload.getChangeId());
            updateCanonicalField(digest, payload.getState() == null ? null : payload.getState().name());
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateCanonicalField(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

}
