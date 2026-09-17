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

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Binds and reads the typed, flattened SQL representation of an {@link AuditEntry}.
 */
final class AuditEntryMapper {

    private static final List<String> COLUMN_NAMES = Collections.unmodifiableList(Arrays.asList(
            "execution_id", "stage_id", "change_id", "author", "created_at", "state", "invoked_class",
            "invoked_method", "source_file", "metadata", "execution_millis", "execution_hostname",
            "error_trace", "type", "tx_strategy", "target_system_id", "change_order", "recovery_strategy",
            "transaction_flag", "system_change"));

    private AuditEntryMapper() {
    }

    static List<String> columnNames() {
        return COLUMN_NAMES;
    }

    static void bind(PreparedStatement statement, AuditEntry auditEntry, int firstColumn) throws SQLException {
        bind(statement, auditEntry, firstColumn, Types.BOOLEAN);
    }

    static void bind(PreparedStatement statement,
                     AuditEntry auditEntry,
                     int firstColumn,
                     int nullableBooleanType) throws SQLException {
        int column = firstColumn;
        statement.setString(column++, auditEntry.getExecutionId());
        statement.setString(column++, auditEntry.getStageId());
        statement.setString(column++, auditEntry.getChangeId());
        statement.setString(column++, auditEntry.getAuthor());
        if (auditEntry.getCreatedAt() == null) {
            statement.setNull(column++, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(column++, Timestamp.valueOf(auditEntry.getCreatedAt()));
        }
        statement.setString(column++, auditEntry.getState() == null ? null : auditEntry.getState().name());
        statement.setString(column++, auditEntry.getClassName());
        statement.setString(column++, auditEntry.getMethodName());
        statement.setString(column++, auditEntry.getSourceFile());
        statement.setString(column++, auditEntry.getMetadata() == null ? null : auditEntry.getMetadata().toString());
        statement.setLong(column++, auditEntry.getExecutionMillis());
        statement.setString(column++, auditEntry.getExecutionHostname());
        statement.setString(column++, auditEntry.getErrorTrace());
        statement.setString(column++, auditEntry.getType() == null ? null : auditEntry.getType().name());
        statement.setString(column++, auditEntry.getTxType() == null ? null : auditEntry.getTxType().name());
        statement.setString(column++, auditEntry.getTargetSystemId());
        statement.setString(column++, auditEntry.getOrder());
        statement.setString(column++, auditEntry.getRecoveryStrategy() == null ? null : auditEntry.getRecoveryStrategy().name());
        setNullableBoolean(statement, column++, auditEntry.getTransactionFlag(), nullableBooleanType);
        setNullableBoolean(statement, column, auditEntry.getSystemChange(), nullableBooleanType);
    }

    static AuditEntry fromResultSet(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp(columnName(4));
        return new AuditEntry(
                resultSet.getString(columnName(0)),
                resultSet.getString(columnName(1)),
                resultSet.getString(columnName(2)),
                resultSet.getString(columnName(3)),
                createdAt == null ? null : createdAt.toLocalDateTime(),
                enumValue(AuditEntry.Status.class, resultSet.getString(columnName(5))),
                enumValue(AuditEntry.ChangeType.class, resultSet.getString(columnName(13))),
                resultSet.getString(columnName(6)),
                resultSet.getString(columnName(7)),
                resultSet.getString(columnName(8)),
                resultSet.getLong(columnName(10)),
                resultSet.getString(columnName(11)),
                resultSet.getString(columnName(9)),
                readBoolean(resultSet, columnName(19)),
                resultSet.getString(columnName(12)),
                AuditTxType.fromString(resultSet.getString(columnName(14))),
                resultSet.getString(columnName(15)),
                resultSet.getString(columnName(16)),
                enumValue(RecoveryStrategy.class, resultSet.getString(columnName(17))),
                readNullableBoolean(resultSet, columnName(18)));
    }

    private static String columnName(int index) {
        return COLUMN_NAMES.get(index);
    }

    private static void setNullableBoolean(PreparedStatement statement,
                                            int column,
                                            Boolean value,
                                            int nullableBooleanType) throws SQLException {
        if (value == null) {
            statement.setNull(column, nullableBooleanType);
        } else {
            statement.setBoolean(column, value);
        }
    }

    private static boolean readBoolean(ResultSet resultSet, String column) throws SQLException {
        Boolean value = readNullableBoolean(resultSet, column);
        return value != null && value;
    }

    private static Boolean readNullableBoolean(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return Boolean.valueOf(value.toString());
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
