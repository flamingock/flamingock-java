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
import io.flamingock.internal.common.sql.SqlDialect;
import org.testcontainers.containers.*;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlAuditTestHelper {

    public static JdbcDatabaseContainer<?> createContainer(String dialectName) {
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

    public static void createTables(DataSource dataSource, SqlDialect dialect) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            dropTablesIfExist(conn, dialect);

            String createTestTable = getCreateTestTableSql(dialect);
            conn.createStatement().execute(createTestTable);

            String createLockTable = getCreateLockTableSql(dialect);
            conn.createStatement().execute(createLockTable);
        }
    }

    private static void dropTablesIfExist(Connection conn, SqlDialect dialect) throws SQLException {
        String[] tables = {"flamingockAuditLog", "test_table", "flamingockLock"};
        for (String table : tables) {
            try {
                String dropSql = getDropTableSql(table, dialect);
                conn.createStatement().execute(dropSql);
            } catch (SQLException e) {
                // Ignore if table doesn't exist
            }
        }
    }

    private static String getDropTableSql(String tableName, SqlDialect dialect) {
        if (dialect == SqlDialect.ORACLE) {
            return "DROP TABLE " + tableName + " CASCADE CONSTRAINTS";
        }
        return "DROP TABLE IF EXISTS " + tableName;
    }

    private static String getCreateTestTableSql(SqlDialect dialect) {
        switch (dialect) {
            case MYSQL:
            case SQLSERVER:
            case MARIADB:
                return "CREATE TABLE test_table (" +
                        "id VARCHAR(255) PRIMARY KEY, " +
                        "name VARCHAR(255), " +
                        "field1 VARCHAR(255), " +
                        "field2 VARCHAR(255))";
            case POSTGRESQL:
                return "CREATE TABLE test_table (" +
                        "id VARCHAR(255) PRIMARY KEY," +
                        "name VARCHAR(255)," +
                        "field1 VARCHAR(255)," +
                        "field2 VARCHAR(255))";
            case ORACLE:
                return "CREATE TABLE test_table (" +
                        "id VARCHAR2(255) PRIMARY KEY, " +
                        "name VARCHAR2(255), " +
                        "field1 VARCHAR2(255), " +
                        "field2 VARCHAR2(255))";
            default:
                throw new UnsupportedOperationException("Dialect not supported: " + dialect);
        }
    }

    private static String getCreateLockTableSql(SqlDialect dialect) {
        switch (dialect) {
            case MYSQL:
            case MARIADB:
                return "CREATE TABLE flamingockLock (" +
                        "`key` VARCHAR(255) PRIMARY KEY, " +
                        "status VARCHAR(32), " +
                        "owner VARCHAR(255), " +
                        "expires_at TIMESTAMP)";
            case POSTGRESQL:
                return "CREATE TABLE flamingockLock (" +
                        "\"key\" VARCHAR(255) PRIMARY KEY," +
                        "status VARCHAR(32)," +
                        "owner VARCHAR(255)," +
                        "expires_at TIMESTAMP)";
            case SQLSERVER:
                return "CREATE TABLE flamingockLock (" +
                        "[key] VARCHAR(255) PRIMARY KEY, " +
                        "status VARCHAR(32), " +
                        "owner VARCHAR(255), " +
                        "expires_at DATETIME)";
            case ORACLE:
                return "CREATE TABLE flamingockLock (" +
                        "\"key\" VARCHAR2(255) PRIMARY KEY, " +
                        "status VARCHAR2(32), " +
                        "owner VARCHAR2(255), " +
                        "expires_at TIMESTAMP)";
            default:
                throw new UnsupportedOperationException("Dialect not supported: " + dialect);
        }
    }

    public static DataSource createDataSource(JdbcDatabaseContainer<?> container) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setDriverClassName(container.getDriverClassName());
        return new HikariDataSource(config);
    }

    public static void verifyPartialDataState(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM test_table WHERE id = ?")) {
            ps.setString(1, "test-client-Federico");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"Federico".equals(rs.getString("name"))) {
                    throw new AssertionError("Federico not found");
                }
            }
            ps.setString(1, "test-client-Jorge");
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"Jorge".equals(rs.getString("name"))) {
                    throw new AssertionError("Jorge not found");
                }
            }
        }
    }

    private static String getIndexCheckSql(SqlDialect dialect) {
        switch (dialect) {
            case POSTGRESQL:
                return "SELECT indexname FROM pg_indexes WHERE indexname = ?";
            case MYSQL:
            case MARIADB:
                return "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_NAME = ? AND INDEX_NAME = ?";
            case ORACLE:
                return "SELECT INDEX_NAME FROM USER_INDEXES WHERE INDEX_NAME = ? AND TABLE_NAME = ?";
            case SQLSERVER:
            case SYBASE:
                return "SELECT name FROM sys.indexes WHERE name = ?";
            case H2:
            case HSQLDB:
            case DERBY:
            case SQLITE:
            default:
                return "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = ?";
        }
    }

    public static void verifyIndexExists(TestContext context) throws SQLException {
        try (Connection conn = context.dataSource.getConnection()) {
            String indexCheckSql = getIndexCheckSql(context.dialect);
            try (PreparedStatement ps = conn.prepareStatement(indexCheckSql)) {
                switch (context.dialect) {
                    case ORACLE:
                        ps.setString(1, "IDX_STANDALONE_INDEX");
                        ps.setString(2, "TEST_TABLE");
                        break;
                    case POSTGRESQL:
                        ps.setString(1, "idx_standalone_index");
                        break;
                    case MYSQL:
                    case MARIADB:
                        ps.setString(1, "test_table");
                        ps.setString(2, "idx_standalone_index");
                        break;
                    case SQLSERVER:
                    case SYBASE:
                        ps.setString(1, "idx_standalone_index");
                        break;
                    case H2:
                    case HSQLDB:
                    case DERBY:
                    case SQLITE:
                        ps.setString(1, "idx_standalone_index");
                        break;
                    default:
                        ps.setString(1, "idx_standalone_index");
                        break;
                }

                try (ResultSet rs = ps.executeQuery()) {
                    boolean indexExists = rs.next();
                    assertTrue(indexExists, "Index idx_standalone_index should exist");
                }
            }
        }
    }
}
