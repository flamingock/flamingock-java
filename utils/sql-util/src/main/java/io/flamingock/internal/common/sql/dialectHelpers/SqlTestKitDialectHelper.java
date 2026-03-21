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
package io.flamingock.internal.common.sql.dialectHelpers;

import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.common.sql.SqlDialectFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class SqlTestKitDialectHelper {

    final private SqlDialect sqlDialect;

    public SqlTestKitDialectHelper(Connection connection) {
        this.sqlDialect = SqlDialectFactory.getSqlDialect(connection);
    }

    public void disableForeignKeyChecks(Connection conn) throws SQLException {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("SET FOREIGN_KEY_CHECKS=0");
                }
                break;
            case SQLITE:
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("PRAGMA foreign_keys = OFF");
                }
                break;
            case H2:
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("SET REFERENTIAL_INTEGRITY FALSE");
                }
                break;
            case SQLSERVER:
            case SYBASE:
            case FIREBIRD:
                dropAllForeignKeys(conn);
                break;
            default:
                break;
        }
    }

    public void enableForeignKeyChecks(Connection conn) throws SQLException {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("SET FOREIGN_KEY_CHECKS=1");
                }
                break;
            case SQLITE:
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("PRAGMA foreign_keys = ON");
                }
                break;
            case H2:
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("SET REFERENTIAL_INTEGRITY TRUE");
                }
                break;
            default:
                break;
        }
    }

    private void dropAllForeignKeys(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String schema = conn.getSchema();
        String catalog = conn.getCatalog();

        try (ResultSet tables = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                try (ResultSet fks = meta.getExportedKeys(catalog, schema, tableName)) {
                    while (fks.next()) {
                        String fkName = fks.getString("FK_NAME");
                        String fkTable = fks.getString("FKTABLE_NAME");
                        if (fkName != null) {
                            try (Statement stmt = conn.createStatement()) {
                                stmt.executeUpdate(
                                    "ALTER TABLE " + fkTable + " DROP CONSTRAINT " + fkName);
                            } catch (SQLException e) {
                            }
                        }
                    }
                }
            }
        }
    }

    public String getDropTableSql(String tableName) {
        switch (sqlDialect) {
            case POSTGRESQL:
                return "DROP TABLE IF EXISTS " + tableName + " CASCADE";
            case ORACLE:
                return "DROP TABLE " + tableName + " CASCADE CONSTRAINTS PURGE";
            case SQLSERVER:
                return "IF OBJECT_ID('" + tableName + "', 'U') IS NOT NULL DROP TABLE " + tableName;
            case SYBASE:
                return "DROP TABLE " + tableName;
            default:
                // MYSQL, MARIADB, SQLITE, H2, DB2, INFORMIX, FIREBIRD
                return "DROP TABLE IF EXISTS " + tableName;
        }
    }

    public List<String> getUserTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        String schema = conn.getSchema();
        String catalog = conn.getCatalog();

        if (sqlDialect == SqlDialect.INFORMIX && schema == null) {
            schema = meta.getUserName();
        }

        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String upperName = tableName.toUpperCase();
                if (sqlDialect == SqlDialect.FIREBIRD && (upperName.startsWith("RDB$") || upperName.startsWith("MON$") || upperName.startsWith("SEC$"))) {
                    continue;
                }
                tables.add(tableName);
            }
        }
        return tables;
    }

    public SqlDialect getSqlDialect() {
        return sqlDialect;
    }
}
