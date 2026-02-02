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

import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.external.store.lock.LockServiceException;
import io.flamingock.internal.core.external.store.lock.LockStatus;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockEntry;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.util.id.RunnerId;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;

public class SqlLockService implements CommunityLockService {

    private final DataSource dataSource;
    private final String lockRepositoryName;
    private SqlLockDialectHelper dialectHelper = null;

    public SqlLockService(DataSource dataSource, String lockRepositoryName) {
        this.dataSource = dataSource;
        this.lockRepositoryName = lockRepositoryName;
    }

    public void initialize(boolean autoCreate) {
        if (autoCreate) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                this.dialectHelper = new SqlLockDialectHelper(conn);
                stmt.executeUpdate(dialectHelper.getCreateTableSqlString(lockRepositoryName));
            } catch (SQLException e) {
                // For Informix, ignore "Table or view already exists" error (SQLCODE -310)
                if (dialectHelper.getSqlDialect() == SqlDialect.INFORMIX &&
                        (e.getErrorCode() == -310 || e.getSQLState() != null && e.getSQLState().startsWith("42S01"))) {
                    return;
                }
                // Firebird throws an error when table already exists; ignore that specific case
                if (dialectHelper.getSqlDialect() == io.flamingock.internal.common.sql.SqlDialect.FIREBIRD) {
                    int errorCode = e.getErrorCode();
                    String sqlState = e.getSQLState();
                    String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

                    if (errorCode == 335544351 || "42000".equals(sqlState) || msg.contains("already exists")) {
                        return;
                    }
                }
                throw new RuntimeException("Failed to initialize lock table", e);
            }
        }
    }


    @Override
    public LockAcquisition upsert(LockKey key, RunnerId owner, long leaseMillis) {
        String keyStr = key.toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(leaseMillis * 1_000_000);

        Connection conn = null;
        try {
            conn = dataSource.getConnection();

            // For Informix, use shorter timeout and simpler transaction handling
            // For Sybase we MUST disable auto-commit so HOLDLOCK works as intended
            boolean isInformix = dialectHelper.getSqlDialect() == SqlDialect.INFORMIX;
            boolean isSybase = dialectHelper.getSqlDialect() == SqlDialect.SYBASE;
            conn.setAutoCommit(isInformix);  // Informix uses autocommit
            if (isSybase) {
                conn.setAutoCommit(false);   // IMPORTANT: disable autocommit for Sybase so HOLDLOCK works
            }

            try {

                if (isSybase) {
                    // For Sybase, use HOLDLOCK to prevent race conditions during lock check
                    String selectSql = "SELECT lock_key, status, owner, expires_at " +
                            "FROM " + lockRepositoryName + " HOLDLOCK " +
                            "WHERE lock_key = ?";

                    CommunityLockEntry existing = null;
                    try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                        ps.setString(1, keyStr);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                existing = new CommunityLockEntry(
                                        rs.getString("lock_key"),
                                        LockStatus.valueOf(rs.getString("status")),
                                        rs.getString("owner"),
                                        rs.getTimestamp("expires_at").toLocalDateTime()
                                );
                            }
                        }
                    }

                    if (existing == null ||
                            owner.toString().equals(existing.getOwner()) ||
                            LocalDateTime.now().isAfter(existing.getExpiresAt())) {
                        // Delete existing lock first, then insert new one
                        try (PreparedStatement delete = conn.prepareStatement(
                                "DELETE FROM " + lockRepositoryName + " WHERE lock_key = ?")) {
                            delete.setString(1, keyStr);
                            delete.executeUpdate();
                        }
                        upsertLockEntry(conn, keyStr, owner.toString(), expiresAt);
                        conn.commit();
                    } else {
                        conn.rollback();
                        throw new LockServiceException("upsert", keyStr,
                                "Still locked by " + existing.getOwner() + " until " + existing.getExpiresAt());
                    }

                    return new LockAcquisition(owner, leaseMillis);
                }

                CommunityLockEntry existing = getLockEntry(conn, keyStr);
                if (existing == null ||
                        owner.toString().equals(existing.getOwner()) ||
                        LocalDateTime.now().isAfter(existing.getExpiresAt())) {
                    upsertLockEntry(conn, keyStr, owner.toString(), expiresAt);
                    // Commit for all dialects except Informix (which uses auto-commit above)
                    if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                        conn.commit();
                    }
                } else {
                    if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                        conn.rollback();
                    }
                    throw new LockServiceException("upsert", keyStr,
                            "Still locked by " + existing.getOwner() + " until " + existing.getExpiresAt());
                }
            } catch (Exception e) {
                if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                    conn.rollback();
                }
                throw e;
            } finally {
                if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new LockServiceException("upsert", keyStr, e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Log but don't throw
                }
            }
        }
        return new LockAcquisition(owner, leaseMillis);
    }


    @Override
    public LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException {
        String keyStr = key.toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(leaseMillis * 1_000_000);

        Connection conn = null;
        try {
            conn = dataSource.getConnection();

            conn.setAutoCommit(dialectHelper.getSqlDialect() == SqlDialect.INFORMIX);

            try {
                CommunityLockEntry existing = getLockEntry(conn, keyStr);
                if (existing != null && owner.toString().equals(existing.getOwner())) {
                    upsertLockEntry(conn, keyStr, owner.toString(), expiresAt);
                    if (dialectHelper.getSqlDialect() != SqlDialect.SQLSERVER &&
                            dialectHelper.getSqlDialect() != SqlDialect.SYBASE &&
                            dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                        conn.commit();
                    }
                } else {
                    if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                        conn.rollback();
                    }
                    throw new LockServiceException("extendLock", keyStr,
                            "Lock belongs to " + (existing != null ? existing.getOwner() : "none"));
                }
            } catch (Exception e) {
                if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                    conn.rollback();
                }
                throw e;
            } finally {
                if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new LockServiceException("extendLock", keyStr, e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Log but don't throw
                }
            }
        }
        return new LockAcquisition(owner, leaseMillis);
    }

    @Override
    public LockAcquisition getLock(LockKey lockKey) {
        String keyStr = lockKey.toString();
        try (Connection conn = dataSource.getConnection()) {
            CommunityLockEntry entry = getLockEntry(conn, keyStr);
            if (entry != null) {
                return new LockAcquisition(RunnerId.fromString(entry.getOwner()),
                        java.sql.Timestamp.valueOf(entry.getExpiresAt()).getTime() - System.currentTimeMillis());
            }
        } catch (SQLException e) {
            // ignore
        }
        return null;
    }

    @Override
    public void releaseLock(LockKey lockKey, RunnerId owner) {
        String keyStr = lockKey.toString();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     dialectHelper.getSelectLockSqlString(lockRepositoryName))) {
            ps.setString(1, keyStr);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String existingOwner = rs.getString("owner");
                    if (existingOwner.equals(owner.toString())) {
                        try (PreparedStatement delete = conn.prepareStatement(
                                dialectHelper.getDeleteLockSqlString(lockRepositoryName))) {
                            delete.setString(1, keyStr);
                            delete.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            // ignore
        }
    }

    private CommunityLockEntry getLockEntry(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                dialectHelper.getSelectLockSqlString(lockRepositoryName))) {

            // Set query timeout for Informix to prevent long waits
            if (dialectHelper.getSqlDialect() == SqlDialect.INFORMIX) {
                ps.setQueryTimeout(5);
            }

            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CommunityLockEntry(
                            rs.getString(1),
                            LockStatus.valueOf(rs.getString("status")),
                            rs.getString("owner"),
                            rs.getTimestamp("expires_at").toLocalDateTime()
                    );
                }
            }
        }
        return null;
    }


    private void upsertLockEntry(Connection conn, String key, String owner, LocalDateTime expiresAt) throws SQLException {
        dialectHelper.upsertLockEntry(conn, lockRepositoryName, key, owner, expiresAt);
    }
}
