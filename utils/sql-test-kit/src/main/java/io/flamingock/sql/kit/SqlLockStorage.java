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

import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.common.sql.dialectHelpers.SqlLockDialectHelper;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.external.store.lock.LockServiceException;
import io.flamingock.internal.core.external.store.lock.LockStatus;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockEntry;
import io.flamingock.internal.util.id.RunnerId;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;

/**
 * SQL implementation of LockStorage for real database testing.
 * Only depends on SQL client/database and core Flamingock classes.
 * Does not depend on SQL-specific Flamingock components like SqlTargetSystem.
 */
public class SqlLockStorage implements LockStorage {

    private final String lockTableName;
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final SqlLockDialectHelper dialectHelper;

    public SqlLockStorage(DataSource dataSource) throws SQLException {
        this(dataSource, DEFAULT_LOCK_STORE_NAME);
    }

    public SqlLockStorage(DataSource dataSource, String lockTableName) throws SQLException {
        this.lockTableName = lockTableName;
        this.dataSource = dataSource;
        try (Connection conn = dataSource.getConnection()) {
            this.dialectHelper = new SqlLockDialectHelper(conn);
        }
    }

    @Override
    public void storeLock(LockKey key, LockAcquisition acquisition) {
        String keyStr = key.toString();
        RunnerId owner = acquisition.getOwner();
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(acquisition.getAcquiredForMillis() * 1_000_000);

        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            // For Informix, use shorter timeout and simpler transaction handling
            // For Sybase we MUST disable auto-commit so HOLDLOCK works as intended
            boolean isInformix = dialectHelper.getSqlDialect() == SqlDialect.INFORMIX;
            boolean isSybase = dialectHelper.getSqlDialect() == SqlDialect.SYBASE;
            connection.setAutoCommit(isInformix);  // Informix uses autocommit

            try {

                if (isSybase) {
                    // For Sybase, use HOLDLOCK to prevent race conditions during lock check
                    String selectSql = "SELECT lock_key, status, owner, expires_at " +
                        "FROM " + lockTableName + " HOLDLOCK " +
                        "WHERE lock_key = ?";

                    CommunityLockEntry existing = null;
                    try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
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
                        try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM " + lockTableName + " WHERE lock_key = ?")) {
                            delete.setString(1, keyStr);
                            delete.executeUpdate();
                        }
                        dialectHelper.upsertLockEntry(connection, lockTableName, keyStr, owner.toString(), expiresAt);
                        connection.commit();
                        return;
                    } else {
                        connection.rollback();
                        throw new LockServiceException("upsert", keyStr,
                            "Still locked by " + existing.getOwner() + " until " + existing.getExpiresAt());
                    }
                }

                CommunityLockEntry existing = getLockEntry(connection, keyStr);
                if (existing == null ||
                    owner.toString().equals(existing.getOwner()) ||
                    LocalDateTime.now().isAfter(existing.getExpiresAt())) {
                    dialectHelper.upsertLockEntry(connection, lockTableName, keyStr, owner.toString(), expiresAt);
                    // Commit for all dialects except Informix (which uses auto-commit above)
                    if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                        connection.commit();
                    }
                } else {
                    if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                        connection.rollback();
                    }
                    throw new LockServiceException("upsert", keyStr,
                        "Still locked by " + existing.getOwner() + " until " + existing.getExpiresAt());
                }
            } catch (Exception e) {
                if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                    connection.rollback();
                }
                throw e;
            } finally {
                if (dialectHelper.getSqlDialect() != SqlDialect.INFORMIX) {
                    connection.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new LockServiceException("upsert", keyStr, e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Log but don't throw
                }
            }
        }
    }

    @Override
    public LockAcquisition getLock(LockKey key) {
        try (Connection connection = dataSource.getConnection()) {
            CommunityLockEntry entry = getLockEntry(connection, key.toString());
            if (entry != null) {
                return new LockAcquisition(RunnerId.fromString(entry.getOwner()),
                    Timestamp.valueOf(entry.getExpiresAt()).getTime() - System.currentTimeMillis());
            }
        } catch (SQLException e) {
            // ignore
        }
        return null;
    }

    private CommunityLockEntry getLockEntry(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            dialectHelper.getSelectLockSqlString(lockTableName))) {

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

    @Override
    public Map<LockKey, LockAcquisition> getAllLocks() {
        Map<LockKey, LockAcquisition> locks = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(dialectHelper.getSelectAllLocksSqlString(lockTableName))) {
            while (rs.next()) {
                LockKey key = LockKey.fromString(rs.getString(1));
                LockAcquisition acquisition = new LockAcquisition(
                    RunnerId.fromString(rs.getString("owner")),
                    Timestamp.valueOf(rs.getTimestamp("expires_at").toLocalDateTime()).getTime() - System.currentTimeMillis()
                );
                locks.put(key, acquisition);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read locks from database", e);
        }

        return locks;
    }

    @Override
    public void removeLock(LockKey key) {
        try {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                dialectHelper.getDeleteLockSqlString(lockTableName))) {

                // Set query timeout for Informix to prevent long waits
                if (dialectHelper.getSqlDialect() == SqlDialect.INFORMIX) {
                    ps.setQueryTimeout(5);
                }
                ps.setString(1, key.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            // ignore
        }
    }

    @Override
    public boolean hasLocks() {
        return !this.getAllLocks().isEmpty();
    }

    @Override
    public void clear() {
        try(Connection connection = dataSource.getConnection();
            Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(String.format("DELETE FROM %s", lockTableName));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear lock entries", e);
        }
        metadata.clear();
    }

    @Override
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @Override
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

}
