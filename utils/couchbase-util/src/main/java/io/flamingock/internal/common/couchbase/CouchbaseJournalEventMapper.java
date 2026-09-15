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
package io.flamingock.internal.common.couchbase;

import com.couchbase.client.java.json.JsonObject;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;

import java.time.Instant;

import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_ACKNOWLEDGED;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_DATA;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_EVENT_ID;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_EVENT_TYPE;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_EVENT_VERSION;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_OCCURRED_AT;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_STREAM_ID;
import static io.flamingock.internal.common.couchbase.journal.JournalEventFieldConstants.KEY_STREAM_SEQUENCE;

/**
 * Maps a {@link JournalEvent} carrying an {@link AuditEntry} payload to/from a Couchbase {@link JsonObject}.
 * <p>
 * The {@code data} payload is nested as a sub-object, delegating to {@link CouchbaseAuditMapper} so the audit
 * representation stays single-sourced. {@code occurredAt} is stored as epoch millis, matching how
 * {@link CouchbaseUtils#addFieldToDocument} already represents dates in this module.
 * <p>
 * Only {@link JournalEventType#CHANGE_STATE} events carry an {@link AuditEntry} payload today. Other event
 * types (e.g. {@link JournalEventType#EXECUTION_STATE}) carry different payloads and are not yet implemented,
 * so this mapper rejects them rather than silently mis-mapping their data as an audit entry.
 */
public class CouchbaseJournalEventMapper {

    /** The only event type whose {@code data} is an {@link AuditEntry} and is supported for now. */
    private static final JournalEventType SUPPORTED_EVENT_TYPE = JournalEventType.CHANGE_STATE;

    private final CouchbaseAuditMapper dataMapper = new CouchbaseAuditMapper();

    public JsonObject toDocument(JournalEvent<AuditEntry> event) {
        requireSupportedType(event.getEventType());
        JsonObject document = JsonObject.create();
        document.put(KEY_EVENT_ID, event.getEventId());
        document.put(KEY_EVENT_TYPE, event.getEventType().name());
        document.put(KEY_EVENT_VERSION, event.getEventVersion());
        document.put(KEY_STREAM_ID, event.getStreamId());
        document.put(KEY_STREAM_SEQUENCE, event.getStreamSequence());
        document.put(KEY_OCCURRED_AT, event.getOccurredAt().toEpochMilli());
        document.put(KEY_ACKNOWLEDGED, event.isAcknowledged());
        document.put(KEY_DATA, dataMapper.toDocument(event.getData()));
        return document;
    }

    public JournalEvent<AuditEntry> fromDocument(JsonObject document) {
        JournalEventType eventType = JournalEventType.valueOf(document.getString(KEY_EVENT_TYPE));
        requireSupportedType(eventType);
        AuditEntry data = dataMapper.fromDocument(document.getObject(KEY_DATA));
        Instant occurredAt = Instant.ofEpochMilli(document.getLong(KEY_OCCURRED_AT));
        return new JournalEvent<>(
                document.getString(KEY_EVENT_ID),
                eventType,
                document.getInt(KEY_EVENT_VERSION),
                document.getString(KEY_STREAM_ID),
                document.getLong(KEY_STREAM_SEQUENCE),
                occurredAt,
                data,
                document.getBoolean(KEY_ACKNOWLEDGED));
    }

    private static void requireSupportedType(JournalEventType eventType) {
        if (eventType != SUPPORTED_EVENT_TYPE) {
            throw new UnsupportedOperationException(
                    "CouchbaseJournalEventMapper only supports " + SUPPORTED_EVENT_TYPE + " events (AuditEntry payload); "
                            + "event type " + eventType + " is not yet implemented");
        }
    }
}
