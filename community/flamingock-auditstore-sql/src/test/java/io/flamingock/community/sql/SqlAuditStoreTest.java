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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class SqlAuditStoreTest {

    static Stream<Arguments> dialectProvider() {
        return Stream.of(
                Arguments.of(SqlDialect.MYSQL, "mysql"),
                Arguments.of(SqlDialect.SQLSERVER, "sqlserver"),
                Arguments.of(SqlDialect.ORACLE, "oracle"),
                Arguments.of(SqlDialect.POSTGRESQL, "postgresql"),
                Arguments.of(SqlDialect.MARIADB, "mariadb"),
                Arguments.of(SqlDialect.H2, "h2")
                //Disabled due to issues with file-based databases in CI environments
                //Arguments.of(SqlDialect.SQLITE, "sqlite")
        );
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
            HikariConfig config = new HikariConfig();
            String dbFile = "test_" + System.currentTimeMillis() + ".db";
            config.setJdbcUrl("jdbc:sqlite:" + dbFile);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(30000);
            config.setMaxLifetime(0);

            config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;");

            DataSource dataSource = new HikariDataSource(config);
            SqlAuditTestHelper.createTables(dataSource, sqlDialect);

            return new TestContext(dataSource, null, SqlDialect.SQLITE);
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

    private void tearDown(TestContext context) throws SQLException {
        if (context.dataSource instanceof HikariDataSource) {
            ((HikariDataSource) context.dataSource).close();
        }
        if (context.container != null) {
            context.container.stop();
        }
    }

    private JdbcDatabaseContainer<?> createContainer(String dialectName) {
        switch (dialectName) {
            case "mysql":
                return new MySQLContainer<>("mysql:8.0")
                        .withDatabaseName("testdb")
                        .withUsername("testuser")
                        .withPassword("testpass");
            case "sqlserver":
                return new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2019-CU18-ubuntu-20.04")
                        .acceptLicense()
                        .withPassword("TestPass123!");
            case "oracle":
                OracleContainer oracleContainer = new OracleContainer(
                        DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart")
                                .asCompatibleSubstituteFor("gvenzl/oracle-xe"))
                        .withPassword("oracle123")
                        .withSharedMemorySize(1073741824L)
                        .withStartupTimeoutSeconds(900)
                        .withEnv("ORACLE_CHARACTERSET", "AL32UTF8");

                return new OracleContainer(
                        DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart")
                                .asCompatibleSubstituteFor("gvenzl/oracle-xe")) {
                    @Override
                    public String getDatabaseName() {
                        return "FREEPDB1";
                    }
                }
                        .withPassword("oracle123")
                        .withSharedMemorySize(1073741824L)
                        .withStartupTimeoutSeconds(900)
                        .withEnv("ORACLE_CHARACTERSET", "AL32UTF8");
            case "postgresql":
                return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
                        .withDatabaseName("testdb")
                        .withUsername("test")
                        .withPassword("test");
            case "mariadb":
                return new MariaDBContainer<>("mariadb:11.3.2")
                        .withDatabaseName("testdb")
                        .withUsername("testuser")
                        .withPassword("testpass");
            default:
                throw new IllegalArgumentException("Unsupported dialect: " + dialectName);
        }
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
        try {
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
        } finally {
            tearDown(context);
        }
    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("When standalone runs the driver and execution fails (with rollback method) should persist all the audit logs up to the failed one (ROLLED_BACK)")
    void failedWithRollback(SqlDialect sqlDialect, String dialectName) throws Exception {
        TestContext context = setupTest(sqlDialect, dialectName);
        try {
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
        } finally {
            tearDown(context);
        }
    }

    @ParameterizedTest
    @MethodSource("dialectProvider")
    @DisplayName("When standalone runs the driver and execution fails (without rollback method) should persist all the audit logs up to the failed one (FAILED)")
    void failedWithoutRollback(SqlDialect sqlDialect, String dialectName) throws Exception {
        TestContext context = setupTest(sqlDialect, dialectName);
        try {
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
        } finally {
            tearDown(context);
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
