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
package io.flamingock.store.sql.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.common.sql.SqlDialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Maps the supported SQL Journal Event envelope and its local JSON audit payload.
 */
final class SqlJournalEventMapper {

    SqlJournalEventMapper() {
    }

    SqlJournalEventMapper(SqlDialect sqlDialect) {
        if (sqlDialect == null) {
            throw new IllegalArgumentException("sqlDialect must not be null");
        }
    }

    void bind(PreparedStatement statement, JournalEvent<AuditEntry> event) throws SQLException {
        requireSupportedEvent(event);
        statement.setString(1, event.getEventId());
        statement.setString(2, event.getIdempotencyKey());
        statement.setString(3, event.getEventType().name());
        statement.setInt(4, event.getEventVersion());
        statement.setString(5, event.getStreamId());
        statement.setLong(6, event.getStreamSequence());
        statement.setTimestamp(7, Timestamp.from(event.getOccurredAt()));
        statement.setBoolean(8, event.isAcknowledged());
        statement.setString(9, SqlJournalPayloadCodec.serialize(event.getData()));
    }

    JournalEvent<AuditEntry> fromResultSet(ResultSet resultSet) throws SQLException {
        String eventTypeName = resultSet.getString(JournalEventConstants.EVENT_TYPE);
        JournalEventType eventType = parseEventType(eventTypeName);
        requireSupportedEventType(eventType);

        Timestamp occurredAt = resultSet.getTimestamp(JournalEventConstants.OCCURRED_AT);
        if (occurredAt == null) {
            throw new SQLException("Journal event occurred_at must not be null");
        }
        String payload = resultSet.getString(JournalEventConstants.PAYLOAD);
        if (payload == null) {
            throw new SQLException("Journal event payload must not be null");
        }

        return new JournalEvent<>(
                resultSet.getString(JournalEventConstants.EVENT_ID),
                resultSet.getString(JournalEventConstants.IDEMPOTENCY_KEY),
                eventType,
                resultSet.getInt(JournalEventConstants.EVENT_VERSION),
                resultSet.getString(JournalEventConstants.STREAM_ID),
                resultSet.getLong(JournalEventConstants.STREAM_SEQUENCE),
                occurredAt.toInstant(),
                SqlJournalPayloadCodec.deserialize(payload),
                resultSet.getBoolean(JournalEventConstants.ACKNOWLEDGED));
    }

    private static void requireSupportedEvent(JournalEvent<AuditEntry> event) {
        if (event == null) {
            throw new UnsupportedOperationException("SQL Journal Events support CHANGE_STATE with AuditEntry payloads only");
        }
        requireSupportedEventType(event.getEventType());
        if (!(event.getData() instanceof AuditEntry)) {
            throw new UnsupportedOperationException("SQL Journal Events support CHANGE_STATE with AuditEntry payloads only");
        }
    }

    private static void requireSupportedEventType(JournalEventType eventType) {
        if (eventType != JournalEventType.CHANGE_STATE) {
            throw new UnsupportedOperationException("Unsupported SQL Journal Event type: " + eventType);
        }
    }

    private static JournalEventType parseEventType(String eventTypeName) {
        try {
            return JournalEventType.valueOf(eventTypeName);
        } catch (RuntimeException exception) {
            throw new UnsupportedOperationException("Unsupported SQL Journal Event type: " + eventTypeName, exception);
        }
    }
}
