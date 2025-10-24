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
package io.flamingock.community.sql.internal;

import io.flamingock.internal.common.sql.AbstractSqlDialectHelper;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.store.lock.LockStatus;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Objects;

public final class SqlLockDialectHelper extends AbstractSqlDialectHelper {

    public SqlLockDialectHelper(DataSource dataSource) {
        super(dataSource);
    }

    public SqlLockDialectHelper(SqlDialect dialect) {
        super(dialect);
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
            case MYSQL:
            case MARIADB:
            case SQLITE:
            case H2:
            case HSQLDB:
            case FIREBIRD:
            case INFORMIX:
                return String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "`key` VARCHAR(255) PRIMARY KEY," +
                                "status VARCHAR(32)," +
                                "owner VARCHAR(255)," +
                                "expires_at TIMESTAMP" +
                                ")", tableName);
            case SQLSERVER:
            case SYBASE:
                return String.format(
                        "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%s' AND xtype='U') " +
                                "CREATE TABLE %s (" +
                                "[key] VARCHAR(255) PRIMARY KEY," +
                                "status VARCHAR(32)," +
                                "owner VARCHAR(255)," +
                                "expires_at DATETIME" +
                                ")", tableName, tableName);
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
                        "CREATE TABLE %s (" +
                                "\"key\" VARCHAR(255) PRIMARY KEY," +
                                "status VARCHAR(32)," +
                                "owner VARCHAR(255)," +
                                "expires_at TIMESTAMP" +
                                ")", tableName);
            default:
                throw new UnsupportedOperationException("Dialect not supported for CREATE TABLE: " + sqlDialect.name());
        }
    }

    public String getSelectLockSqlString(String tableName) {
        switch (sqlDialect) {
            case POSTGRESQL:
                return String.format("SELECT \"key\", status, owner, expires_at FROM %s WHERE \"key\" = ?", tableName);
            case SQLSERVER:
            case SYBASE:
                return String.format("SELECT [key], status, owner, expires_at FROM %s WITH (UPDLOCK, ROWLOCK) WHERE [key] = ?", tableName);
            case ORACLE:
                return String.format("SELECT \"key\", status, owner, expires_at FROM %s WHERE \"key\" = ? FOR UPDATE", tableName);
            default:
                return String.format("SELECT `key`, status, owner, expires_at FROM %s WHERE `key` = ?", tableName);
        }
    }

    public String getInsertOrUpdateLockSqlString(String tableName) {
        switch (sqlDialect) {
            case MYSQL:
            case MARIADB:
            case INFORMIX:
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
            case SYBASE:
                return String.format(
                        "BEGIN TRANSACTION; " +
                                "UPDATE %s SET status = ?, owner = ?, expires_at = ? WHERE [key] = ?; " +
                                "IF @@ROWCOUNT = 0 " +
                                "BEGIN " +
                                "INSERT INTO %s ([key], status, owner, expires_at) VALUES (?, ?, ?, ?) " +
                                "END; " +
                                "COMMIT TRANSACTION;",
                        tableName, tableName);
            case ORACLE:
                return String.format(
                        "MERGE INTO %s t USING (SELECT ? AS \"key\", ? AS status, ? AS owner, ? AS expires_at FROM dual) s " +
                                "ON (t.\"key\" = s.\"key\") " +
                                "WHEN MATCHED THEN UPDATE SET t.status = s.status, t.owner = s.owner, t.expires_at = s.expires_at " +
                                "WHEN NOT MATCHED THEN INSERT (\"key\", status, owner, expires_at) VALUES (s.\"key\", s.status, s.owner, s.expires_at)",
                        tableName);
            case H2:
            case HSQLDB:
                return String.format(
                        "MERGE INTO %s (`key`, status, owner, expires_at) KEY (`key`) VALUES (?, ?, ?, ?)",
                        tableName);
            case DB2:
                return String.format(
                        "MERGE INTO %s USING (SELECT ? AS \"key\", ? AS status, ? AS owner, ? AS expires_at FROM SYSIBM.SYSDUMMY1) AS src " +
                                "ON (%s.\"key\" = src.\"key\") " +
                                "WHEN MATCHED THEN UPDATE SET status = src.status, owner = src.owner, expires_at = src.expires_at " +
                                "WHEN NOT MATCHED THEN INSERT (\"key\", status, owner, expires_at) VALUES (src.\"key\", src.status, src.owner, src.expires_at)",
                        tableName, tableName);
            case FIREBIRD:
                return String.format(
                        "UPDATE OR INSERT INTO %s (`key`, status, owner, expires_at) VALUES (?, ?, ?, ?) MATCHING (`key`)",
                        tableName);
            default:
                throw new UnsupportedOperationException("Dialect not supported for upsert: " + sqlDialect.name());
        }
    }

    public String getDeleteLockSqlString(String tableName) {
        if (Objects.requireNonNull(sqlDialect) == SqlDialect.POSTGRESQL) {
            return String.format("DELETE FROM %s WHERE \"key\" = ?", tableName);
        }
        return String.format("DELETE FROM %s WHERE `key` = ?", tableName);
    }

    public void upsertLockEntry(Connection conn, String tableName, String key, String owner, LocalDateTime expiresAt) throws SQLException {
        String sql = getInsertOrUpdateLockSqlString(tableName);

        if (getSqlDialect() == SqlDialect.SQLSERVER || getSqlDialect() == SqlDialect.SYBASE) {
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
        } else {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key);
                ps.setString(2, LockStatus.LOCK_HELD.name());
                ps.setString(3, owner);
                ps.setTimestamp(4, Timestamp.valueOf(expiresAt));
                ps.executeUpdate();
            }
        }
    }

    public SqlDialect getSqlDialect() {
        return sqlDialect;
    }
}
