/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.community.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.community.sql.driver.SqlAuditStore;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.internal.util.Trio;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.targetsystem.sql.SqlTargetSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.sqlite.SQLiteDataSource;
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
class SqlAuditStoreTest {

    private static final Map<String, JdbcDatabaseContainer<?>> containers = new HashMap<>();
    private static final Map<String, DataSource> dataSources = new HashMap<>();

    static Stream<Arguments> dialectProvider() {
        return Stream.of(
                Arguments.of(SqlDialect.MYSQL, "mysql"),
                Arguments.of(SqlDialect.SQLSERVER, "sqlserver"),
                Arguments.of(SqlDialect.ORACLE, "oracle"),
                Arguments.of(SqlDialect.POSTGRESQL, "postgresql"),
                Arguments.of(SqlDialect.MARIADB, "mariadb"),
                Arguments.of(SqlDialect.H2, "h2"),
                Arguments.of(SqlDialect.SQLITE, "sqlite")
        );
    }

    @BeforeAll
    void startContainers() {
        for (Arguments arg : dialectProvider().toArray(Arguments[]::new)) {
            SqlDialect dialect = (SqlDialect) arg.get()[0];
            String dialectName = (String) arg.get()[1];
            if (!"h2".equals(dialectName) && !"sqlite".equals(dialectName)) {
                JdbcDatabaseContainer<?> container = SqlAuditTestHelper.createContainer(dialectName);
                container.start();
                containers.put(dialectName, container);
                dataSources.put(dialectName, SqlAuditTestHelper.createDataSource(container));
            }
        }
    }

