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
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.internal.util.Trio;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.targetsystem.mysql.SqlTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class SqlAuditStoreTest {

    private static DataSource dataSource;

    @Container
    public static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @BeforeEach
    void setUp() throws SQLException {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mysqlContainer.getJdbcUrl());
        config.setUsername(mysqlContainer.getUsername());
        config.setPassword(mysqlContainer.getPassword());
        config.setDriverClassName(mysqlContainer.getDriverClassName());
        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("DROP TABLE IF EXISTS flamingockAuditLog");
            conn.createStatement().execute("DROP TABLE IF EXISTS test_table");
            conn.createStatement().execute("DROP TABLE IF EXISTS flamingockLock");
            conn.createStatement().execute(
                    "CREATE TABLE test_table (" +
                            "id VARCHAR(255) PRIMARY KEY, " +
                            "name VARCHAR(255), " +
                            "field1 VARCHAR(255), " +
                            "field2 VARCHAR(255))"
            );
            conn.createStatement().execute(
                    "CREATE TABLE flamingockLock (" +
                            "`key` VARCHAR(255) PRIMARY KEY, " +
                            "status VARCHAR(32), " +
                            "owner VARCHAR(255), " +
                            "expires_at TIMESTAMP)"
            );
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Drop index if exists
            try (ResultSet rs = conn.getMetaData().getIndexInfo(null, null, "test_table", false, false)) {
                while (rs.next()) {
                    String idxName = rs.getString("INDEX_NAME");
                    if ("idx_standalone_index".equals(idxName)) {
                        conn.createStatement().execute("DROP INDEX idx_standalone_index ON test_table");
                        break;
                    }
                }
            }
            conn.createStatement().execute("DROP TABLE IF EXISTS test_table");
            conn.createStatement().execute("DROP TABLE IF EXISTS flamingockLock");
            conn.createStatement().execute("DROP TABLE IF EXISTS flamingockAuditLog");
        }
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    @DisplayName("When standalone runs the driver should persist the audit logs and the test data")
    void happyPathWithMockedPipeline() throws Exception {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(io.flamingock.community.sql.changes.happyPath._001__create_index.class, Collections.singletonList(Connection.class), null),
                    new Trio<>(io.flamingock.community.sql.changes.happyPath._002__insert_document.class, Collections.singletonList(Connection.class), null),
                    new Trio<>(io.flamingock.community.sql.changes.happyPath._003__insert_another_document.class, Collections.singletonList(Connection.class), null)
            ));

            SqlAuditStore auditStore = new SqlAuditStore(dataSource);
            SqlTargetSystem targetSystem = new SqlTargetSystem("sql", dataSource);

            FlamingockFactory.getCommunityBuilder()
                    .setAuditStore(auditStore)
                    .addTargetSystem(targetSystem)
                    .build()
                    .run();
        }

        try (Connection conn = dataSource.getConnection();
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

        try (Connection conn = dataSource.getConnection();
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


    @Test
    @DisplayName("When standalone runs the driver and execution fails (with rollback method) should persist all the audit logs up to the failed one (ROLLED_BACK)")
    void failedWithRollback() throws Exception {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new Trio<>(io.flamingock.community.sql.changes.failedWithRollback._001__create_index.class, Collections.singletonList(Connection.class), null),
                            new Trio<>(io.flamingock.community.sql.changes.failedWithRollback._002__insert_document.class, Collections.singletonList(Connection.class), null),
                            new Trio<>(io.flamingock.community.sql.changes.failedWithRollback._003__execution_with_exception.class, Collections.singletonList(Connection.class), null)
                    )
            );

            SqlAuditStore auditStore = new SqlAuditStore(dataSource);
            SqlTargetSystem targetSystem = new SqlTargetSystem("sql", dataSource);

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        .setAuditStore(auditStore)
                        .addTargetSystem(targetSystem)
                        .build()
                        .run();
            });

            try (Connection conn = dataSource.getConnection();
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


            try (Connection conn = dataSource.getConnection();
                 ResultSet rs = conn.getMetaData().getIndexInfo(null, null, "test_table", false, false)) {
                boolean found = false;
                while (rs.next()) {
                    if ("idx_standalone_index".equals(rs.getString("INDEX_NAME"))) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found, "Index idx_standalone_index should exist");
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
                ps.setString(1, "test-client-Federico");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Federico", rs.getString("name"));
                }
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
                ps.setString(1, "test-client-Jorge");
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse(rs.next());
                }
            }

        }
    }

    @Test
    @DisplayName("When standalone runs the driver and execution fails (without rollback method) should persist all the audit logs up to the failed one (FAILED)")
    void failedWithoutRollback() throws SQLException {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new Trio<>(io.flamingock.community.sql.changes.failedWithoutRollback._001__create_index.class, Collections.singletonList(Connection.class), null),
                            new Trio<>(io.flamingock.community.sql.changes.failedWithoutRollback._002__insert_document.class, Collections.singletonList(Connection.class), null),
                            new Trio<>(io.flamingock.community.sql.changes.failedWithoutRollback._003__execution_with_exception.class, Collections.singletonList(Connection.class), null)
                    )
            );

            SqlAuditStore auditStore = new SqlAuditStore(dataSource);
            SqlTargetSystem targetSystem = new SqlTargetSystem("sql", dataSource);

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        .setAuditStore(auditStore)
                        .addTargetSystem(targetSystem)
                        .build()
                        .run();
            });


            try (Connection conn = dataSource.getConnection();
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

            try (Connection conn = dataSource.getConnection();
                 ResultSet rs = conn.getMetaData().getIndexInfo(null, null, "test_table", false, false)) {
                boolean found = false;
                while (rs.next()) {
                    if ("idx_standalone_index".equals(rs.getString("INDEX_NAME"))) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found, "Index idx_standalone_index should exist");
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
                ps.setString(1, "test-client-Federico");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Federico", rs.getString("name"));
                }
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
                ps.setString(1, "test-client-Jorge");
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse(rs.next());
                }
            }
        }
    }
}
