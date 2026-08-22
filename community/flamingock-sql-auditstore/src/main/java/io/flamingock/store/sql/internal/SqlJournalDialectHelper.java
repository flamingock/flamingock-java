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

import io.flamingock.internal.common.sql.SqlDialect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Provides portable Journal Event SQL without relying on vendor-specific upsert or pagination syntax.
 */
public final class SqlJournalDialectHelper {

    private static final String INDEX_PREFIX = "idx_";
    private static final int INDEX_HASH_LENGTH = 8;

    private final SqlDialect sqlDialect;

    public SqlJournalDialectHelper(SqlDialect sqlDialect) {
        if (sqlDialect == null) {
            throw new IllegalArgumentException("sqlDialect must not be null");
        }
        this.sqlDialect = sqlDialect;
    }

    public SqlDialect getSqlDialect() {
        return sqlDialect;
    }

    int getMaximumIndexNameLength() {
        switch (sqlDialect) {
            case ORACLE:
                return 30;
            case POSTGRESQL:
                return 63;
            default:
                return 128;
        }
    }

    List<String> getIndexNames(String tableName) {
        JournalEventConstants.validateIdentifier(tableName, "tableName");
        return Collections.unmodifiableList(Arrays.asList(
                indexName(tableName, JournalEventConstants.PENDING_EVENTS_INDEX),
                indexName(tableName, JournalEventConstants.EVENT_ID_INDEX)));
    }

    List<ColumnDefinition> getColumnDefinitions() {
        List<String> auditColumnNames = AuditEntryMapper.columnNames();
        return Collections.unmodifiableList(Arrays.asList(
                new ColumnDefinition(JournalEventConstants.EVENT_ID, ColumnType.VARCHAR, 255, false),
                new ColumnDefinition(JournalEventConstants.EVENT_TYPE, ColumnType.VARCHAR, 32, false),
                new ColumnDefinition(JournalEventConstants.EVENT_VERSION, ColumnType.INTEGER, 0, false),
                new ColumnDefinition(JournalEventConstants.STREAM_ID, ColumnType.VARCHAR, 255, false),
                new ColumnDefinition(JournalEventConstants.STREAM_SEQUENCE, ColumnType.LONG, 19, false),
                new ColumnDefinition(JournalEventConstants.OCCURRED_AT, ColumnType.TIMESTAMP, 0, false),
                new ColumnDefinition(JournalEventConstants.ACKNOWLEDGED, ColumnType.BOOLEAN, 0, false),
                new ColumnDefinition(auditColumnNames.get(0), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(1), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(2), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(3), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(4), ColumnType.TIMESTAMP, 0, true),
                new ColumnDefinition(auditColumnNames.get(5), ColumnType.VARCHAR, 64, true),
                new ColumnDefinition(auditColumnNames.get(6), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(7), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(8), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(9), ColumnType.TEXT, 2048, true),
                new ColumnDefinition(auditColumnNames.get(10), ColumnType.LONG, 19, true),
                new ColumnDefinition(auditColumnNames.get(11), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(12), ColumnType.TEXT, 2048, true),
                new ColumnDefinition(auditColumnNames.get(13), ColumnType.VARCHAR, 64, true),
                new ColumnDefinition(auditColumnNames.get(14), ColumnType.VARCHAR, 64, true),
                new ColumnDefinition(auditColumnNames.get(15), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(16), ColumnType.VARCHAR, 255, true),
                new ColumnDefinition(auditColumnNames.get(17), ColumnType.VARCHAR, 64, true),
                new ColumnDefinition(auditColumnNames.get(18), ColumnType.BOOLEAN, 0, true),
                new ColumnDefinition(auditColumnNames.get(19), ColumnType.BOOLEAN, 0, true)));
    }

