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
package io.flamingock.store.sql.internal;

import io.flamingock.internal.common.sql.SqlDialectFactory;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.external.store.lock.LockStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Objects;

public final class SqlLockDialectHelper {

    final private SqlDialect sqlDialect;

    public SqlLockDialectHelper(Connection connection) throws SQLException {
        this.sqlDialect = SqlDialectFactory.getSqlDialect(connection);
    }

    public String getCreateTableSqlString(String tableName) {
        switch (sqlDialect) {
            case POSTGRESQL:
                return String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "\"key\" VARCHAR(255) PRIMARY KEY," +
                                "status VARCHAR(32)," +
                                "owner VARCHAR(255)," +
                                "expires_at TIMESTAMP" +
                                ")", tableName);
            case FIREBIRD:
                return String.format(
                        "CREATE TABLE %s (" +
                                "lock_key VARCHAR(255) PRIMARY KEY, " +
                                "status VARCHAR(32), " +
                                "owner VARCHAR(255), " +
                                "expires_at TIMESTAMP" +
                                ")",
                        tableName);
            case MYSQL:
            case MARIADB:
            case SQLITE:
            case H2:
                return String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "`key` VARCHAR(255) PRIMARY KEY," +
                                "status VARCHAR(32)," +
                                "owner VARCHAR(255)," +
                                "expires_at TIMESTAMP" +
                                ")", tableName);
            case SQLSERVER:
                return String.format(
                        "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%s' AND xtype='U') " +
                                "CREATE TABLE %s (" +
                                "[key] VARCHAR(255) PRIMARY KEY," +
                                "status VARCHAR(32)," +
                                "owner VARCHAR(255)," +
                                "expires_at DATETIME" +
                                ")", tableName, tableName);
            case SYBASE:
                return String.format(
                        "IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='%s' AND type='U') " +
                                "BEGIN " +
                                "   EXEC('CREATE TABLE %s (" +
                                "       lock_key VARCHAR(255) NOT NULL PRIMARY KEY, " +
                                "       status VARCHAR(32), " +
                                "       owner VARCHAR(255), " +
                                "       expires_at DATETIME" +
                                "   )') " +
                                "END",
                        tableName, tableName
                );
            case ORACLE:
                return String.format(
                        "BEGIN EXECUTE IMMEDIATE 'CREATE TABLE %s (" +
                                "\"key\" VARCHAR2(255) PRIMARY KEY," +
                                "status VARCHAR2(32)," +
                                "owner VARCHAR2(255)," +
                                "expires_at TIMESTAMP" +
                                ")'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF; END;", tableName);
            case DB2:
                return String.format(
                        "BEGIN " +
                                "DECLARE CONTINUE HANDLER FOR SQLSTATE '42710' BEGIN END; " +
                                "EXECUTE IMMEDIATE 'CREATE TABLE %s (" +
                                "lock_key VARCHAR(255) NOT NULL PRIMARY KEY, " +
                                "status VARCHAR(32), " +
                                "owner VARCHAR(255), " +
                                "expires_at TIMESTAMP)'; " +
                                "END", tableName);
            case INFORMIX:
                return String.format(
                        "CREATE TABLE %s (" +
                                "lock_key VARCHAR(255) PRIMARY KEY, " +
                                "status VARCHAR(32), " +
                                "owner VARCHAR(255), " +
                                "expires_at DATETIME YEAR TO FRACTION(3)" +
                                ")", tableName);
            default:
                throw new UnsupportedOperationException("Dialect not supported for CREATE TABLE: " + sqlDialect.name());
        }
    }

    public String getSelectLockSqlString(String tableName) {
        switch (sqlDialect) {
            case POSTGRESQL:
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
                        "MERGE INTO %s (`key`, status, owner, expires_at) KEY (`key`) VALUES (?, ?, ?, ?)",
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
                return String.format("UPDATE " + tableName + " SET status = ?, owner = ?, expires_at = ? WHERE lock_key = ?", tableName);
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

    public String getDeleteLockSqlString(String tableName) {
        if (Objects.requireNonNull(sqlDialect) == SqlDialect.POSTGRESQL) {
            return String.format("DELETE FROM %s WHERE \"key\" = ?", tableName);
        }
        if (sqlDialect == SqlDialect.INFORMIX || sqlDialect == SqlDialect.DB2) {
            return String.format("DELETE FROM %s WHERE lock_key = ?", tableName);
        }
        if (sqlDialect == SqlDialect.FIREBIRD) {
            return String.format("DELETE FROM %s WHERE lock_key = ?", tableName);
        }
        return String.format("DELETE FROM %s WHERE `key` = ?", tableName);
    }

    public void upsertLockEntry(Connection conn, String tableName, String key, String owner, LocalDateTime expiresAt) throws SQLException {
        String sql = getInsertOrUpdateLockSqlString(tableName);

        if (sqlDialect == SqlDialect.DB2) {
            // UPDATE first
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE " + tableName + " SET owner = ?, expires_at = ? WHERE lock_key = ?")) {
                update.setString(1, owner);
                update.setTimestamp(2, Timestamp.valueOf(expiresAt));
                update.setString(3, key);
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
            String updateSql = getInsertOrUpdateLockSqlString(tableName);
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
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


    public SqlDialect getSqlDialect() {
        return sqlDialect;
    }
}
