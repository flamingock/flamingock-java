/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.template.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.store.sql.SqlAuditStore;
import io.flamingock.targetsystem.sql.SqlTargetSystem;
import io.flamingock.internal.core.builder.FlamingockFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableFlamingock(configFile = "flamingock/pipeline.yaml")
class SqlTemplateTest {

    private static final String TEST_TABLE = "test_users";

    private static DataSource dataSource;

    @BeforeAll
    static void beforeAll() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        dataSource = new HikariDataSource(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Cleanup test table if exists
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TEST_TABLE);
                // Also clean Flamingock audit tables
                stmt.execute("DROP TABLE IF EXISTS flamingockAuditLog");
                stmt.execute("DROP TABLE IF EXISTS flamingockLock");
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    @DisplayName("WHEN sql target system THEN runs fine with Flamingock")
    void happyPath() throws Exception {
        SqlTargetSystem sqlTargetSystem = new SqlTargetSystem("sql", dataSource);
        FlamingockFactory.getCommunityBuilder()
                .setAuditStore(SqlAuditStore.from(sqlTargetSystem))
                .addTargetSystem(sqlTargetSystem)
                .build()
                .run();

        // Verify table was created and data inserted
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TEST_TABLE)) {

            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }

        // Verify specific data
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM " + TEST_TABLE + " ORDER BY id")) {

            assertTrue(rs.next());
            assertEquals("Admin", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("backup", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("text;with;semi", rs.getString(1));
        }
    }
}
