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
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.journal.JournalEventType;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.sql.SqlTxWrapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlAuditPersistenceJournalTest {

    private static final String AUDIT_TABLE = "flamingockAuditLog";
    private static final String JOURNAL_TABLE = "flamingockJournalEvents";
    private static final String STREAM_ID = "stage-under-test";

    private DataSource dataSource;
    private SqlTxWrapper txWrapper;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:sql_audit_journal;DB_CLOSE_DELAY=-1");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("");
        dataSource = jdbcDataSource;
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DROP TABLE IF EXISTS " + JOURNAL_TABLE);
            connection.createStatement().execute("DROP TABLE IF EXISTS " + AUDIT_TABLE);
        }
        txWrapper = new SqlTxWrapper(new TransactionManager<>(this::openConnection));
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    @Test
    @DisplayName("journal disabled keeps append history and never creates journal storage")
    void journalDisabledKeepsLegacyAppendPathAndCreatesNoJournalTable() throws Exception {
         SqlJournalEventStore journalStore = new SqlJournalEventStore(dataSource, JOURNAL_TABLE, txWrapper);
        SqlAuditPersistence persistence = persistenceFor(
                new SqlAuditRepository(dataSource, AUDIT_TABLE), journalStore, mock(JournalEventSequencer.class), false);

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        assertEquals(2, persistence.getAuditHistory().size());
        assertFalse(tableExists(JOURNAL_TABLE), "flag OFF must not initialize or access the journal table");
    }

    @Test
    @DisplayName("SQL persistence initialization consumes a ready audit writer")
    void persistenceInitializationConsumesReadyAuditWriter() throws Exception {
        SqlAuditRepository auditRepository = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        auditRepository.initialize(true);
        SqlAuditPersistence persistence = new SqlAuditPersistence(
                new CommunityConfiguration(), auditRepository, null, null, null, false);

        persistence.initialize(RunnerId.generate());
        persistence.writeEntry(auditEntry("legacy-constructor", AuditEntry.Status.APPLIED));

        assertEquals(1, persistence.getAuditHistory().size());
    }

    @Test
    @DisplayName("SQL persistence initialization does not perform schema setup")
    void persistenceInitializationDoesNotPerformSchemaSetup() {
        SqlAuditRepository auditRepository = org.mockito.Mockito.mock(SqlAuditRepository.class);
        SqlJournalEventStore journalStore = org.mockito.Mockito.mock(SqlJournalEventStore.class);
        JournalEventSequencer sequencer = org.mockito.Mockito.mock(JournalEventSequencer.class);
        SqlAuditPersistence persistence = new SqlAuditPersistence(
                new CommunityConfiguration(), auditRepository, journalStore, sequencer, txWrapper, true);

        persistence.initialize(RunnerId.generate());

        verify(auditRepository, never()).initialize(ArgumentMatchers.anyBoolean());
        verify(journalStore, never()).initialize(ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("journal enabled retains event history while the audit table stores current state")
    void journalEnabledSplitsCurrentStateFromHistory() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditPersistence persistence = persistenceFor(
                new SqlAuditRepository(dataSource, AUDIT_TABLE), journalStore, newSequencer(journalStore), true);

        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.STARTED));
        persistence.writeEntry(auditEntry("change-1", AuditEntry.Status.APPLIED));

        List<AuditEntry> auditHistory = persistence.getAuditHistory();
        Optional<JournalEvent<AuditEntry>> last = journalStore.getLastEventByStream(STREAM_ID);

        assertEquals(1, auditHistory.size());
        assertEquals(AuditEntry.Status.APPLIED, auditHistory.get(0).getState());
        assertTrue(last.isPresent());
        assertEquals(2L, last.get().getStreamSequence());
        assertEquals(2, journalStore.getUnacknowledgedEvents(10).size());
    }

    @Test
    @DisplayName("journal-enabled writes update every mapped value without replacing the current row")
    void journalEnabledUpdatesEveryMappedValueWithoutReplacingCurrentRow() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        SqlAuditPersistence persistence = persistenceFor(auditor, journalStore, newSequencer(journalStore), true);
        AuditEntry initial = fullAuditEntry("identity-change", "initial", AuditEntry.Status.STARTED);
        AuditEntry updated = fullAuditEntry("identity-change", "updated", AuditEntry.Status.APPLIED);

        persistence.writeEntry(initial);
        long currentRowId = currentRowId("identity-change");

        persistence.writeEntry(updated);

        assertEquals(1, auditRowCount("identity-change"));
        assertEquals(currentRowId, currentRowId("identity-change"));
        assertAuditEntryEquals(updated, persistence.getAuditHistory().get(0));
    }

    @Test
    @DisplayName("current-state updates bind nullable audit values through the dialect mapper")
    void currentStateUpdatesBindNullableValues() throws Exception {
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        auditor.initialize(true);
        auditor.writeEntry(fullAuditEntry("nullable-current", "initial", AuditEntry.Status.STARTED));

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            auditor.replaceCurrentState(connection, nullableAuditEntry("nullable-current"));
            connection.commit();
        }

        try (Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "SELECT author, created_at, state, metadata, error_trace, tx_strategy, "
                             + "target_system_id, change_order, recovery_strategy, transaction_flag, system_change "
                             + "FROM " + AUDIT_TABLE + " WHERE change_id = ?")) {
            statement.setString(1, "nullable-current");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertNull(resultSet.getString("author"));
                assertNull(resultSet.getTimestamp("created_at"));
                assertNull(resultSet.getString("state"));
                assertNull(resultSet.getString("metadata"));
                assertNull(resultSet.getString("error_trace"));
                assertEquals(AuditTxType.NON_TX.name(), resultSet.getString("tx_strategy"));
                assertNull(resultSet.getString("target_system_id"));
                assertNull(resultSet.getString("change_order"));
                assertEquals(RecoveryStrategy.MANUAL_INTERVENTION.name(), resultSet.getString("recovery_strategy"));
                assertNull(resultSet.getObject("transaction_flag"));
                assertFalse(resultSet.getBoolean("system_change"));
            }
        }
    }

    @Test
    @DisplayName("append and history mapping preserve nullable audit values")
    void appendAndHistoryMappingPreserveNullableValues() throws Exception {
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        auditor.initialize(true);

        Result result = auditor.writeEntry(nullableAuditEntry("nullable-append"));

        assertTrue(result instanceof Result.Ok);
        AuditEntry actual = auditor.getAuditHistory().get(0);
        assertNull(actual.getCreatedAt());
        assertNull(actual.getAuthor());
        assertNull(actual.getState());
        assertNull(actual.getTransactionFlag());
        assertFalse(actual.getSystemChange());
    }

    @Test
    @DisplayName("current-state writes leave transaction ownership with the caller")
    void currentStateWriteDoesNotCommitOrCloseCallerConnection() throws Exception {
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        auditor.initialize(true);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            auditor.replaceCurrentState(connection,
                    fullAuditEntry("caller-owned", "uncommitted", AuditEntry.Status.APPLIED));

            assertFalse(connection.isClosed());
            assertFalse(connection.getAutoCommit());
            assertEquals(0, auditRowCount("caller-owned"));
            connection.rollback();
        }

        assertEquals(0, auditRowCount("caller-owned"));
    }

    @Test
    @DisplayName("journal-enabled writes insert exactly one current row when the update matches nothing")
    void journalEnabledInsertsWhenCurrentRowIsAbsent() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        SqlAuditPersistence persistence = persistenceFor(auditor, journalStore, newSequencer(journalStore), true);

        persistence.writeEntry(fullAuditEntry("new-current-row", "inserted", AuditEntry.Status.APPLIED));

        assertEquals(1, auditRowCount("new-current-row"));
        assertEquals(1, persistence.getAuditHistory().size());
    }

    @Test
    @DisplayName("more than one current row fails transactionally without repairing audit state")
    void multipleCurrentRowsFailWithoutRepairOrSequenceConfirmation() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        auditor.initialize(true);
        AuditEntry first = fullAuditEntry("duplicate-current", "first", AuditEntry.Status.STARTED);
        AuditEntry second = fullAuditEntry("duplicate-current", "second", AuditEntry.Status.FAILED);
        auditor.writeEntry(first);
        auditor.writeEntry(second);

        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditPersistence persistence = persistenceFor(auditor, journalStore, newSequencer(journalStore), true);

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(fullAuditEntry("duplicate-current", "attempted", AuditEntry.Status.APPLIED)));

        assertEquals(2, auditRowCount("duplicate-current"));
        assertEquals(2, persistence.getAuditHistory().size());
        assertEquals(first.getExecutionId(), persistence.getAuditHistory().get(0).getExecutionId());
        assertEquals(second.getExecutionId(), persistence.getAuditHistory().get(1).getExecutionId());
        assertTrue(journalStore.getUnacknowledgedEvents(10).isEmpty());

        persistence.writeEntry(fullAuditEntry("after-duplicate", "retry", AuditEntry.Status.APPLIED));

        assertEquals(1L, journalStore.getLastEventByStream(STREAM_ID).get().getStreamSequence());
    }

    @Test
    @DisplayName("journal current-state writes reject a null change id before DML")
    void nullChangeIdIsRejectedBeforeDml() throws Exception {
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        auditor.initialize(true);

        try (Connection connection = dataSource.getConnection()) {
            assertThrows(IllegalArgumentException.class,
                    () -> auditor.replaceCurrentState(connection, fullAuditEntry(null, "null-id", AuditEntry.Status.APPLIED)));
        }

        assertEquals(0, auditRowCount(null));
    }

    @Test
    @DisplayName("journal current-state writes reject a blank change id before DML")
    void blankChangeIdIsRejectedBeforeDml() throws Exception {
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        auditor.initialize(true);

        try (Connection connection = dataSource.getConnection()) {
            assertThrows(IllegalArgumentException.class,
                    () -> auditor.replaceCurrentState(connection, fullAuditEntry("   ", "blank-id", AuditEntry.Status.APPLIED)));
        }

        assertEquals(0, auditRowCount("   "));
    }

    @Test
    @DisplayName("invalid audit table identifiers are rejected before current-state DML")
    void invalidAuditTableIdentifierIsRejectedBeforeDml() {
        assertThrows(IllegalArgumentException.class,
                () -> new SqlAuditRepository(dataSource, "audit-log"));
        assertThrows(IllegalArgumentException.class,
                () -> new SqlAuditRepository(dataSource, " "));
    }

    @Test
    @DisplayName("journal-disabled writes keep append behavior and skip current-state persistence")
    void journalDisabledWritesUseAppendOnlyRepositoryOperation() {
        SqlAuditRepository auditor = mock(SqlAuditRepository.class);
        when(auditor.writeEntry(ArgumentMatchers.any(AuditEntry.class))).thenReturn(Result.OK());
        SqlAuditPersistence persistence = new SqlAuditPersistence(
                new CommunityConfiguration(), auditor, null, null, null, false);

        Result result = persistence.writeEntry(auditEntry("append-only", AuditEntry.Status.APPLIED));

        assertTrue(result instanceof Result.Ok);
        verify(auditor).writeEntry(ArgumentMatchers.any(AuditEntry.class));
        verify(auditor, never()).replaceCurrentState(
                ArgumentMatchers.any(Connection.class), ArgumentMatchers.any(AuditEntry.class));
    }

    @Test
    @DisplayName("journal-enabled writes append the event before current state and confirm after success")
    void journalWriteAppendsEventBeforeCurrentStateAndConfirmsAfterSuccess() {
        SqlAuditRepository auditor = mock(SqlAuditRepository.class);
        SqlJournalEventStore journalStore = mock(SqlJournalEventStore.class);
        JournalEventSequencer sequencer = mock(JournalEventSequencer.class);
        AuditEntry auditEntry = auditEntry("ordered-write", AuditEntry.Status.APPLIED);
        JournalEvent<AuditEntry> event = event(STREAM_ID, 1L, "ordered-event", auditEntry.getChangeId());
        when(sequencer.newEvent(auditEntry)).thenReturn(event);
        when(auditor.replaceCurrentState(
                ArgumentMatchers.any(Connection.class), ArgumentMatchers.any(AuditEntry.class)))
                .thenReturn(Result.OK());
        SqlAuditPersistence persistence = persistenceFor(auditor, journalStore, sequencer, true);

        persistence.writeEntry(auditEntry);

        org.mockito.InOrder order = inOrder(sequencer, journalStore, auditor);
        order.verify(sequencer).newEvent(auditEntry);
        order.verify(journalStore).append(ArgumentMatchers.any(Connection.class), ArgumentMatchers.same(event));
        order.verify(auditor).replaceCurrentState(
                ArgumentMatchers.any(Connection.class), ArgumentMatchers.same(auditEntry));
        order.verify(sequencer).confirm();
    }

    @Test
    @DisplayName("journal enabled does not backfill an existing audit row")
    void journalEnabledDoesNotBackfillExistingAuditHistory() throws Exception {
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        auditor.initialize(true);
        auditor.writeEntry(auditEntry("legacy-change", AuditEntry.Status.APPLIED));

        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditPersistence persistence = persistenceFor(auditor, journalStore, newSequencer(journalStore), true);
        persistence.writeEntry(auditEntry("new-change", AuditEntry.Status.APPLIED));

        assertEquals(2, persistence.getAuditHistory().size());
        assertEquals(1, journalStore.getUnacknowledgedEvents(10).size());
        assertEquals("new-change", journalStore.getUnacknowledgedEvents(10).get(0).getData().getChangeId());
    }

    @Test
    @DisplayName("a journal append failure rolls the current audit write back")
    void journalFailureRollsBackAuditEntry() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SqlJournalEventStore journalStore = initializedJournalStore();
        JournalEventSequencer sequencer = newSequencer(journalStore);
        appendDirectly(journalStore, event(STREAM_ID, 1L, "occupying-event", "occupying-change"));
        SqlAuditPersistence persistence = persistenceFor(
                new SqlAuditRepository(dataSource, AUDIT_TABLE), journalStore, sequencer, true);

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(auditEntry("failed-change", AuditEntry.Status.APPLIED)));

        assertTrue(persistence.getAuditHistory().isEmpty());
        assertEquals(1, journalStore.getUnacknowledgedEvents(10).size());
    }

    @Test
    @DisplayName("an audit write failure rolls the journal event back")
    void auditFailureRollsBackJournalEvent() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditRepository failingAuditor = mock(SqlAuditRepository.class);
        doThrow(new IllegalStateException("audit write failed"))
                .when(failingAuditor)
                .replaceCurrentState(ArgumentMatchers.any(Connection.class), ArgumentMatchers.any(AuditEntry.class));
        SqlAuditPersistence persistence = persistenceFor(failingAuditor, journalStore, newSequencer(journalStore), true);

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(auditEntry("failed-audit", AuditEntry.Status.APPLIED)));

        assertTrue(journalStore.getUnacknowledgedEvents(10).isEmpty());
    }

    @Test
    @DisplayName("a failed journal transaction reuses its unconfirmed stream position")
    void failedWriteLeavesNoSequenceGap() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SqlJournalEventStore journalStore = initializedJournalStore();
        JournalEventSequencer sequencer = newSequencer(journalStore);
        SqlAuditRepository failingAuditor = mock(SqlAuditRepository.class);
        doThrow(new IllegalStateException("audit write failed"))
                .when(failingAuditor)
                .replaceCurrentState(ArgumentMatchers.any(Connection.class), ArgumentMatchers.any(AuditEntry.class));
        SqlAuditPersistence persistence = persistenceFor(failingAuditor, journalStore, sequencer, true);

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(auditEntry("first-attempt", AuditEntry.Status.APPLIED)));
        when(failingAuditor.replaceCurrentState(
                ArgumentMatchers.any(Connection.class), ArgumentMatchers.any(AuditEntry.class)))
                .thenReturn(Result.OK());

        persistence.writeEntry(auditEntry("retry", AuditEntry.Status.APPLIED));

        assertEquals(1, journalStore.getUnacknowledgedEvents(10).size());
        assertEquals(1L, journalStore.getUnacknowledgedEvents(10).get(0).getStreamSequence());
        assertEquals("retry", journalStore.getUnacknowledgedEvents(10).get(0).getData().getChangeId());
    }

    @Test
    @DisplayName("the audit reader stays independent from journal delivery reads")
    void auditReaderRemainsIndependentFromJournalStore() throws Exception {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        SqlAuditPersistence persistence = persistenceFor(auditor, journalStore, newSequencer(journalStore), true);
        persistence.writeEntry(auditEntry("reader-change", AuditEntry.Status.APPLIED));

        assertEquals(1, persistence.getAuditHistory().size());
        assertEquals(1, journalStore.getUnacknowledgedEvents(10).size());
    }

    @Test
    @DisplayName("the captured journal flag controls writes without a second global flag read")
    void capturedJournalFlagControlsWrites() throws Exception {
        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditPersistence persistence = persistenceFor(
                new SqlAuditRepository(dataSource, AUDIT_TABLE), journalStore, newSequencer(journalStore), true);

        persistence.writeEntry(auditEntry("captured-flag", AuditEntry.Status.APPLIED));

        assertEquals(1, journalStore.getUnacknowledgedEvents(10).size());
        assertEquals(1, persistence.getAuditHistory().size());
    }

    @Test
    @DisplayName("the first flag-on write updates a legacy row and emits only the transition event")
    void firstFlagOnWriteUpdatesLegacyCurrentState() throws Exception {
        SqlAuditRepository auditor = new SqlAuditRepository(dataSource, AUDIT_TABLE);
        auditor.initialize(true);
        auditor.writeEntry(auditEntry("legacy-transition", AuditEntry.Status.STARTED));

        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditPersistence persistence = persistenceFor(auditor, journalStore,
                newSequencer(journalStore), true);

        persistence.writeEntry(auditEntry("legacy-transition", AuditEntry.Status.APPLIED));

        assertEquals(1, persistence.getAuditHistory().size());
        assertEquals(AuditEntry.Status.APPLIED, persistence.getAuditHistory().get(0).getState());
        assertEquals(1, journalStore.getUnacknowledgedEvents(10).size());
        assertEquals("legacy-transition",
                journalStore.getUnacknowledgedEvents(10).get(0).getData().getChangeId());
    }

    @Test
    @DisplayName("a returned audit error rolls the journal event back")
    void returnedAuditErrorRollsBackJournalEvent() throws Exception {
        SqlJournalEventStore journalStore = initializedJournalStore();
        SqlAuditRepository failingAuditor = mock(SqlAuditRepository.class);
        when(failingAuditor.replaceCurrentState(
                ArgumentMatchers.any(Connection.class), ArgumentMatchers.any(AuditEntry.class)))
                .thenReturn(new Result.Error(new IllegalStateException("audit write failed")));
        SqlAuditPersistence persistence = persistenceFor(failingAuditor, journalStore,
                newSequencer(journalStore), true);

        assertThrows(DatabaseTransactionException.class,
                () -> persistence.writeEntry(auditEntry("failed-result", AuditEntry.Status.APPLIED)));

        assertTrue(journalStore.getUnacknowledgedEvents(10).isEmpty());
    }

    private SqlJournalEventStore initializedJournalStore() {
         SqlJournalEventStore journalStore = new SqlJournalEventStore(dataSource, JOURNAL_TABLE, txWrapper);
        journalStore.initialize(true);
        return journalStore;
    }

    private JournalEventSequencer newSequencer(SqlJournalEventStore journalStore) {
        return new JournalEventSequencerFactory(journalStore).forStream(STREAM_ID);
    }

    private SqlAuditPersistence persistenceFor(SqlAuditRepository auditor,
                                                SqlJournalEventStore journalStore,
                                                JournalEventSequencer sequencer,
                                                boolean journalEventsEnabled) {
        auditor.initialize(true);
        SqlAuditPersistence persistence = new SqlAuditPersistence(
                new CommunityConfiguration(), auditor, journalStore, sequencer, txWrapper, journalEventsEnabled);
        persistence.initialize(RunnerId.generate());
        return persistence;
    }

    private void appendDirectly(SqlJournalEventStore journalStore, JournalEvent<AuditEntry> event) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            journalStore.append(connection, event);
            connection.commit();
        }
    }

    private Connection openConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not open test connection", exception);
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private long currentRowId(String changeId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM " + AUDIT_TABLE + " WHERE change_id = ?")) {
            statement.setString(1, changeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                long id = resultSet.getLong(1);
                assertFalse(resultSet.next());
                return id;
            }
        }
    }

    private int auditRowCount(String changeId) throws SQLException {
        String sql = changeId == null
                ? "SELECT COUNT(*) FROM " + AUDIT_TABLE
                : "SELECT COUNT(*) FROM " + AUDIT_TABLE + " WHERE change_id = ?";
        try (Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            if (changeId != null) {
                statement.setString(1, changeId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private static AuditEntry auditEntry(String changeId, AuditEntry.Status status) {
        return AuditEntryTestFactory.createTestAuditEntry(changeId, status, AuditTxType.NON_TX, (Class<?>) null);
    }

    private static AuditEntry fullAuditEntry(String changeId, String suffix, AuditEntry.Status status) {
        return new AuditEntry(
                "execution-" + suffix,
                "stage-" + suffix,
                changeId,
                "author-" + suffix,
                LocalDateTime.of(2026, 8, 18, 10, 20, 30),
                status,
                AuditEntry.ChangeType.STANDARD_CODE,
                "class-" + suffix,
                "method-" + suffix,
                "source-" + suffix,
                100L + suffix.length(),
                "host-" + suffix,
                "metadata-" + suffix,
                true,
                "error-" + suffix,
                AuditTxType.NON_TX,
                "target-" + suffix,
                "order-" + suffix,
                RecoveryStrategy.ALWAYS_RETRY,
                false);
    }

    private static AuditEntry nullableAuditEntry(String changeId) {
        return new AuditEntry(
                "execution-nullable",
                "stage-nullable",
                changeId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null);
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

    private static JournalEvent<AuditEntry> event(String streamId,
                                                  long sequence,
                                                  String eventId,
                                                  String changeId) {
        return new JournalEvent<>(
                eventId,
                JournalEventType.CHANGE_STATE,
                JournalEvent.DEFAULT_VERSION,
                streamId,
                sequence,
                Instant.now(),
                auditEntry(changeId, AuditEntry.Status.APPLIED),
                false);
    }
}
