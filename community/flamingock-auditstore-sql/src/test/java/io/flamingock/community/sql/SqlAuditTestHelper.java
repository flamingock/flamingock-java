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

import io.flamingock.internal.common.sql.SqlDialect;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlAuditTestHelper {

    public static JdbcDatabaseContainer<?> createContainer(String dialectName) {
        return SharedSqlContainers.getContainer(dialectName);
    }

    public static DataSource createDataSource(JdbcDatabaseContainer<?> container) {
        return SharedSqlContainers.createDataSource(container);
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
            case SQLITE:
            case H2:
            case HSQLDB:
                return "CREATE TABLE test_table (" +
                        "id VARCHAR(255) PRIMARY KEY, " +
                        "name VARCHAR(255), " +
                        "field1 VARCHAR(255), " +
                        "field2 VARCHAR(255))";
            case POSTGRESQL:
            case DB2:
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
            case INFORMIX:
                return "CREATE TABLE test_table (" +
                        "id VARCHAR(100) PRIMARY KEY, " +
                        "name VARCHAR(100), " +
                        "field1 VARCHAR(100), " +
                        "field2 VARCHAR(100))";

            default:
                throw new UnsupportedOperationException("Dialect not supported: " + dialect);
        }
    }

    private static String getCreateLockTableSql(SqlDialect dialect) {
        switch (dialect) {
            case MYSQL:
            case MARIADB:
            case SQLITE:
            case H2:
            case HSQLDB:
            case FIREBIRD:
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
            case DB2:
                // Use lock_key in DB2 test DDL to match runtime helper and avoid reserved-key issues
                return "CREATE TABLE flamingockLock (" +
                        "lock_key VARCHAR(255) PRIMARY KEY, " +
                        "status VARCHAR(32), " +
                        "owner VARCHAR(255), " +
                        "expires_at TIMESTAMP)";
            case INFORMIX:
                return "CREATE TABLE flamingockLock (" +
                        "lock_key VARCHAR(255) PRIMARY KEY," +
                        "status VARCHAR(32)," +
                        "owner VARCHAR(255)," +
                        "expires_at DATETIME YEAR TO FRACTION(3)" +
                        ")";
            default:
                throw new UnsupportedOperationException("Dialect not supported: " + dialect);
        }
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
            case SQLITE:
                // SQLite uses sqlite_master for indexes
                return "SELECT name FROM sqlite_master WHERE type='index' AND name = ?";
            case INFORMIX:
                // Informix stores index names in sysindexes
                return "SELECT idxname FROM sysindexes WHERE idxname = ?";
            case H2:
            case HSQLDB:
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
                    case DB2:
                        ps.setString(1, "IDX_STANDALONE_INDEX");
                        break;
                    case MYSQL:
                    case MARIADB:
                        ps.setString(1, "test_table");
                        ps.setString(2, "idx_standalone_index");
                        break;
                    case INFORMIX:
                        ps.setString(1, "idx_standalone_index");
                        break;
                    case H2:
                    case HSQLDB:
                        ps.setString(1, "IDX_STANDALONE_INDEX");
                        break;
                    case POSTGRESQL:
                    case SQLSERVER:
                    case SYBASE:
                    case SQLITE:
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