    int getBooleanJdbcType() {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
                return Types.TINYINT;
            case POSTGRESQL:
            case H2:
            case FIREBIRD:
            case INFORMIX:
                return Types.BOOLEAN;
            case SQLITE:
                return Types.INTEGER;
            case SQLSERVER:
            case SYBASE:
                return Types.BIT;
            case ORACLE:
                return Types.NUMERIC;
            case DB2:
            default:
                return Types.SMALLINT;
        }
    }

    public String getCreateTableSqlString(String tableName) {
        JournalEventConstants.validateIdentifier(tableName, "tableName");
        StringBuilder sql = new StringBuilder("CREATE TABLE ")
                .append(tableName)
                .append(" (");
        List<ColumnDefinition> definitions = getColumnDefinitions();
        for (int i = 0; i < definitions.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            ColumnDefinition definition = definitions.get(i);
            sql.append(definition.name)
                    .append(' ')
                    .append(sqlType(definition));
            if (!definition.nullable) {
                sql.append(" NOT NULL");
            }
        }
        return sql.append(", PRIMARY KEY (")
                .append(JournalEventConstants.STREAM_ID)
                .append(", ")
                .append(JournalEventConstants.STREAM_SEQUENCE)
                .append(")")
                .append(')')
                .toString();
    }

    public List<String> getCreateIndexSqlStrings(String tableName) {
        List<String> indexNames = getIndexNames(tableName);
        return Collections.unmodifiableList(Arrays.asList(
                String.format("CREATE INDEX %s ON %s (%s, %s, %s)",
                        indexNames.get(0), tableName, JournalEventConstants.ACKNOWLEDGED,
                        JournalEventConstants.STREAM_ID, JournalEventConstants.STREAM_SEQUENCE),
                String.format("CREATE INDEX %s ON %s (%s)",
                        indexNames.get(1), tableName, JournalEventConstants.EVENT_ID)));
    }

    public String getInsertSqlString(String tableName) {
        JournalEventConstants.validateIdentifier(tableName, "tableName");
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (ColumnDefinition definition : getColumnDefinitions()) {
            if (columns.length() > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(definition.name);
            placeholders.append("?");
        }
        return String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);
    }

    private String sqlType(ColumnDefinition definition) {
        switch (definition.type) {
            case VARCHAR:
                return getVarcharType(definition.size);
            case INTEGER:
                return "INTEGER";
            case LONG:
                return getLongType();
            case TIMESTAMP:
                return getTimestampType();
            case BOOLEAN:
                return getBooleanType();
            case TEXT:
                return getTextType();
            default:
                throw new IllegalArgumentException("Unsupported Journal column type: " + definition.type);
        }
    }

    public String getLastEventSqlString(String tableName) {
        JournalEventConstants.validateIdentifier(tableName, "tableName");
        return String.format(
                "SELECT * FROM %s WHERE stream_id = ? ORDER BY stream_sequence DESC",
                tableName);
    }

    public String getUnacknowledgedEventsSqlString(String tableName) {
        JournalEventConstants.validateIdentifier(tableName, "tableName");
        return String.format(
                "SELECT * FROM %s WHERE acknowledged = ? ORDER BY stream_id ASC, stream_sequence ASC",
                tableName);
    }

    public String getAcknowledgeSqlString(String tableName) {
        JournalEventConstants.validateIdentifier(tableName, "tableName");
        return String.format(
                "UPDATE %s SET acknowledged = ? WHERE event_id = ? AND acknowledged = ?",
                tableName);
    }

    private String indexName(String tableName, String suffix) {
        String naturalName = INDEX_PREFIX + tableName + "_" + suffix;
        int maximumLength = getMaximumIndexNameLength();
        if (naturalName.length() <= maximumLength) {
            return naturalName;
        }

        String hash = hash(tableName);
        int tableLength = maximumLength - INDEX_PREFIX.length() - suffix.length() - hash.length() - 2;
        if (tableLength < 1) {
            throw new IllegalArgumentException("Table name cannot produce a valid SQL index name");
        }
        return INDEX_PREFIX + tableName.substring(0, tableLength) + "_" + suffix + "_" + hash;
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(INDEX_HASH_LENGTH);
            for (int i = 0; i < INDEX_HASH_LENGTH / 2; i++) {
                result.append(String.format("%02x", digest[i]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String getVarcharType(int length) {
        return (sqlDialect == SqlDialect.ORACLE ? "VARCHAR2(" : "VARCHAR(") + length + ")";
    }

    private String getLongType() {
        return sqlDialect == SqlDialect.ORACLE ? "NUMBER(19)" : "BIGINT";
    }

    private String getTimestampType() {
        switch (sqlDialect) {
            case SQLSERVER:
            case SYBASE:
                return "DATETIME";
            case INFORMIX:
                return "DATETIME YEAR TO FRACTION(3)";
            default:
                return "TIMESTAMP";
        }
    }

    private String getTextType() {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
            case POSTGRESQL:
            case SQLSERVER:
            case SYBASE:
            case SQLITE:
                return "TEXT";
            case INFORMIX:
                return "LVARCHAR(2048)";
            case ORACLE:
                return "VARCHAR2(4000)";
            case DB2:
            case FIREBIRD:
            case H2:
            default:
                return "VARCHAR(4000)";
        }
    }

    private String getBooleanType() {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
                return "TINYINT(1)";
            case POSTGRESQL:
            case H2:
            case FIREBIRD:
            case INFORMIX:
                return "BOOLEAN";
            case SQLITE:
                return "INTEGER";
            case SQLSERVER:
            case SYBASE:
                return "BIT";
            case ORACLE:
                return "NUMBER(1)";
            case DB2:
            default:
                return "SMALLINT";
        }
    }

    enum ColumnType {
        VARCHAR,
        INTEGER,
        LONG,
        TIMESTAMP,
        BOOLEAN,
        TEXT
    }

    static final class ColumnDefinition {
        final String name;
        final ColumnType type;
        final int size;
        final boolean nullable;

        ColumnDefinition(String name, ColumnType type, int size, boolean nullable) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.nullable = nullable;
        }
    }
}
