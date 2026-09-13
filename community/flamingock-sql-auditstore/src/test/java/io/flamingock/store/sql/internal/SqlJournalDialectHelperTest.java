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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlJournalDialectHelperTest {

    private static final String TABLE_NAME = "flamingockJournalEvents";

    @ParameterizedTest(name = "{0} journal schema is typed and portable")
    @EnumSource(SqlDialect.class)
    @DisplayName("generates the journal schema and indexes for every supported SQL dialect")
    void generatesTypedSchemaAndIndexesForEveryDialect(SqlDialect dialect) {
        SqlJournalDialectHelper helper = new SqlJournalDialectHelper(dialect);
        String ddl = helper.getCreateTableSqlString(TABLE_NAME).toUpperCase();
        List<String> indexSql = helper.getCreateIndexSqlStrings(TABLE_NAME);

        assertTrue(ddl.contains("EVENT_ID"));
        assertTrue(ddl.contains("EVENT_TYPE"));
        assertTrue(ddl.contains("EVENT_VERSION"));
        assertTrue(ddl.contains("STREAM_ID"));
        assertTrue(ddl.contains("STREAM_SEQUENCE"));
        assertTrue(ddl.contains("OCCURRED_AT"));
        assertTrue(ddl.contains("ACKNOWLEDGED"));
        assertTrue(ddl.contains("CREATED_AT"));
        assertTrue(ddl.contains("PRIMARY KEY"));
        assertFalse(ddl.contains("JSON"), "journal payloads must not use JSON columns");
        assertFalse(ddl.contains("CLOB"), "journal payloads must not use CLOB columns");

        assertEquals(2, countOccurrences(ddl, "STREAM_ID"),
                "stream_id must appear as a column and as both composite-key references");
        assertEquals(2, indexSql.size(), "pending and event-id indexes complement the composite primary key");
        assertTrue(indexSql.stream().allMatch(sql -> sql.toUpperCase().contains("CREATE INDEX")));
        assertNotNull(helper.getSqlDialect());
        assertTrue(helper.getIndexNames(TABLE_NAME).stream()
                .allMatch(name -> name.length() <= helper.getMaximumIndexNameLength()));

        List<String> definitionNames = columnNames(helper.getColumnDefinitions());
        assertTrue(Arrays.asList("event_id", "stream_id", "stream_sequence", "occurred_at", "acknowledged")
                .stream().allMatch(definitionNames::contains));
        assertEquals(definitionNames, insertColumnNames(helper.getInsertSqlString(TABLE_NAME)));
    }

    @Test
    @DisplayName("keeps Journal schema names separate from the ordered audit payload names")
    void keepsMinimalNameOwnershipBoundaries() throws Exception {
        List<String> expectedAuditColumns = Arrays.asList(
                "execution_id", "stage_id", "change_id", "author", "created_at", "state",
                "invoked_class", "invoked_method", "source_file", "metadata", "execution_millis",
                "execution_hostname", "error_trace", "type", "tx_strategy", "target_system_id",
                "change_order", "recovery_strategy", "transaction_flag", "system_change");

        assertEquals(expectedAuditColumns, AuditEntryMapper.columnNames());
        assertEquals(20, AuditEntryMapper.columnNames().size());
        assertThrows(UnsupportedOperationException.class,
                () -> AuditEntryMapper.columnNames().add("unexpected_column"));
        assertFalse(Arrays.stream(AuditEntryMapper.class.getDeclaredFields())
                .anyMatch(field -> expectedAuditColumns.contains(field.getName().toLowerCase(Locale.ROOT))));

        assertFalse(Arrays.stream(JournalEventConstants.class.getDeclaredFields())
                .anyMatch(field -> expectedAuditColumns.contains(field.getName().toLowerCase(Locale.ROOT))));
        assertFalse(Modifier.isPublic(JournalEventConstants.class.getModifiers()));
        assertTrue(Modifier.isPublic(SqlJournalDialectHelper.class.getModifiers()));
        assertTrue(Modifier.isFinal(SqlJournalDialectHelper.class.getModifiers()));
        assertClassIsAbsent("io.flamingock.store.sql.internal.SqlAuditColumnConstants");
        assertClassIsAbsent("io.flamingock.store.sql.internal.JournalEventPersistenceConstants");
    }

    @ParameterizedTest(name = "{0} uses the exact journal type policy")
    @EnumSource(SqlDialect.class)
    @DisplayName("uses exact portable types and capacities")
    void usesExactPortableTypes(SqlDialect dialect) {
        SqlJournalDialectHelper helper = new SqlJournalDialectHelper(dialect);
        String ddl = helper.getCreateTableSqlString(TABLE_NAME).toUpperCase(Locale.ROOT);

        assertTrue(ddl.contains("EVENT_ID " + varcharType(dialect, 255) + " NOT NULL"));
        assertTrue(ddl.contains("EVENT_TYPE " + varcharType(dialect, 32) + " NOT NULL"));
        assertTrue(ddl.contains("EVENT_VERSION INTEGER NOT NULL"));
        assertTrue(ddl.contains("STREAM_ID " + varcharType(dialect, 255) + " NOT NULL"));
        assertTrue(ddl.contains("STREAM_SEQUENCE " + longType(dialect) + " NOT NULL"));
        assertTrue(ddl.contains("OCCURRED_AT " + timestampType(dialect) + " NOT NULL"));
        assertTrue(ddl.contains("ACKNOWLEDGED " + booleanType(dialect) + " NOT NULL"));
        assertTrue(ddl.contains("PRIMARY KEY (STREAM_ID, STREAM_SEQUENCE)"));
        assertTrue(ddl.contains("METADATA " + textType(dialect)));
        assertTrue(ddl.contains("ERROR_TRACE " + textType(dialect)));
        assertFalse(ddl.contains("CLOB"));
        assertTrue(ddl.contains("TRANSACTION_FLAG " + booleanType(dialect)));
        assertTrue(ddl.contains("SYSTEM_CHANGE " + booleanType(dialect)));
    }

    @Test
    @DisplayName("keeps the required typed Journal definitions and text capacity policy")
    void keepsTypedColumnDefinitionsAndTextCapacity() {
        SqlJournalDialectHelper helper = new SqlJournalDialectHelper(SqlDialect.H2);

        assertEquals(Arrays.asList(
                "event_id", "event_type", "event_version", "stream_id", "stream_sequence", "occurred_at",
                "acknowledged", "execution_id", "stage_id", "change_id", "author", "created_at", "state",
                "invoked_class", "invoked_method", "source_file", "metadata", "execution_millis",
                "execution_hostname", "error_trace", "type", "tx_strategy", "target_system_id",
                "change_order", "recovery_strategy", "transaction_flag", "system_change"),
                columnNames(helper.getColumnDefinitions()));
        assertEquals(27, helper.getColumnDefinitions().size());
        assertEquals(SqlJournalDialectHelper.ColumnType.TEXT, helper.getColumnDefinitions().get(16).type);
        assertEquals(2048, helper.getColumnDefinitions().get(16).size);
        assertEquals(SqlJournalDialectHelper.ColumnType.TEXT, helper.getColumnDefinitions().get(19).type);
        assertEquals(2048, helper.getColumnDefinitions().get(19).size);
        assertTrue(helper.getColumnDefinitions().get(25).nullable);
        assertTrue(helper.getColumnDefinitions().get(26).nullable);
    }

    private static List<String> columnNames(List<SqlJournalDialectHelper.ColumnDefinition> definitions) {
        List<String> names = new java.util.ArrayList<>();
        for (SqlJournalDialectHelper.ColumnDefinition definition : definitions) {
            names.add(definition.name);
        }
        return names;
    }

    private static List<String> insertColumnNames(String insertSql) {
        int start = insertSql.indexOf('(') + 1;
        int end = insertSql.indexOf(") VALUES");
        return Arrays.asList(insertSql.substring(start, end).split(", "));
    }

    @Test
    @DisplayName("derives deterministic table-scoped index names within dialect limits")
    void derivesDeterministicTableScopedIndexNames() {
        SqlJournalDialectHelper helper = new SqlJournalDialectHelper(SqlDialect.ORACLE);
        String tableName = "journalEventsWithAnIntentionallyVeryLongTableNameForOracle";

        List<String> first = helper.getIndexNames(tableName);
        List<String> second = helper.getIndexNames(tableName);

        assertEquals(first, second);
        assertEquals(2, first.stream().distinct().count());
        assertTrue(first.stream().allMatch(name -> name.length() <= 30));
        assertTrue(first.stream().allMatch(name -> name.startsWith("idx_")));
        assertTrue(helper.getCreateIndexSqlStrings(tableName).stream()
                .allMatch(sql -> first.stream().anyMatch(sql::contains)));

        String shortTableName = "customJournalEvents";
        assertEquals(Arrays.asList(
                        "idx_customJournalEvents_pending_events",
                        "idx_customJournalEvents_event_id"),
                new SqlJournalDialectHelper(SqlDialect.H2).getIndexNames(shortTableName));
    }

    private static void assertClassIsAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }

    private static String varcharType(SqlDialect dialect, int size) {
        return (dialect == SqlDialect.ORACLE ? "VARCHAR2(" : "VARCHAR(") + size + ")";
    }

    private static String longType(SqlDialect dialect) {
        return dialect == SqlDialect.ORACLE ? "NUMBER(19)" : "BIGINT";
    }

    private static String timestampType(SqlDialect dialect) {
        if (dialect == SqlDialect.SQLSERVER || dialect == SqlDialect.SYBASE) {
            return "DATETIME";
        }
        if (dialect == SqlDialect.INFORMIX) {
            return "DATETIME YEAR TO FRACTION(3)";
        }
        return "TIMESTAMP";
    }

    private static String booleanType(SqlDialect dialect) {
        switch (dialect) {
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

    private static String textType(SqlDialect dialect) {
        switch (dialect) {
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

    private static int countOccurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
