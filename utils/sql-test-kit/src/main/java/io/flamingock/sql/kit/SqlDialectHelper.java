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
package io.flamingock.sql.kit;

import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.common.sql.SqlDialectFactory;
import io.flamingock.internal.core.external.store.lock.LockStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class SqlDialectHelper {

    final private SqlDialect sqlDialect;

    public SqlDialectHelper(Connection connection) {
        this.sqlDialect = SqlDialectFactory.getSqlDialect(connection);
    }

    public String getInsertSqlString(String tableName) {
        return String.format(
            "INSERT INTO %s (" +
                "execution_id, stage_id, change_id, author, created_at, state, invoked_class, invoked_method, source_file, metadata, " +
                "execution_millis, execution_hostname, error_trace, type, tx_strategy, target_system_id, change_order, recovery_strategy, transaction_flag, system_change" +
                ") VALUES (" +
                "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?" +
                ")", tableName);
    }

    public String getSelectHistorySqlString(String tableName) {
            return String.format(
                "SELECT execution_id, stage_id, change_id, author, created_at, state, type, invoked_class, invoked_method, source_file, " +
                    "execution_millis, execution_hostname, metadata, system_change, error_trace, tx_strategy, target_system_id, change_order, recovery_strategy, transaction_flag " +
                    "FROM %s " +
                    "ORDER BY id ASC", tableName);
        }
    public String getSelectHistoryByChangeIdSqlString(String tableName) {
        return String.format(
            "SELECT execution_id, stage_id, change_id, author, created_at, state, type, invoked_class, invoked_method, source_file, " +
                "execution_millis, execution_hostname, metadata, system_change, error_trace, tx_strategy, target_system_id, change_order, recovery_strategy, transaction_flag " +
                "FROM %s " +
                "WHERE change_id = ? " +
                "ORDER BY id ASC", tableName);
    }

    public String getCountByStatusSqlString(String tableName) {
        return String.format(
            "SELECT COUNT(change_id) " +
                "FROM %s " +
                "WHERE state = ?", tableName);
    }

    public String getDeleteAllSqlString(String tableName) {
        return String.format("DELETE FROM %s", tableName);
    }



    public String getInsertOrUpdateLockSqlString(String tableName) {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
                return String.format(
                    "INSERT INTO %s (`key`, status, owner, expires_at) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE status = VALUES(status), owner = VALUES(owner), expires_at = VALUES(expires_at)",
                    tableName);
            case POSTGRESQL:
                return String.format(
                    "INSERT INTO %s (\"key\", status, owner, expires_at) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT (\"key\") DO UPDATE SET status = EXCLUDED.status, owner = EXCLUDED.owner, expires_at = EXCLUDED.expires_at",
                    tableName);
            case SQLITE:
                return String.format(
                    "INSERT OR REPLACE INTO %s (`key`, status, owner, expires_at) VALUES (?, ?, ?, ?)",
                    tableName);
            case SQLSERVER:
                return String.format(
                    "BEGIN TRANSACTION; " +
                        "UPDATE %s SET status = ?, owner = ?, expires_at = ? WHERE [key] = ?; " +
                        "IF @@ROWCOUNT = 0 " +
                        "BEGIN " +
                        "INSERT INTO %s ([key], status, owner, expires_at) VALUES (?, ?, ?, ?) " +
                        "END; " +
                        "COMMIT TRANSACTION;",
                    tableName, tableName);
            case SYBASE:
                return String.format(
                    "BEGIN TRAN " +
                        "UPDATE %s SET status = ?, owner = ?, expires_at = ? WHERE lock_key = ?; " +
                        "IF @@ROWCOUNT = 0 " +
                        "BEGIN " +
                        "   INSERT INTO %s (lock_key, status, owner, expires_at) VALUES (?, ?, ?, ?); " +
                        "END " +
                        "COMMIT TRAN",
                    tableName, tableName
                );
            case ORACLE:
                return String.format(
                    "MERGE INTO %s t USING (SELECT ? AS \"key\", ? AS status, ? AS owner, ? AS expires_at FROM dual) s " +
                        "ON (t.\"key\" = s.\"key\") " +
                        "WHEN MATCHED THEN UPDATE SET t.status = s.status, t.owner = s.owner, t.expires_at = s.expires_at " +
                        "WHEN NOT MATCHED THEN INSERT (\"key\", status, owner, expires_at) VALUES (s.\"key\", s.status, s.owner, s.expires_at)",
                    tableName);
            case H2:
                return String.format(
                    "MERGE INTO %s (\"key\", status, owner, expires_at) KEY (\"key\") VALUES (?, ?, ?, ?)",
                    tableName);
            case DB2:
                // Use a VALUES-derived table and a target alias for DB2 to avoid parsing issues
                return String.format(
                    "MERGE INTO %s tgt USING (VALUES (?, ?, ?, ?)) src(lock_key, status, owner, expires_at) " +
                        "ON (tgt.lock_key = src.lock_key) " +
                        "WHEN MATCHED THEN UPDATE SET status = src.status, owner = src.owner, expires_at = src.expires_at " +
                        "WHEN NOT MATCHED THEN INSERT (lock_key, status, owner, expires_at) VALUES (src.lock_key, src.status, src.owner, src.expires_at)",
                    tableName);
            case FIREBIRD:
                return String.format("UPDATE %s SET status = ?, owner = ?, expires_at = ? WHERE lock_key = ?", tableName);
            case INFORMIX:
                // Informix doesn't support ON DUPLICATE KEY UPDATE
                // Use a procedural approach similar to SQL Server
                return String.format(
                    "UPDATE %s SET status = ?, owner = ?, expires_at = ? WHERE lock_key = ?; " +
                        "INSERT INTO %s (lock_key, status, owner, expires_at) " +
                        "SELECT ?, ?, ?, ? FROM sysmaster:sysdual " +
                        "WHERE NOT EXISTS (SELECT 1 FROM %s WHERE lock_key = ?)",
                    tableName, tableName, tableName);
            default:
                throw new UnsupportedOperationException("Dialect not supported for upsert: " + sqlDialect.name());
        }
    }

    public void upsertLockEntry(Connection conn, String tableName, String key, String owner, LocalDateTime expiresAt) throws SQLException {
        String sql = getInsertOrUpdateLockSqlString(tableName);

        if (sqlDialect == SqlDialect.DB2) {
            // UPDATE first
            try (PreparedStatement update = conn.prepareStatement(
                "UPDATE " + tableName + " SET status = ?, owner = ?, expires_at = ? WHERE lock_key = ?")) {
                update.setString(1, LockStatus.LOCK_HELD.name());
                update.setString(2, owner);
                update.setTimestamp(3, Timestamp.valueOf(expiresAt));
                update.setString(4, key);
                int updated = update.executeUpdate();
                if (updated > 0) {
                    return;
                }
            }

            // If no row updated, try INSERT
            try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO " + tableName + " (lock_key, status, owner, expires_at) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, key);
                insert.setString(2, LockStatus.LOCK_HELD.name());
                insert.setString(3, owner);
                insert.setTimestamp(4, Timestamp.valueOf(expiresAt));
                insert.executeUpdate();
            }
            return;
        }

        if (getSqlDialect() == SqlDialect.INFORMIX) {
            // Try UPDATE first
            try (PreparedStatement update = conn.prepareStatement(
                "UPDATE " + tableName + " SET status = ?, owner = ?, expires_at = ? WHERE lock_key = ?")) {
                update.setString(1, LockStatus.LOCK_HELD.name());
                update.setString(2, owner);
                update.setTimestamp(3, Timestamp.valueOf(expiresAt));
                update.setString(4, key);
                int updated = update.executeUpdate();
                if (updated > 0) {
                    return;
                }
            }

            // If no row updated, try INSERT
            try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO " + tableName + " (lock_key, status, owner, expires_at) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, key);
                insert.setString(2, LockStatus.LOCK_HELD.name());
                insert.setString(3, owner);
                insert.setTimestamp(4, Timestamp.valueOf(expiresAt));
                insert.executeUpdate();
            }
            return;
        }

        if (getSqlDialect() == SqlDialect.SQLSERVER) {
            // For SQL Server/Sybase, use Statement and format SQL
            try (Statement stmt = conn.createStatement()) {
                String formattedSql = sql
                    .replaceFirst("\\?", "'" + LockStatus.LOCK_HELD.name() + "'")
                    .replaceFirst("\\?", "'" + owner + "'")
                    .replaceFirst("\\?", "'" + Timestamp.valueOf(expiresAt) + "'")
                    .replaceFirst("\\?", "'" + key + "'")
                    .replaceFirst("\\?", "'" + key + "'")
                    .replaceFirst("\\?", "'" + LockStatus.LOCK_HELD.name() + "'")
                    .replaceFirst("\\?", "'" + owner + "'")
                    .replaceFirst("\\?", "'" + Timestamp.valueOf(expiresAt) + "'");
                stmt.execute(formattedSql);
            }
            return;
        }

        if (sqlDialect == SqlDialect.FIREBIRD) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, LockStatus.LOCK_HELD.name());
                ps.setString(2, owner);
                ps.setTimestamp(3, Timestamp.valueOf(expiresAt));
                ps.setString(4, key);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    String insertSql = "INSERT INTO " + tableName + " (lock_key, status, owner, expires_at) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                        ins.setString(1, key);
                        ins.setString(2, LockStatus.LOCK_HELD.name());
                        ins.setString(3, owner);
                        ins.setTimestamp(4, Timestamp.valueOf(expiresAt));
                        ins.executeUpdate();
                    }
                }
            }
            return;
        }

        if (sqlDialect == SqlDialect.SYBASE) {
            // The lock was already deleted in acquireLockQuery for Sybase
            try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO " + tableName + " (lock_key, status, owner, expires_at) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, key);
                insert.setString(2, LockStatus.LOCK_HELD.name());
                insert.setString(3, owner);
                insert.setTimestamp(4, Timestamp.valueOf(expiresAt));
                insert.executeUpdate();
            }
            return;
        }


        // Default case for other dialects
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, LockStatus.LOCK_HELD.name());
            ps.setString(3, owner);
            ps.setTimestamp(4, Timestamp.valueOf(expiresAt));
            ps.executeUpdate();
        }
    }

    public String getSelectLockSqlString(String tableName) {
        switch (sqlDialect) {
            case POSTGRESQL:
            case H2:
                return String.format("SELECT \"key\", status, owner, expires_at FROM %s WHERE \"key\" = ?", tableName);
            case DB2:
                // Select lock_key as the first column (getLockEntry expects rs.getString(1) to be the key)
                return String.format("SELECT lock_key, status, owner, expires_at FROM %s WHERE lock_key = ?", tableName);
            case SQLSERVER:
                return String.format("SELECT [key], status, owner, expires_at FROM %s WITH (UPDLOCK, ROWLOCK) WHERE [key] = ?", tableName);
            case SYBASE:
                return String.format(
                    "SELECT lock_key, status, owner, expires_at " +
                        "FROM %s HOLDLOCK " +
                        "WHERE lock_key = ?",
                    tableName
                );            case ORACLE:
                return String.format("SELECT \"key\", status, owner, expires_at FROM %s WHERE \"key\" = ? FOR UPDATE", tableName);
            case INFORMIX:
                return String.format("SELECT lock_key, status, owner, expires_at FROM %s WHERE lock_key = ?", tableName);
            case FIREBIRD:
                return String.format("SELECT lock_key, status, owner, expires_at FROM %s WHERE lock_key = ?", tableName);
            default:
                return String.format("SELECT `key`, status, owner, expires_at FROM %s WHERE `key` = ?", tableName);
        }
    }

    public String getSelectAllLocksSqlString(String tableName) {
        switch (sqlDialect) {
            case POSTGRESQL:
            case H2:
                return String.format("SELECT \"key\", status, owner, expires_at FROM %s", tableName);
            case SQLSERVER:
                return String.format("SELECT [key], status, owner, expires_at FROM %s WITH (UPDLOCK, ROWLOCK)", tableName);
            case SYBASE:
                return String.format(
                    "SELECT lock_key, status, owner, expires_at " +
                        "FROM %s HOLDLOCK",
                    tableName
                );
            case ORACLE:
                return String.format("SELECT \"key\", status, owner, expires_at FROM %s FOR UPDATE", tableName);
            case DB2:
            case INFORMIX:
            case FIREBIRD:
                return String.format("SELECT lock_key, status, owner, expires_at FROM %s", tableName);
            default:
                return String.format("SELECT `key`, status, owner, expires_at FROM %s", tableName);
        }
    }

    public String getDeleteLockSqlString(String tableName) {
        switch (sqlDialect) {
            case POSTGRESQL:
                return String.format("DELETE FROM %s WHERE \"key\" = ?", tableName);
            case INFORMIX:
            case DB2:
            case FIREBIRD:
            case SYBASE:
                return String.format("DELETE FROM %s WHERE lock_key = ?", tableName);
            case SQLSERVER:
                return String.format("DELETE FROM %s WHERE [key] = ?", tableName);
            case ORACLE:
                return String.format("DELETE FROM %s WHERE \"key\" = ?", tableName);
            default: // MYSQL, MARIADB, SQLITE, H2
                return String.format("DELETE FROM %s WHERE `key` = ?", tableName);
        }
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
                // No hay un comando global; hay que eliminar las FK constraints antes de dropear tablas
                dropAllForeignKeys(conn);
                break;
            default:
                // POSTGRESQL, ORACLE, DB2, INFORMIX: el DROP maneja dependencias por sí solo
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
        // Para SQL Server, Sybase y Firebird: eliminar FK constraints de todas las tablas de usuario
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
                                // Ignorar si ya fue eliminado
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
                // IF OBJECT_ID funciona desde SQL Server 2005, más compatible que IF EXISTS (2016+)
                return "IF OBJECT_ID('" + tableName + "', 'U') IS NOT NULL DROP TABLE " + tableName;
            case SYBASE:
                // Sybase no tiene IF EXISTS en DROP TABLE
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

        // Para Informix: getSchema() devuelve null, usar el nombre de usuario como schema
        if (sqlDialect == SqlDialect.INFORMIX && schema == null) {
            schema = meta.getUserName();
        }

        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                // Para Firebird: filtrar tablas del sistema (RDB$, MON$, SEC$)
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
