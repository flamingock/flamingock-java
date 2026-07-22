
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
package io.flamingock.internal.common.core.journal;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable runtime fact produced by the Flamingock client .
 * <p>
 * The outer object is the event; {@code data} is the contained domain object (an audit entry, an
 * execution, …). Events are held in the durable local buffer.
 * <p>
 * This is a plain Java 8 POJO with no persistence or serialization annotations — each audit-store
 * adapter owns the conversion to its own representation.
 */
public final class JournalEvent<T> {

    public static final int DEFAULT_VERSION = 1;

    private final String eventId;
    private final JournalEventType eventType;
    private final int eventVersion;

    private final String streamId;
    private final long streamSequence;

    private final Instant occurredAt;
    private final T data;

    private boolean acknowledged;

    public JournalEvent(String eventId,
                        JournalEventType eventType,
                        String streamId,
                        long streamSequence,
                        Instant occurredAt,
                        T data) {
        this(eventId, eventType, DEFAULT_VERSION, streamId, streamSequence, occurredAt, data, false);
    }

    public JournalEvent(String eventId,
                        JournalEventType eventType,
                        int eventVersion,
                        String streamId,
                        long streamSequence,
                        Instant occurredAt,
                        T data,
                        boolean acknowledged) {
        this.acknowledged = acknowledged;
        this.eventId = requireNotBlank(eventId, "eventId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.streamId = requireNotBlank(streamId, "streamId");

        if (streamSequence < 1) {
            throw new IllegalArgumentException("streamSequence must be greater than zero");
        }

        this.eventVersion = eventVersion;
        this.streamSequence = streamSequence;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.data = Objects.requireNonNull(data, "data must not be null");
    }

    public String getEventId() {
        return eventId;
    }

    public JournalEventType getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getStreamId() {
        return streamId;
    }

    public long getStreamSequence() {
        return streamSequence;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public T getData() {
        return data;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void acknowledge() {
        this.acknowledged = true;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
