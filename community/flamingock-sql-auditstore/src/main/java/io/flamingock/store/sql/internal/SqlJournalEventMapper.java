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
 * Maps the supported SQL Journal Event envelope and its flattened audit payload.
 */
final class SqlJournalEventMapper {

    private final int nullableBooleanType;

    SqlJournalEventMapper() {
        this(SqlDialect.H2);
    }

    SqlJournalEventMapper(SqlDialect sqlDialect) {
        nullableBooleanType = new SqlJournalDialectHelper(sqlDialect).getBooleanJdbcType();
    }

    void bind(PreparedStatement statement, JournalEvent<AuditEntry> event) throws SQLException {
        requireSupportedEvent(event);
        statement.setString(1, event.getEventId());
        statement.setString(2, event.getEventType().name());
        statement.setInt(3, event.getEventVersion());
        statement.setString(4, event.getStreamId());
        statement.setLong(5, event.getStreamSequence());
        statement.setTimestamp(6, Timestamp.from(event.getOccurredAt()));
        statement.setBoolean(7, event.isAcknowledged());
        AuditEntryMapper.bind(statement, event.getData(), 8, nullableBooleanType);
    }

    JournalEvent<AuditEntry> fromResultSet(ResultSet resultSet) throws SQLException {
        JournalEventType eventType = JournalEventType.valueOf(
                resultSet.getString(JournalEventConstants.EVENT_TYPE));
        if (eventType != JournalEventType.CHANGE_STATE) {
            throw new UnsupportedOperationException("Unsupported SQL Journal Event type: " + eventType);
        }

        Timestamp occurredAt = resultSet.getTimestamp(JournalEventConstants.OCCURRED_AT);
        if (occurredAt == null) {
            throw new SQLException("Journal event occurred_at must not be null");
        }

        return new JournalEvent<>(
                resultSet.getString(JournalEventConstants.EVENT_ID),
                eventType,
                resultSet.getInt(JournalEventConstants.EVENT_VERSION),
                resultSet.getString(JournalEventConstants.STREAM_ID),
                resultSet.getLong(JournalEventConstants.STREAM_SEQUENCE),
                occurredAt.toInstant(),
                AuditEntryMapper.fromResultSet(resultSet),
                resultSet.getBoolean(JournalEventConstants.ACKNOWLEDGED));
    }

    private static void requireSupportedEvent(JournalEvent<AuditEntry> event) {
        if (event == null || event.getEventType() != JournalEventType.CHANGE_STATE
                || !(event.getData() instanceof AuditEntry)) {
            throw new UnsupportedOperationException("SQL Journal Events support CHANGE_STATE with AuditEntry payloads only");
        }
    }
}
