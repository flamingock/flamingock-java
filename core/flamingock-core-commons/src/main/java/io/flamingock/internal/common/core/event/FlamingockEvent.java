package io.flamingock.internal.common.core.event;


import java.time.Instant;
import java.util.Objects;

public final class FlamingockEvent<T> {

    public static final int DEFAULT_VERSION = 1;

    private final String eventId;
    private final FlamingockEventType eventType;
    private final int eventVersion;

    private final String streamId;
    private final long streamSequence;

    private final Instant occurredAt;
    private final T data;

    private boolean acknowledged;

    public FlamingockEvent(
            String eventId,
            FlamingockEventType eventType,
            String streamId,
            long streamSequence,
            Instant occurredAt,
            T data)  {
        this(eventId, eventType, DEFAULT_VERSION, streamId, streamSequence, occurredAt, data, false);
    }


    public FlamingockEvent(
            String eventId,
            FlamingockEventType eventType,
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
            throw new IllegalArgumentException(
                    "streamSequence must be greater than zero"
            );
        }

        this.eventVersion = eventVersion;
        this.streamSequence = streamSequence;
        this.occurredAt = Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
        );
        this.data = Objects.requireNonNull(
                data,
                "data must not be null"
        );
    }

    public String getEventId() {
        return eventId;
    }

    public FlamingockEventType getEventType() {
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

    private static String requireNotBlank(
            String value,
            String fieldName) {

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value;
    }

}
