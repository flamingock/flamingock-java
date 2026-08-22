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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.targetsystem.sql.SqlTxWrapper;
import org.h2.jdbcx.JdbcDataSource;
import org.sqlite.SQLiteDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlJournalEventStoreJdbcTest {

    private static final String TABLE_NAME = "flamingockJournalEvents";

    private DataSource dataSource;
    private SqlTxWrapper txWrapper;
    private SqlJournalEventStore journalEventStore;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:sql_journal_store;DB_CLOSE_DELAY=-1");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("");
        dataSource = jdbcDataSource;
        txWrapper = transactionWrapperFor(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        }
        journalEventStore = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);
    }

    @Test
    @DisplayName("initializes the journal schema and validates it on a later startup")
    void initializesAndValidatesSchema() throws Exception {
        journalEventStore.initialize(true);

        SqlJournalEventStore secondStore = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);
        secondStore.initialize(false);

        assertFalse(secondStore.getLastEventByStream("missing").isPresent());
    }

    @Test
    @DisplayName("does not create a missing table when auto-create is disabled")
    void rejectsMissingSchemaWhenAutoCreateIsDisabled() {
        assertThrows(IllegalStateException.class, () -> journalEventStore.initialize(false));
    }

    @Test
    @DisplayName("accepts SQLite INTEGER affinity for the portable 64-bit sequence columns")
    void acceptsSqliteIntegerAffinityForLongColumns() throws Exception {
        Path databaseFile = Files.createTempFile("sql-journal-metadata-", ".db");
        SQLiteDataSource sqliteDataSource = new SQLiteDataSource();
        sqliteDataSource.setUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath());

        try {
            SqlJournalEventStore sqliteStore = new SqlJournalEventStore(
                    sqliteDataSource, TABLE_NAME, transactionWrapperFor(sqliteDataSource));

            sqliteStore.initialize(true);

            assertFalse(sqliteStore.getLastEventByStream("missing").isPresent());
        } finally {
            Files.deleteIfExists(databaseFile);
        }
    }

    @Test
    @DisplayName("returns the highest sequence for one stream and ignores other streams")
    void returnsLastEventByStream() throws Exception {
        journalEventStore.initialize(true);
        append(event("stage-1", 1L, "event-1", false));
        append(event("stage-1", 3L, "event-3", false));
        append(event("stage-1", 2L, "event-2", true));
        append(event("stage-2", 9L, "other-stream", false));

        Optional<JournalEvent<AuditEntry>> last = journalEventStore.getLastEventByStream("stage-1");

        assertTrue(last.isPresent());
        assertEquals("event-3", last.get().getEventId());
        assertEquals(3L, last.get().getStreamSequence());
        assertFalse(journalEventStore.getLastEventByStream("unknown").isPresent());
    }

    @Test
    @DisplayName("returns pending events in stream-position order and honors the requested limit")
    void returnsPendingEventsInBoundedOrder() throws Exception {
        journalEventStore.initialize(true);
        append(event("stage-2", 1L, "event-2-1", false));
        append(event("stage-1", 2L, "event-1-2", false));
        append(event("stage-1", 1L, "event-1-1", false));
        append(event("stage-0", 1L, "acknowledged", true));

        List<String> ids = journalEventStore.getUnacknowledgedEvents(2).stream()
                .map(JournalEvent::getEventId)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("event-1-1", "event-1-2"), ids);
        assertEquals(3, journalEventStore.getUnacknowledgedEvents(10).size());
        assertEquals(Collections.emptyList(), journalEventStore.getUnacknowledgedEvents(0));
        assertFalse(journalEventStore.getLastEventByStream(" ").isPresent());
    }

    @Test
    @DisplayName("acknowledges every pending row for duplicate event IDs and is idempotent")
    void acknowledgesDuplicateEventIdsAndSkipsBlankIds() throws Exception {
        journalEventStore.initialize(true);
        append(event("stage-1", 1L, "shared-event-id", false));
        append(event("stage-2", 1L, "shared-event-id", false));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:sql_journal_store;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setAutoCommit(false);
        try (HikariDataSource nonAutoCommitDataSource = new HikariDataSource(config)) {
            SqlTxWrapper nonAutoCommitTxWrapper = new SqlTxWrapper(
                    new TransactionManager<>(() -> {
                        try {
                            return nonAutoCommitDataSource.getConnection();
                        } catch (SQLException exception) {
                            throw new IllegalStateException("Could not open test connection", exception);
                        }
                    }));
            SqlJournalEventStore transactionalStore = new SqlJournalEventStore(
                    nonAutoCommitDataSource, TABLE_NAME, nonAutoCommitTxWrapper);
            transactionalStore.initialize(false);

            long acknowledged = transactionalStore.acknowledgeEvents(
                    Arrays.asList(null, "", " ", "shared-event-id"));

            assertEquals(2L, acknowledged);
            assertEquals(0, transactionalStore.getUnacknowledgedEvents(10).size());
            assertEquals(0L, transactionalStore.acknowledgeEvents(
                    Collections.singletonList("shared-event-id")));
        }
    }

    @Test
    @DisplayName("acknowledgements use independent transactions")
    void acknowledgesEachEventInItsOwnTransaction() throws Exception {
        journalEventStore.initialize(true);
        append(event("stage-1", 1L, "first-event", false));
        append(event("stage-2", 1L, "second-event", false));

        int[] transactionCount = {0};
        TransactionWrapper failingAfterSecondTransaction = new TransactionWrapper() {
            @Override
            public <CONTEXT extends RuntimeContext, RESULT> RESULT wrapInTransaction(
                    CONTEXT runtimeContext, Function<CONTEXT, RESULT> operation) {
                int transactionNumber = ++transactionCount[0];
                return txWrapper.wrapInTransaction(runtimeContext, context -> {
                    RESULT result = operation.apply(context);
                    if (transactionNumber == 2) {
                        throw new IllegalStateException("forced second acknowledgement failure");
                    }
                    return result;
                });
            }
        };
        SqlJournalEventStore transactionalStore = new SqlJournalEventStore(
                dataSource, TABLE_NAME, failingAfterSecondTransaction);
        transactionalStore.initialize(false);

        assertThrows(DatabaseTransactionException.class,
                () -> transactionalStore.acknowledgeEvents(Arrays.asList("first-event", "second-event")));

        assertEquals(2, transactionCount[0]);
        assertEquals(Collections.singletonList("second-event"), transactionalStore.getUnacknowledgedEvents(10)
                .stream()
                .map(JournalEvent::getEventId)
                .collect(Collectors.toList()));
    }

    @Test
    @DisplayName("rejects a stream-position collision without replacing the original event")
    void rejectsStreamPositionCollision() throws Exception {
        journalEventStore.initialize(true);
        append(event("stage-1", 1L, "original", false));

        assertThrows(RuntimeException.class, () -> append(event("stage-1", 1L, "replacement", false)));

        assertEquals("original", journalEventStore.getLastEventByStream("stage-1").get().getEventId());
    }

    @Test
    @DisplayName("accepts extra columns and a different physical required-column order")
    void acceptsExtraColumnsAndPhysicalReordering() throws Exception {
        createReorderedSchema();

        SqlJournalEventStore restarted = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);
        restarted.initialize(false);
        append(restarted, event("reordered-stage", 1L, "reordered-event", false));

        assertEquals("reordered-event",
                restarted.getLastEventByStream("reordered-stage").get().getEventId());
    }

    @Test
    @DisplayName("rejects an extra non-null column without a default or generated value")
    void rejectsInsertBlockingExtraColumn() throws Exception {
        journalEventStore.initialize(true);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute(
                    "ALTER TABLE " + TABLE_NAME + " ADD required_extra VARCHAR(20) NOT NULL");
        }

        SqlJournalEventStore restarted = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> restarted.initialize(false));

        assertTrue(exception.getMessage().toLowerCase().contains("required_extra"));
    }

    @Test
    @DisplayName("rejects a missing required journal column")
    void rejectsMissingRequiredColumn() throws Exception {
        journalEventStore.initialize(true);
        SqlJournalDialectHelper helper = new SqlJournalDialectHelper(SqlDialect.H2);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DROP INDEX " + helper.getIndexNames(TABLE_NAME).get(1));
            connection.createStatement().execute("ALTER TABLE " + TABLE_NAME + " DROP COLUMN event_id");
        }

        SqlJournalEventStore restarted = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);

        assertThrows(IllegalStateException.class, () -> restarted.initialize(false));
    }

    @Test
    @DisplayName("rejects ambiguous case-insensitive metadata for a required journal column")
    void rejectsAmbiguousRequiredColumnMetadata() throws Exception {
        journalEventStore.initialize(true);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute(
                    "ALTER TABLE " + TABLE_NAME + " ADD \"Event_Id\" VARCHAR(255)");
        }

        SqlJournalEventStore restarted = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);

        assertThrows(IllegalStateException.class, () -> restarted.initialize(false));
    }

    @Test
    @DisplayName("rejects a journal column with the wrong type or capacity")
    void rejectsWrongColumnMetadata() throws Exception {
        journalEventStore.initialize(true);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute(
                    "ALTER TABLE " + TABLE_NAME + " ALTER COLUMN event_id VARCHAR(64) NOT NULL");
        }

        SqlJournalEventStore restarted = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);

        assertThrows(IllegalStateException.class, () -> restarted.initialize(false));
    }

    @Test
    @DisplayName("rejects a required journal column made nullable")
    void rejectsWrongNullability() throws Exception {
        journalEventStore.initialize(true);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute(
                    "ALTER TABLE " + TABLE_NAME + " ALTER COLUMN event_type VARCHAR(32) NULL");
        }

        SqlJournalEventStore restarted = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);

        assertThrows(IllegalStateException.class, () -> restarted.initialize(false));
    }

    @Test
    @DisplayName("rejects a journal primary key with the wrong column order")
    void rejectsWrongPrimaryKeyOrder() throws Exception {
        journalEventStore.initialize(true);
        try (Connection connection = dataSource.getConnection()) {
            String primaryKeyName;
            try (java.sql.ResultSet resultSet = connection.getMetaData()
                    .getPrimaryKeys(null, null, TABLE_NAME.toUpperCase())) {
                assertTrue(resultSet.next());
                primaryKeyName = resultSet.getString("PK_NAME");
            }
            connection.createStatement().execute(
                    "ALTER TABLE " + TABLE_NAME + " DROP CONSTRAINT " + primaryKeyName);
            connection.createStatement().execute(
                    "ALTER TABLE " + TABLE_NAME + " ADD PRIMARY KEY (stream_sequence, stream_id)");
        }

        SqlJournalEventStore restarted = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);

        assertThrows(IllegalStateException.class, () -> restarted.initialize(false));
    }

    @Test
    @DisplayName("rejects an index with the expected name but the wrong column shape")
    void rejectsWrongIndexShape() throws Exception {
        journalEventStore.initialize(true);
        SqlJournalDialectHelper helper = new SqlJournalDialectHelper(SqlDialect.H2);
        String pendingIndex = helper.getIndexNames(TABLE_NAME).get(0);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DROP INDEX " + pendingIndex);
            connection.createStatement().execute(
                    "CREATE INDEX " + pendingIndex + " ON " + TABLE_NAME + " (event_id)");
        }

        SqlJournalEventStore restarted = new SqlJournalEventStore(dataSource, TABLE_NAME, txWrapper);

        assertThrows(IllegalStateException.class, () -> restarted.initialize(false));
    }

    private void append(JournalEvent<AuditEntry> event) throws Exception {
        append(journalEventStore, event);
    }

    private void append(SqlJournalEventStore store, JournalEvent<AuditEntry> event) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            store.append(connection, event);
            connection.commit();
        }
    }

    private void createReorderedSchema() throws SQLException {
        SqlJournalDialectHelper helper = new SqlJournalDialectHelper(SqlDialect.H2);
        List<SqlJournalDialectHelper.ColumnDefinition> definitions =
                new ArrayList<>(helper.getColumnDefinitions());
        Collections.reverse(definitions);

        StringBuilder ddl = new StringBuilder("CREATE TABLE ")
                .append(TABLE_NAME)
                .append(" (unexpected_column VARCHAR(20) DEFAULT 'extra' NOT NULL, ");
        for (SqlJournalDialectHelper.ColumnDefinition definition : definitions) {
            if (ddl.charAt(ddl.length() - 1) != ' ') {
                ddl.append(", ");
            }
            ddl.append(definition.name)
                    .append(' ')
                    .append(h2Type(definition))
                    .append(definition.nullable ? "" : " NOT NULL");
        }
        ddl.append(", PRIMARY KEY (stream_id, stream_sequence))");

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute(ddl.toString());
            for (String indexSql : helper.getCreateIndexSqlStrings(TABLE_NAME)) {
                connection.createStatement().execute(indexSql);
            }
        }
    }

    private SqlTxWrapper transactionWrapperFor(DataSource source) {
        return new SqlTxWrapper(new TransactionManager<>(() -> {
            try {
                return source.getConnection();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not open test connection", exception);
            }
        }));
    }

    private static String h2Type(SqlJournalDialectHelper.ColumnDefinition definition) {
        switch (definition.type) {
            case VARCHAR:
                return "VARCHAR(" + definition.size + ")";
            case INTEGER:
                return "INTEGER";
            case LONG:
                return "BIGINT";
            case TIMESTAMP:
                return "TIMESTAMP";
            case BOOLEAN:
                return "BOOLEAN";
            case TEXT:
                return "VARCHAR(4000)";
            default:
                throw new AssertionError("Unsupported test column type: " + definition.type);
        }
    }

    private static JournalEvent<AuditEntry> event(String streamId,
                                                  long sequence,
                                                  String eventId,
                                                  boolean acknowledged) {
        return new JournalEvent<>(
                eventId,
                JournalEventType.CHANGE_STATE,
                JournalEvent.DEFAULT_VERSION,
                streamId,
                sequence,
                Instant.parse("2026-08-11T10:20:30Z").plusSeconds(sequence),
                AuditEntryTestFactory.createTestAuditEntry(
                        eventId, AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null),
                acknowledged);
    }
}
