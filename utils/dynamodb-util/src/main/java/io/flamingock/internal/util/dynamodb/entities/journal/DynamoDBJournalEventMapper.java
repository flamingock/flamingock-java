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
package io.flamingock.internal.util.dynamodb.entities.journal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.util.JsonObjectMapper;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;

import java.time.Instant;

/**
 * Maps a {@link JournalEvent} carrying an {@link AuditEntry} payload to/from a
 * {@link JournalEventEntity}.
 * <p>
 * The {@code data} payload is embedded as the JSON serialization of an {@link AuditEntryEntity},
 * so the audit representation stays single-sourced. {@code occurredAt} is stored as an ISO-8601
 * string. {@code pendingStreamId} is present while the event is unacknowledged and absent once
 * acknowledged.
 * <p>
 * Only {@link JournalEventType#CHANGE_STATE} events carry an {@link AuditEntry} payload today.
 * Other event types carry different payloads and are not yet implemented, so this mapper rejects
 * them rather than silently mis-mapping their data as an audit entry.
 */
public final class DynamoDBJournalEventMapper {

    /** The only event type whose {@code data} is an {@link AuditEntry} and is supported for now. */
    private static final JournalEventType SUPPORTED_EVENT_TYPE = JournalEventType.CHANGE_STATE;

    private DynamoDBJournalEventMapper() {
    }

    public static JournalEventEntity toEntity(JournalEvent<AuditEntry> event) {
        requireSupportedType(event.getEventType());
        JournalEventEntity entity = new JournalEventEntity();
        entity.setEventId(event.getEventId());
        entity.setEventType(event.getEventType().name());
        entity.setEventVersion(event.getEventVersion());
        entity.setStreamId(event.getStreamId());
        entity.setStreamSequence(event.getStreamSequence());
        entity.setOccurredAt(event.getOccurredAt().toString());
        entity.setPendingStreamId(event.isAcknowledged() ? null : event.getStreamId());
        entity.setPayload(serializePayload(event.getData()));
        return entity;
    }

    public static JournalEvent<AuditEntry> fromEntity(JournalEventEntity entity) {
        JournalEventType eventType = JournalEventType.valueOf(entity.getEventType());
        requireSupportedType(eventType);
        AuditEntry data = deserializePayload(entity.getPayload());
        Instant occurredAt = Instant.parse(entity.getOccurredAt());
        boolean acknowledged = entity.getPendingStreamId() == null;
        return new JournalEvent<>(
                entity.getEventId(),
                eventType,
                entity.getEventVersion() != null ? entity.getEventVersion() : JournalEvent.DEFAULT_VERSION,
                entity.getStreamId(),
                entity.getStreamSequence(),
                occurredAt,
                data,
                acknowledged);
    }

    private static String serializePayload(AuditEntry auditEntry) {
        try {
            return JsonObjectMapper.DEFAULT_INSTANCE.writeValueAsString(new AuditEntryEntity(auditEntry));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize journal event payload", e);
        }
    }

    private static AuditEntry deserializePayload(String payload) {
        try {
            return JsonObjectMapper.DEFAULT_INSTANCE.readValue(payload, AuditEntryEntity.class).toAuditEntry();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize journal event payload", e);
        }
    }

    private static void requireSupportedType(JournalEventType eventType) {
        if (eventType != SUPPORTED_EVENT_TYPE) {
            throw new UnsupportedOperationException(
                    "DynamoDBJournalEventMapper only supports " + SUPPORTED_EVENT_TYPE + " events (AuditEntry payload); "
                            + "event type " + eventType + " is not yet implemented");
        }
    }
}