    @AfterAll
    void stopContainers() {
        containers.values().forEach(JdbcDatabaseContainer::stop);
        dataSources.values().forEach(ds -> {
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }
        });
    }

    private TestContext setupTest(SqlDialect sqlDialect, String dialectName) throws SQLException {
        if ("h2".equals(dialectName)) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
            config.setUsername("sa");
            config.setPassword("");
            config.setDriverClassName("org.h2.Driver");
            DataSource dataSource = new HikariDataSource(config);

            SqlAuditTestHelper.createTables(dataSource, sqlDialect);

            return new TestContext(dataSource, null, sqlDialect);
        }

        if ("sqlite".equals(dialectName)) {
            String dbFile = "test_" + System.currentTimeMillis() + ".db";

            // Use a shared in-memory DB or file DB, but single connection
            String jdbcUrl = "jdbc:sqlite:" + dbFile;

            // Create a single-connection DataSource for SQLite
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl(jdbcUrl);

            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA busy_timeout=5000;");
            }

            // Run table creation with this same DataSource
            SqlAuditTestHelper.createTables(ds, sqlDialect);

            return new TestContext(ds, null, SqlDialect.SQLITE);
        }

        JdbcDatabaseContainer<?> container = SqlAuditTestHelper.createContainer(dialectName);
        container.start();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setDriverClassName(container.getDriverClassName());
        DataSource dataSource = new HikariDataSource(config);

        SqlAuditTestHelper.createTables(dataSource, sqlDialect);

        return new TestContext(dataSource, container, sqlDialect);
    }

    private Class<?>[] getChangeClasses(String dialectName, String scenario) {
        switch (dialectName) {
            case "mysql":
            case "mariadb":
            case "sqlite":
            case "h2":
                if ("happyPath".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.mysql.happyPath._001__create_index.class,
                            io.flamingock.community.sql.changes.mysql.happyPath._002__insert_document.class,
                            io.flamingock.community.sql.changes.mysql.happyPath._003__insert_another_document.class
                    };
                } else if ("failedWithRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.mysql.failedWithRollback._001__create_index.class,
                            io.flamingock.community.sql.changes.mysql.failedWithRollback._002__insert_document.class,
                            io.flamingock.community.sql.changes.mysql.failedWithRollback._003__execution_with_exception.class
                    };
                } else if ("failedWithoutRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.mysql.failedWithoutRollback._001__create_index.class,
                            io.flamingock.community.sql.changes.mysql.failedWithoutRollback._002__insert_document.class,
                            io.flamingock.community.sql.changes.mysql.failedWithoutRollback._003__execution_with_exception.class
                    };
                }
                break;
            case "sqlserver":
                if ("happyPath".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.sqlserver.happyPath._001__create_index.class,
                            io.flamingock.community.sql.changes.sqlserver.happyPath._002__insert_document.class,
                            io.flamingock.community.sql.changes.sqlserver.happyPath._003__insert_another_document.class
                    };
                } else if ("failedWithRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.sqlserver.failedWithRollback._001__create_index.class,
                            io.flamingock.community.sql.changes.sqlserver.failedWithRollback._002__insert_document.class,
                            io.flamingock.community.sql.changes.sqlserver.failedWithRollback._003__execution_with_exception.class
                    };
                } else if ("failedWithoutRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.sqlserver.failedWithoutRollback._001__create_index.class,
                            io.flamingock.community.sql.changes.sqlserver.failedWithoutRollback._002__insert_document.class,
                            io.flamingock.community.sql.changes.sqlserver.failedWithoutRollback._003__execution_with_exception.class
                    };
                }
                break;
            case "oracle":
                if ("happyPath".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.oracle.happyPath._001__create_index.class,
                            io.flamingock.community.sql.changes.oracle.happyPath._002__insert_document.class,
                            io.flamingock.community.sql.changes.oracle.happyPath._003__insert_another_document.class
                    };
                } else if ("failedWithRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.oracle.failedWithRollback._001__create_index.class,
                            io.flamingock.community.sql.changes.oracle.failedWithRollback._002__insert_document.class,
                            io.flamingock.community.sql.changes.oracle.failedWithRollback._003__execution_with_exception.class
                    };
                } else if ("failedWithoutRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.oracle.failedWithoutRollback._001__create_index.class,
                            io.flamingock.community.sql.changes.oracle.failedWithoutRollback._002__insert_document.class,
                            io.flamingock.community.sql.changes.oracle.failedWithoutRollback._003__execution_with_exception.class
                    };
                }
                break;
            case "postgresql":
                if ("happyPath".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.postgresql.happyPath._001__create_index.class,
                            io.flamingock.community.sql.changes.postgresql.happyPath._002__insert_document.class,
                            io.flamingock.community.sql.changes.postgresql.happyPath._003__insert_another_document.class
                    };
                } else if ("failedWithRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.postgresql.failedWithRollback._001__create_index.class,
                            io.flamingock.community.sql.changes.postgresql.failedWithRollback._002__insert_document.class,
                            io.flamingock.community.sql.changes.postgresql.failedWithRollback._003__execution_with_exception.class
                    };
                } else if ("failedWithoutRollback".equals(scenario)) {
                    return new Class<?>[]{
                            io.flamingock.community.sql.changes.postgresql.failedWithoutRollback._001__create_index.class,
                            io.flamingock.community.sql.changes.postgresql.failedWithoutRollback._002__insert_document.class,
                            io.flamingock.community.sql.changes.postgresql.failedWithoutRollback._003__execution_with_exception.class
                    };
                }
                break;
        }
        throw new IllegalArgumentException("Unsupported dialect/scenario: " + dialectName + "/" + scenario);
    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("When standalone runs the driver should persist the audit logs and the test data")
    void happyPathWithMockedPipeline(SqlDialect sqlDialect, String dialectName) throws Exception {
        TestContext context = setupTest(sqlDialect, dialectName);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
                Class<?>[] changeClasses = getChangeClasses(dialectName, "happyPath");

                mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                        new Trio<>(changeClasses[0], Collections.singletonList(Connection.class), null),
                        new Trio<>(changeClasses[1], Collections.singletonList(Connection.class), null),
                        new Trio<>(changeClasses[2], Collections.singletonList(Connection.class), null)
                ));

                SqlAuditStore auditStore = new SqlAuditStore(context.dataSource);
                SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);

                FlamingockFactory.getCommunityBuilder()
                        .setAuditStore(auditStore)
                        .addTargetSystem(targetSystem)
                        .build()
                        .run();
            }

            // Verify audit logs
            try (Connection conn = context.dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT task_id, state FROM " + CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME + " ORDER BY id ASC");
                 ResultSet rs = ps.executeQuery()) {

                String[] expectedTaskIds = {"create-index", "insert-document", "insert-another-document"};
                int recordCount = 0;
                int startedCount = 0;
                int appliedCount = 0;

                while (rs.next()) {
                    String taskId = rs.getString("task_id");
                    String state = rs.getString("state");
                    assertTrue(
                            java.util.Arrays.asList(expectedTaskIds).contains(taskId),
                            "Unexpected task_id: " + taskId
                    );
                    assertTrue(
                            state.equals("STARTED") || state.equals("APPLIED"),
                            "Unexpected state: " + state
                    );
                    if (state.equals("STARTED")) startedCount++;
                    if (state.equals("APPLIED")) appliedCount++;
                    recordCount++;
                }

                assertEquals(6, recordCount, "Audit log should have 6 records");
                assertEquals(3, startedCount, "Should have 3 STARTED records");
                assertEquals(3, appliedCount, "Should have 3 APPLIED records");
            }

            // Verify test data
            try (Connection conn = context.dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
                ps.setString(1, "test-client-Federico");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Federico", rs.getString("name"));
                }
                ps.setString(1, "test-client-Jorge");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Jorge", rs.getString("name"));
                }
            }

    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("When standalone runs the driver and execution fails (with rollback method) should persist all the audit logs up to the failed one (ROLLED_BACK)")
    void failedWithRollback(SqlDialect sqlDialect, String dialectName) throws Exception {
        TestContext context = setupTest(sqlDialect, dialectName);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
                Class<?>[] changeClasses = getChangeClasses(dialectName, "failedWithRollback");

                mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                        new Trio<>(changeClasses[0], Collections.singletonList(Connection.class), null),
                        new Trio<>(changeClasses[1], Collections.singletonList(Connection.class), null),
                        new Trio<>(changeClasses[2], Collections.singletonList(Connection.class), null)
                ));

                SqlAuditStore auditStore = new SqlAuditStore(context.dataSource);
                SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);

                assertThrows(PipelineExecutionException.class, () -> {
                    FlamingockFactory.getCommunityBuilder()
                            .setAuditStore(auditStore)
                            .addTargetSystem(targetSystem)
                            .build()
                            .run();
                });

                // Verify audit sequence
                try (Connection conn = context.dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT task_id, state FROM " + CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME + " ORDER BY id ASC");
                     ResultSet rs = ps.executeQuery()) {

                    assertTrue(rs.next());
                    assertEquals("create-index", rs.getString("task_id"));
                    assertEquals("STARTED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("create-index", rs.getString("task_id"));
                    assertEquals("APPLIED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("insert-document", rs.getString("task_id"));
                    assertEquals("STARTED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("insert-document", rs.getString("task_id"));
                    assertEquals("APPLIED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("execution-with-exception", rs.getString("task_id"));
                    assertEquals("STARTED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("execution-with-exception", rs.getString("task_id"));
                    assertEquals("FAILED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("execution-with-exception", rs.getString("task_id"));
                    assertEquals("ROLLED_BACK", rs.getString("state"));

                    assertFalse(rs.next());
                }

                // Verify index exists
                SqlAuditTestHelper.verifyIndexExists(context);

                // Verify partial data
                try (Connection conn = context.dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
                    ps.setString(1, "test-client-Federico");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Federico", rs.getString("name"));
                    }
                }

                try (Connection conn = context.dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
                    ps.setString(1, "test-client-Jorge");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertFalse(rs.next());
                    }
                }
            }

    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("When standalone runs the driver and execution fails (without rollback method) should persist all the audit logs up to the failed one (FAILED)")
    void failedWithoutRollback(SqlDialect sqlDialect, String dialectName) throws Exception {
        TestContext context = setupTest(sqlDialect, dialectName);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
                Class<?>[] changeClasses = getChangeClasses(dialectName, "failedWithoutRollback");

                mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                        new Trio<>(changeClasses[0], Collections.singletonList(Connection.class), null),
                        new Trio<>(changeClasses[1], Collections.singletonList(Connection.class), null),
                        new Trio<>(changeClasses[2], Collections.singletonList(Connection.class), null)
                ));

                SqlAuditStore auditStore = new SqlAuditStore(context.dataSource);
                SqlTargetSystem targetSystem = new SqlTargetSystem("sql", context.dataSource);

                assertThrows(PipelineExecutionException.class, () -> {
                    FlamingockFactory.getCommunityBuilder()
                            .setAuditStore(auditStore)
                            .addTargetSystem(targetSystem)
                            .build()
                            .run();
                });

                // Verify audit sequence
                try (Connection conn = context.dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT task_id, state FROM " + CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME + " ORDER BY id ASC");
                     ResultSet rs = ps.executeQuery()) {

                    assertTrue(rs.next());
                    assertEquals("create-index", rs.getString("task_id"));
                    assertEquals("STARTED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("create-index", rs.getString("task_id"));
                    assertEquals("APPLIED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("insert-document", rs.getString("task_id"));
                    assertEquals("STARTED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("insert-document", rs.getString("task_id"));
                    assertEquals("APPLIED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("execution-with-exception", rs.getString("task_id"));
                    assertEquals("STARTED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("execution-with-exception", rs.getString("task_id"));
                    assertEquals("FAILED", rs.getString("state"));

                    assertTrue(rs.next());
                    assertEquals("execution-with-exception", rs.getString("task_id"));
                    assertEquals("ROLLED_BACK", rs.getString("state"));

                    assertFalse(rs.next());
                }

                // Verify index exists and data state
                SqlAuditTestHelper.verifyIndexExists(context);
                verifyPartialDataState(context);
            }

    }

    private void verifyPartialDataState(TestContext context) throws SQLException {
        try (Connection conn = context.dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
            ps.setString(1, "test-client-Federico");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Federico", rs.getString("name"));
            }
        }

        try (Connection conn = context.dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
            ps.setString(1, "test-client-Jorge");
            try (ResultSet rs = ps.executeQuery()) {
                assertFalse(rs.next());
            }
        }
    }
}
