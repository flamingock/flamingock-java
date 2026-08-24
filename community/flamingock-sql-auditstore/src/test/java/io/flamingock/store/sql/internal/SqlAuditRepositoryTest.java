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

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.Result;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlAuditRepositoryTest {

    private static final String AUDIT_TABLE = "flamingockAuditLog";
    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    @Test
    @DisplayName("rejects a null data source with the configured dependency message")
    void rejectsNullDataSource() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new SqlAuditRepository(null, AUDIT_TABLE));

        assertEquals("dataSource must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("accepts a valid data source without claiming initialization")
    void acceptsValidConstruction() {
        assertDoesNotThrow(() -> new SqlAuditRepository(newDataSource(), AUDIT_TABLE));
    }

    @Test
    @DisplayName("returns an explicit error for a null legacy audit entry")
    void writeEntryRejectsNullEntry() {
        SqlAuditRepository repository = initializedRepository();

        Result result = repository.writeEntry(null);

        Throwable error = errorFrom(result);
        assertEquals(IllegalArgumentException.class, error.getClass());
        assertEquals("auditEntry must not be null", error.getMessage());
    }

    @Test
    @DisplayName("returns an explicit error when a legacy write happens before initialization")
    void writeEntryRejectsUninitializedRepository() {
        SqlAuditRepository repository = newRepository();

        Result result = repository.writeEntry(auditEntry("uninitialized-write"));

        Throwable error = errorFrom(result);
        assertEquals(IllegalStateException.class, error.getClass());
        assertEquals("SQL auditor is not initialized", error.getMessage());
    }

    @Test
    @DisplayName("fails clearly when audit history is read before initialization")
    void getAuditHistoryRejectsUninitializedRepository() {
        SqlAuditRepository repository = newRepository();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                repository::getAuditHistory);

        assertEquals("SQL auditor is not initialized", exception.getMessage());
    }

    @Test
    @DisplayName("fails clearly when the audit table is missing and auto-creation is disabled")
    void missingTableFailsInitialization() {
        SqlAuditRepository repository = newRepository();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> repository.initialize(false));

        assertEquals("SQL audit table '" + AUDIT_TABLE + "' does not exist", exception.getMessage());
    }

    @Test
    @DisplayName("retains the SQL setup cause when initialization cannot obtain a connection")
    void setupFailureRetainsCause() throws SQLException {
        DataSource failingDataSource = mock(DataSource.class);
        SQLException cause = new SQLException("connection unavailable");
        when(failingDataSource.getConnection()).thenThrow(cause);
        SqlAuditRepository repository = new SqlAuditRepository(failingDataSource, AUDIT_TABLE);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> repository.initialize(true));

        assertEquals("Failed to initialize audit table", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    private static SqlAuditRepository initializedRepository() {
        SqlAuditRepository repository = newRepository();
        repository.initialize(true);
        return repository;
    }

    private static SqlAuditRepository newRepository() {
        return new SqlAuditRepository(newDataSource(), AUDIT_TABLE);
    }

    private static AuditEntry auditEntry(String changeId) {
        return AuditEntryTestFactory.createTestAuditEntry(
                changeId, AuditEntry.Status.APPLIED, AuditTxType.NON_TX, (Class<?>) null);
    }

    private static Throwable errorFrom(Result result) {
        assertTrue(result instanceof Result.Error, "expected a Result.Error");
        return ((Result.Error) result).getError();
    }

    private static DataSource newDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:sql_audit_repository_"
                + DATABASE_SEQUENCE.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
