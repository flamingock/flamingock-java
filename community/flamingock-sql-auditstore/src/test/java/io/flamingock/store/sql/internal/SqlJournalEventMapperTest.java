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
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.common.sql.SqlDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlJournalEventMapperTest {

    private static final String TABLE_NAME = "flamingockJournalEvents";

    @Test
    @DisplayName("round-trips the typed CHANGE_STATE envelope and flattened AuditEntry payload")
    void roundTripsTypedChangeStateEvent() throws Exception {
        AuditEntry auditEntry = auditEntry();
        Instant occurredAt = Instant.parse("2026-08-11T10:20:30.123456Z");
        JournalEvent<AuditEntry> source = new JournalEvent<>(
                "event-1", JournalEventType.CHANGE_STATE, 3, "stage-1", 7L, occurredAt, auditEntry, false);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:journal_mapper;DB_CLOSE_DELAY=-1")) {
            SqlJournalDialectHelper dialectHelper = new SqlJournalDialectHelper(SqlDialect.H2);
            connection.createStatement().execute(dialectHelper.getCreateTableSqlString(TABLE_NAME));
            for (String indexSql : dialectHelper.getCreateIndexSqlStrings(TABLE_NAME)) {
                connection.createStatement().execute(indexSql);
            }

            try (PreparedStatement insert = connection.prepareStatement(dialectHelper.getInsertSqlString(TABLE_NAME))) {
                new SqlJournalEventMapper().bind(insert, source);
                insert.executeUpdate();
            }

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT * FROM " + TABLE_NAME + " WHERE event_id = ?")) {
                select.setString(1, source.getEventId());
                try (ResultSet resultSet = select.executeQuery()) {
                    assertTrue(resultSet.next(), "the mapper must write a row");
                    JournalEvent<AuditEntry> actual = new SqlJournalEventMapper().fromResultSet(resultSet);

                    assertEquals(source.getEventId(), actual.getEventId());
                    assertEquals(source.getEventType(), actual.getEventType());
                    assertEquals(source.getEventVersion(), actual.getEventVersion());
                    assertEquals(source.getStreamId(), actual.getStreamId());
                    assertEquals(source.getStreamSequence(), actual.getStreamSequence());
                    assertEquals(source.getOccurredAt(), actual.getOccurredAt());
                    assertFalse(actual.isAcknowledged());
                    assertAuditEntryEquals(auditEntry, actual.getData());
                }
            }
        }
    }

    @Test
    @DisplayName("rejects event types whose payload mapping is not implemented")
    void rejectsUnsupportedEventType() throws Exception {
        JournalEvent<AuditEntry> unsupported = new JournalEvent<>(
                "event-unsupported", JournalEventType.EXECUTION_STATE, "stage-1", 1L, Instant.now(), auditEntry());

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:journal_mapper_unsupported;DB_CLOSE_DELAY=-1");
             PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            assertThrows(UnsupportedOperationException.class,
                    () -> new SqlJournalEventMapper().bind(statement, unsupported));
        }
    }

    @Test
    @DisplayName("round-trips an acknowledged event and nullable audit fields")
    void roundTripsAcknowledgedEventAndNullableFields() throws Exception {
        AuditEntry auditEntry = new AuditEntry(
                "execution-nullable", "stage-nullable", "change-nullable", null,
                null, null, null,
                null, null, null, 0L, null, null, false, null, null,
                null, null, null, null);
        JournalEvent<AuditEntry> source = new JournalEvent<>(
                "event-acknowledged", JournalEventType.CHANGE_STATE, JournalEvent.DEFAULT_VERSION,
                "stage-nullable", 2L,
                Instant.parse("2026-08-11T10:20:30Z"), auditEntry, true);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:journal_mapper_nullable;DB_CLOSE_DELAY=-1")) {
            SqlJournalDialectHelper dialectHelper = new SqlJournalDialectHelper(SqlDialect.H2);
            connection.createStatement().execute(dialectHelper.getCreateTableSqlString(TABLE_NAME));
            try (PreparedStatement insert = connection.prepareStatement(dialectHelper.getInsertSqlString(TABLE_NAME))) {
                new SqlJournalEventMapper().bind(insert, source);
                insert.executeUpdate();
            }
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT * FROM " + TABLE_NAME)) {
                assertTrue(resultSet.next());
                JournalEvent<AuditEntry> actual = new SqlJournalEventMapper().fromResultSet(resultSet);

                assertTrue(actual.isAcknowledged());
                assertEquals(source.getEventVersion(), actual.getEventVersion());
                assertEquals(source.getData().getExecutionId(), actual.getData().getExecutionId());
                assertNull(actual.getData().getCreatedAt());
                assertEquals(source.getData().getTxType(), actual.getData().getTxType());
                assertEquals(source.getData().getRecoveryStrategy(), actual.getData().getRecoveryStrategy());
                assertEquals(source.getData().getTransactionFlag(), actual.getData().getTransactionFlag());
                assertEquals(source.getData().getMetadata(), actual.getData().getMetadata());
            }
        }
    }

    @Test
    @DisplayName("keeps event occurrence time separate from the audit creation time")
    void keepsEnvelopeAndPayloadTimesDistinct() throws Exception {
        AuditEntry auditEntry = auditEntry();
        JournalEvent<AuditEntry> source = new JournalEvent<>(
                "event-time", JournalEventType.CHANGE_STATE, "stage-time", 1L,
                Instant.parse("2026-08-11T12:00:00Z"), auditEntry);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:journal_mapper_times;DB_CLOSE_DELAY=-1")) {
            SqlJournalDialectHelper dialectHelper = new SqlJournalDialectHelper(SqlDialect.H2);
            connection.createStatement().execute(dialectHelper.getCreateTableSqlString(TABLE_NAME));
            try (PreparedStatement insert = connection.prepareStatement(dialectHelper.getInsertSqlString(TABLE_NAME))) {
                new SqlJournalEventMapper().bind(insert, source);
                insert.executeUpdate();
            }
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT occurred_at, created_at FROM " + TABLE_NAME)) {
                assertTrue(resultSet.next());
                assertFalse(resultSet.getTimestamp("occurred_at").toLocalDateTime()
                        .equals(resultSet.getTimestamp("created_at").toLocalDateTime()),
                        "the envelope and payload timestamps must be stored independently");
            }
        }
    }

    @Test
    @DisplayName("enforces stream position uniqueness without requiring globally unique event IDs")
    void enforcesCompositeStreamPositionAndAllowsDuplicateEventIds() throws Exception {
        JournalEvent<AuditEntry> first = new JournalEvent<>(
                "event-shared", JournalEventType.CHANGE_STATE, "stage-1", 1L, Instant.now(), auditEntry());
        JournalEvent<AuditEntry> otherStream = new JournalEvent<>(
                "event-shared", JournalEventType.CHANGE_STATE, "stage-2", 1L, Instant.now(), auditEntry());
        JournalEvent<AuditEntry> collidingPosition = new JournalEvent<>(
                "event-other", JournalEventType.CHANGE_STATE, "stage-1", 1L, Instant.now(), auditEntry());

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:journal_mapper_identity;DB_CLOSE_DELAY=-1")) {
            SqlJournalDialectHelper dialectHelper = new SqlJournalDialectHelper(SqlDialect.H2);
            connection.createStatement().execute(dialectHelper.getCreateTableSqlString(TABLE_NAME));
            for (String indexSql : dialectHelper.getCreateIndexSqlStrings(TABLE_NAME)) {
                connection.createStatement().execute(indexSql);
            }

            insert(connection, dialectHelper, first);
            insert(connection, dialectHelper, otherStream);

            assertThrows(Exception.class, () -> insert(connection, dialectHelper, collidingPosition));

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {
                assertTrue(resultSet.next());
                assertEquals(2, resultSet.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("keeps transaction handles package-private")
    void keepsTransactionHandlesPackagePrivate() throws Exception {
        Method append = SqlJournalEventStore.class.getDeclaredMethod(
                "append", Connection.class, JournalEvent.class);
        Method replaceCurrentState = SqlAuditRepository.class.getDeclaredMethod(
                "replaceCurrentState", Connection.class, AuditEntry.class);

        assertFalse(Modifier.isPublic(append.getModifiers()));
        assertFalse(Modifier.isPublic(replaceCurrentState.getModifiers()));
    }

    @ParameterizedTest(name = "{0} uses its dialect boolean JDBC NULL type")
    @MethodSource("nullableBooleanDialects")
    @DisplayName("binds nullable booleans with the dialect JDBC type")
    void bindsNullableBooleansWithDialectType(SqlDialect dialect, int expectedJdbcType) throws Exception {
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        AuditEntry auditEntry = new AuditEntry(
                "execution", "stage", "change", "author", LocalDateTime.now(), null, null,
                null, null, null, 0L, null, null, false, null, null,
                null, null, null, null);
        JournalEvent<AuditEntry> event = new JournalEvent<>(
                "event", JournalEventType.CHANGE_STATE, "stage", 1L, Instant.now(), auditEntry);

        new SqlJournalEventMapper(dialect).bind(statement, event);

        Mockito.verify(statement).setNull(26, expectedJdbcType);
        Mockito.verify(statement).setBoolean(27, false);
    }

    private static Stream<Arguments> nullableBooleanDialects() {
        return Stream.of(
                Arguments.of(SqlDialect.MYSQL, Types.TINYINT),
                Arguments.of(SqlDialect.SQLSERVER, Types.BIT),
                Arguments.of(SqlDialect.ORACLE, Types.NUMERIC),
                Arguments.of(SqlDialect.DB2, Types.SMALLINT));
    }

    private static void insert(Connection connection,
                               SqlJournalDialectHelper dialectHelper,
                               JournalEvent<AuditEntry> event) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(dialectHelper.getInsertSqlString(TABLE_NAME))) {
            new SqlJournalEventMapper().bind(insert, event);
            insert.executeUpdate();
        }
    }

    private static AuditEntry auditEntry() {
        return new AuditEntry(
                "execution-1", "stage-1", "change-1", "author-1",
                LocalDateTime.of(2020, 1, 2, 3, 4, 5, 600_000_000),
                AuditEntry.Status.APPLIED, AuditEntry.ChangeType.STANDARD_CODE,
                "com.example.Change", "apply", "Change.java", 123L, "host-1",
                "metadata-value", false, "error-trace", AuditTxType.NON_TX, "sql", "001",
                RecoveryStrategy.MANUAL_INTERVENTION, true);
    }

    private static void assertAuditEntryEquals(AuditEntry expected, AuditEntry actual) {
        assertEquals(expected.getExecutionId(), actual.getExecutionId());
        assertEquals(expected.getStageId(), actual.getStageId());
        assertEquals(expected.getChangeId(), actual.getChangeId());
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getCreatedAt(), actual.getCreatedAt());
        assertEquals(expected.getState(), actual.getState());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getClassName(), actual.getClassName());
        assertEquals(expected.getMethodName(), actual.getMethodName());
        assertEquals(expected.getSourceFile(), actual.getSourceFile());
        assertEquals(expected.getExecutionMillis(), actual.getExecutionMillis());
        assertEquals(expected.getExecutionHostname(), actual.getExecutionHostname());
        assertEquals(expected.getMetadata(), actual.getMetadata());
        assertEquals(expected.getSystemChange(), actual.getSystemChange());
        assertEquals(expected.getErrorTrace(), actual.getErrorTrace());
        assertEquals(expected.getTxType(), actual.getTxType());
        assertEquals(expected.getTargetSystemId(), actual.getTargetSystemId());
        assertEquals(expected.getOrder(), actual.getOrder());
        assertEquals(expected.getRecoveryStrategy(), actual.getRecoveryStrategy());
        assertEquals(expected.getTransactionFlag(), actual.getTransactionFlag());
    }
}
