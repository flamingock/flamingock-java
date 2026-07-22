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
package io.flamingock.internal.common.mongodb;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import org.bson.Document;

import java.time.Instant;
import java.util.Date;

import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_ACKNOWLEDGED;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_DATA;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_EVENT_ID;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_EVENT_TYPE;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_EVENT_VERSION;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_OCCURRED_AT;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_STREAM_ID;
import static io.flamingock.internal.common.mongodb.event.EventFieldConstants.KEY_STREAM_SEQUENCE;

/**
 * Maps a {@link JournalEvent} carrying an {@link AuditEntry} payload to/from a MongoDB {@link Document}.
 * <p>
 * The {@code data} payload is nested as a sub-document, delegating to {@link MongoDBAuditMapper} so the
 * audit representation stays single-sourced. {@code occurredAt} is stored as a BSON {@link Date}
 * ({@code TimeUtil} has no {@link Instant} support), which is sufficient for the event buffer.
 * <p>
 * Only {@link JournalEventType#CHANGE_STATE} events carry an {@link AuditEntry} payload today. Other
 * event types (e.g. {@link JournalEventType#EXECUTION_STATE}) carry different payloads and are not yet
 * implemented, so this mapper rejects them rather than silently mis-mapping their data as an audit entry.
 */
public class MongoDBEventMapper {

    /** The only event type whose {@code data} is an {@link AuditEntry} and is supported for now. */
    private static final JournalEventType SUPPORTED_EVENT_TYPE = JournalEventType.CHANGE_STATE;

    private final MongoDBAuditMapper<MongoDBDocumentHelper> dataMapper =
            new MongoDBAuditMapper<>(() -> new MongoDBDocumentHelper(new Document()));

    public Document toDocument(JournalEvent<AuditEntry> event) {
        requireSupportedType(event.getEventType());
        Document document = new Document();
        document.append(KEY_EVENT_ID, event.getEventId());
        document.append(KEY_EVENT_TYPE, event.getEventType().name());
        document.append(KEY_EVENT_VERSION, event.getEventVersion());
        document.append(KEY_STREAM_ID, event.getStreamId());
        document.append(KEY_STREAM_SEQUENCE, event.getStreamSequence());
        document.append(KEY_OCCURRED_AT, Date.from(event.getOccurredAt()));
        document.append(KEY_ACKNOWLEDGED, event.isAcknowledged());
        document.append(KEY_DATA, dataMapper.toDocument(event.getData()).getDocument());
        return document;
    }

    public JournalEvent<AuditEntry> fromDocument(Document document) {
        JournalEventType eventType = JournalEventType.valueOf(document.getString(KEY_EVENT_TYPE));
        requireSupportedType(eventType);
        AuditEntry data = dataMapper.fromDocument(new MongoDBDocumentHelper(document.get(KEY_DATA, Document.class)));
        Instant occurredAt = ((Date) document.get(KEY_OCCURRED_AT)).toInstant();
        return new JournalEvent<>(
                document.getString(KEY_EVENT_ID),
                eventType,
                ((Number) document.get(KEY_EVENT_VERSION)).intValue(),
                document.getString(KEY_STREAM_ID),
                ((Number) document.get(KEY_STREAM_SEQUENCE)).longValue(),
                occurredAt,
                data,
                document.getBoolean(KEY_ACKNOWLEDGED, false));
    }

    private static void requireSupportedType(JournalEventType eventType) {
        if (eventType != SUPPORTED_EVENT_TYPE) {
            throw new UnsupportedOperationException(
                    "MongoDBEventMapper only supports " + SUPPORTED_EVENT_TYPE + " events (AuditEntry payload); "
                            + "event type " + eventType + " is not yet implemented");
        }
    }
}
