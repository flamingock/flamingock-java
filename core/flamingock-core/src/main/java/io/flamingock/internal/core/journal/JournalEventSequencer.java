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
 * If a caller forgets to confirm a write that did land, the reused position collides with the unique
 * {@code (streamId, streamSequence)} index — a loud failure rather than a silent duplicate.
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

    @NotNull
    private JournalEvent<AuditEntry> getAuditEntryJournalEvent(AuditEntry payload, JournalEventType type) {
        pendingConfirmation = true;
        return new JournalEvent<>(
                UUID.randomUUID().toString(),   // eventId
                type,
                streamId,
                nextSequence,                   // spent only on confirm(), so a failed write leaves no gap
                Instant.now(),                  // occurredAt
                payload);
    }

}
