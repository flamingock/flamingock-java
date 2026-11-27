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
import io.flamingock.internal.common.core.journal.DuplicateStreamSequenceException;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Hands out stream positions for a single stream, in memory — safe because the stage lock guarantees one
 * writer per stream.
 * <p>
 * A position is only spent once the caller confirms the event was durably written. Until then the same
 * position is handed out again, so a write that fails and aborts leaves no hole in the stream. That matters
 * beyond tidiness: a contiguous {@code streamSequence} is what lets a consumer reconstruct order and tell
 * "still in flight" from "lost", so gaps must never be produced by ordinary failure handling.
 * <p>
 * A duplicate position can be resynchronized from the authoritative journal tail and retried once when
 * the backend has established that repeating the complete write attempt is safe.
 */
public class JournalEventSequencer {
    private final String streamId;
    private long nextSequence;
    private boolean pendingConfirmation;

    JournalEventSequencer(String streamId, long initialSequence) {
        this.streamId = streamId;
        this.nextSequence = initialSequence;   // seeded from outside
    }

    /**
     * Builds the next event <em>without</em> spending its stream position; call {@link #confirm()} once the
     * event is durably written.
     */
    public JournalEvent<AuditEntry> newEvent(AuditEntry payload) {
        return getAuditEntryJournalEvent(payload, JournalEventType.CHANGE_STATE);
    }

    /**
     * Marks the position handed out by the last {@link #newEvent} as durably written, moving the stream on.
     * A no-op if nothing is outstanding.
     */
    public void confirm() {
        if (pendingConfirmation) {
            nextSequence++;
            pendingConfirmation = false;
        }
    }

    /**
     * Runs a safely repeatable journal write and retries once after a corroborated pending-position collision.
     * The supplier must throw a direct {@link DuplicateStreamSequenceException} only when the attempt is
     * safe to repeat, and return only after its transaction, if any, has completed successfully. This method
     * does not confirm writes; the caller must still call {@link #confirm()} after durable success.
     *
     * @param readDurableTail reads the highest durable event for the stream only after a matching collision
     * @param write complete write attempt
     * @param <T> result type
     * @return the successful attempt's result
     */
    public <T> T retryOnceOnCollision(Function<String, Optional<JournalEvent<AuditEntry>>> readDurableTail,
                                      Supplier<T> write) {
        try {
            return write.get();
        } catch (DuplicateStreamSequenceException collision) {
            if (!pendingConfirmation || !Objects.equals(streamId, collision.getStreamId())
                    || nextSequence != collision.getStreamSequence()) {
                throw collision;
            }
            Optional<JournalEvent<AuditEntry>> tail;
            try {
                tail = readDurableTail.apply(streamId);
            } catch (RuntimeException readFailure) {
                if (readFailure != collision) {
                    collision.addSuppressed(readFailure);
                }
                throw collision;
            }
            if (!tail.isPresent() || !Objects.equals(streamId, tail.get().getStreamId())
                    || tail.get().getStreamSequence() < collision.getStreamSequence()
                    || tail.get().getStreamSequence() == Long.MAX_VALUE) {
                throw collision;
            }
            nextSequence = tail.get().getStreamSequence() + 1;
            pendingConfirmation = false;
        }
        return write.get();
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
