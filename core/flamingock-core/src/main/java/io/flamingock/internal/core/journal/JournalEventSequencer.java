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

import java.time.Instant;
import java.util.UUID;

public class JournalEventSequencer {
    private final String streamId;
    private long nextSequence;

    JournalEventSequencer(String streamId, long initialSequence) {
        this.streamId = streamId;
        this.nextSequence = initialSequence;   // seeded from outside
    }

    public <T> JournalEvent<T> newEvent(T payload) {
        JournalEventType type = getType(payload);
        return new JournalEvent<>(
                UUID.randomUUID().toString(),   // eventId
                type,
                streamId,
                nextSequence++,                 // in-memory, safe: distributed lock covers it
                Instant.now(),                  // occurredAt
                payload);
    }

    private JournalEventType getType(Object payload) {
        if(payload instanceof AuditEntry) {
            return JournalEventType.CHANGE_STATE;
        }
        throw new IllegalArgumentException("Cannot process JournalEvent for type: " + payload.getClass().getName());
    }
}
