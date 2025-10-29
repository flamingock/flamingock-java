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

import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.store.lock.community.CommunityLockEntry;
import io.flamingock.internal.core.store.lock.LockAcquisition;
import io.flamingock.internal.core.store.lock.LockKey;
import io.flamingock.internal.core.store.lock.LockServiceException;
import io.flamingock.internal.core.store.lock.LockStatus;
import io.flamingock.internal.util.id.RunnerId;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;

public class SqlLockService implements CommunityLockService {

    private final DataSource dataSource;
    private final String lockRepositoryName;
    private final SqlLockDialectHelper dialectHelper;

    public SqlLockService(DataSource dataSource, String lockRepositoryName) {
        this.dataSource = dataSource;
        this.lockRepositoryName = lockRepositoryName;
        this.dialectHelper = new SqlLockDialectHelper(dataSource);
    }

    public void initialize(boolean autoCreate) {
        if (autoCreate) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                // Enable pessimistic locking for HSQLDB
                if (dialectHelper.getSqlDialect() == SqlDialect.HSQLDB) {
                    stmt.execute("SET DATABASE TRANSACTION CONTROL LOCKS");
                }
                stmt.executeUpdate(dialectHelper.getCreateTableSqlString(lockRepositoryName));
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize lock table", e);
            }
        }
    }

    @Override
    public LockAcquisition upsert(LockKey key, RunnerId owner, long leaseMillis) {
        String keyStr = key.toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(leaseMillis * 1_000_000);

        // For HSQLDB, use a retry mechanism with shorter intervals
        if (dialectHelper.getSqlDialect() == SqlDialect.HSQLDB) {
            return upsertWithRetry(keyStr, owner, expiresAt, leaseMillis);
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                CommunityLockEntry existing = getLockEntry(conn, keyStr);
                if (existing == null ||
                        owner.toString().equals(existing.getOwner()) ||
                        LocalDateTime.now().isAfter(existing.getExpiresAt())) {
                    upsertLockEntry(conn, keyStr, owner.toString(), expiresAt);
                    if (dialectHelper.getSqlDialect() != SqlDialect.SQLSERVER &&
                            dialectHelper.getSqlDialect() != SqlDialect.SYBASE) {
                        conn.commit();
                    }
                } else {
                    conn.rollback();
                    throw new LockServiceException("upsert", keyStr,
                            "Still locked by " + existing.getOwner() + " until " + existing.getExpiresAt());
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new LockServiceException("upsert", keyStr, e.getMessage());
        }
        return new LockAcquisition(owner, leaseMillis);
    }

    private LockAcquisition upsertWithRetry(String keyStr, RunnerId owner, LocalDateTime expiresAt, long leaseMillis) {
        int maxRetries = 50; // 5 seconds total (50 * 100ms)
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                conn.setAutoCommit(false);
                try {
                    CommunityLockEntry existing = getLockEntry(conn, keyStr);
                    if (existing == null ||
                            owner.toString().equals(existing.getOwner()) ||
                            LocalDateTime.now().isAfter(existing.getExpiresAt())) {
                        upsertLockEntry(conn, keyStr, owner.toString(), expiresAt);
                        conn.commit();
                        return new LockAcquisition(owner, leaseMillis);
                    } else {
                        conn.rollback();
                        // Lock is held by someone else, wait and retry
                        retryCount++;
                        if (retryCount < maxRetries) {
                            try {
                                Thread.sleep(100); // 100ms between retries
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new LockServiceException("upsert", keyStr, "Interrupted while waiting for lock");
                            }
                        }
                    }
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new LockServiceException("upsert", keyStr, e.getMessage());
            }
        }

        throw new LockServiceException("upsert", keyStr, "Lock acquisition timeout after " + maxRetries + " retries");
    }


    @Override
    public LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException {
        String keyStr = key.toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(leaseMillis * 1_000_000);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                CommunityLockEntry existing = getLockEntry(conn, keyStr);
                if (existing != null && owner.toString().equals(existing.getOwner())) {
                    upsertLockEntry(conn, keyStr, owner.toString(), expiresAt);
                    if (dialectHelper.getSqlDialect() != SqlDialect.SQLSERVER &&
                            dialectHelper.getSqlDialect() != SqlDialect.SYBASE) {
                        conn.commit();
                    }
                } else {
                    conn.rollback();
                    throw new LockServiceException("extendLock", keyStr,
                            "Lock belongs to " + (existing != null ? existing.getOwner() : "none"));
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new LockServiceException("extendLock", keyStr, e.getMessage());
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
        String selectSql = dialectHelper.getSelectForReleaseLockSqlString(lockRepositoryName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
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
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CommunityLockEntry(
                            rs.getString(1), // key column
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
