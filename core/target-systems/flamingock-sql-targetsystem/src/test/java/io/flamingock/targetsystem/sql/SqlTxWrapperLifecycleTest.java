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
package io.flamingock.targetsystem.sql;

import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.core.change.navigation.step.FailedStep;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.transaction.TransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SqlTxWrapperLifecycleTest {

    private DataSource dataSource;
    private SqlTxWrapper txWrapper;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:sql_tx_wrapper_lifecycle;DB_CLOSE_DELAY=-1");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("");
        dataSource = jdbcDataSource;
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("DROP TABLE IF EXISTS tx_events");
            connection.createStatement().execute("CREATE TABLE tx_events (id INT PRIMARY KEY, payload VARCHAR(255))");
        }
        txWrapper = new SqlTxWrapper(new TransactionManager<>(this::openConnection));
    }

    @Test
    @DisplayName("commits callback writes and returns the callback value")
    void commitsSuccessfulCallback() throws Exception {
        BasicRuntimeContext context = new BasicRuntimeContext("success");

        String result = txWrapper.wrapInTransaction(context, runtimeContext -> {
            insert(runtimeContext, 1, "committed");
            return "success";
        });

        assertEquals("success", result);
        assertEquals(1, countRows());
    }

    @Test
    @DisplayName("rolls back writes when the callback returns a FailedStep")
    void rollsBackFailedStepValue() throws Exception {
        FailedStep failedStep = mock(FailedStep.class);
        BasicRuntimeContext context = new BasicRuntimeContext("failed-value");

        FailedStep result = txWrapper.wrapInTransaction(context, runtimeContext -> {
            insert(runtimeContext, 2, "rolled-back-value");
            return failedStep;
        });

        assertSame(failedStep, result);
        assertEquals(0, countRows());
    }

    @Test
    @DisplayName("rolls back writes and wraps callback exceptions")
    void rollsBackCallbackException() throws Exception {
        BasicRuntimeContext context = new BasicRuntimeContext("exception");

        DatabaseTransactionException exception = assertThrows(DatabaseTransactionException.class,
                () -> txWrapper.wrapInTransaction(context, runtimeContext -> {
                    insert(runtimeContext, 3, "rolled-back-exception");
                    throw new IllegalStateException("callback failed");
                }));

        assertEquals("callback failed", exception.getCause().getMessage());
        assertEquals(0, countRows());
    }

    @Test
    @DisplayName("closes a session after each transaction so the same session ID can be reused")
    void reusesSessionIdWithFreshConnection() throws Exception {
        BasicRuntimeContext firstContext = new BasicRuntimeContext("reused-session");
        BasicRuntimeContext secondContext = new BasicRuntimeContext("reused-session");

        txWrapper.wrapInTransaction(firstContext, runtimeContext -> {
            insert(runtimeContext, 4, "first-transaction");
            return "first";
        });
        txWrapper.wrapInTransaction(secondContext, runtimeContext -> {
            insert(runtimeContext, 5, "second-transaction");
            return "second";
        });

        assertEquals(2, countRows());
    }

    @Test
    @DisplayName("closes a session after a value-reported rollback before a later transaction")
    void reusesSessionIdAfterFailedStepRollback() throws Exception {
        BasicRuntimeContext failedContext = new BasicRuntimeContext("failed-then-reused");
        BasicRuntimeContext successfulContext = new BasicRuntimeContext("failed-then-reused");
        FailedStep failedStep = mock(FailedStep.class);

        txWrapper.wrapInTransaction(failedContext, runtimeContext -> {
            insert(runtimeContext, 6, "discarded");
            return failedStep;
        });
        txWrapper.wrapInTransaction(successfulContext, runtimeContext -> {
            insert(runtimeContext, 7, "committed-after-failure");
            return "success";
        });

        assertEquals(1, countRows());
    }

    private Connection openConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not open H2 connection", exception);
        }
    }

    private static void insert(RuntimeContext runtimeContext, int id, String value) {
        try {
            Connection connection = runtimeContext.getContext().getRequiredDependencyValue(Connection.class);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tx_events (id, payload) VALUES (?, ?)")) {
                statement.setInt(1, id);
                statement.setString(2, value);
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not insert test row", exception);
        }
    }

    private int countRows() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM tx_events");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
