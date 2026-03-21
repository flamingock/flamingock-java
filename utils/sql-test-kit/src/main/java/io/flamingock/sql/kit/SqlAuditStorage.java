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

import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.common.sql.dialectHelpers.SqlAuditorDialectHelper;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;

/**
 * SQL implementation of AuditStorage for real database testing.
 * Only depends on SQL client/database and core Flamingock classes.
 * Does not depend on SQL-specific Flamingock components like SqlTargetSystem.
 */
public class SqlAuditStorage implements AuditStorage {

    private final DataSource dataSource;
    private final SqlAuditorDialectHelper dialectHelper;
    private final String auditTableName;

    public SqlAuditStorage(DataSource dataSource) throws SQLException {
        this(dataSource, DEFAULT_AUDIT_STORE_NAME);
    }

    public SqlAuditStorage(DataSource dataSource, String tableName) throws SQLException {
        this.auditTableName = tableName;
        this.dataSource = dataSource;
        try (Connection conn = dataSource.getConnection()) {
            this.dialectHelper = new SqlAuditorDialectHelper(conn);
        }
    }

    @Override
    public void addAuditEntry(AuditEntry auditEntry) {
        try (Connection connection = dataSource.getConnection()) {
            // For Informix, ensure autoCommit is enabled for audit writes
            if (dialectHelper.getSqlDialect() == SqlDialect.INFORMIX) {
                connection.setAutoCommit(true);
            }

            try (PreparedStatement ps = connection.prepareStatement(
                dialectHelper.getInsertSqlString(auditTableName))) {
                ps.setString(1, auditEntry.getExecutionId());
                ps.setString(2, auditEntry.getStageId());
                ps.setString(3, auditEntry.getTaskId());
                ps.setString(4, auditEntry.getAuthor());
                ps.setTimestamp(5, Timestamp.valueOf(auditEntry.getCreatedAt()));
                ps.setString(6, auditEntry.getState() != null ? auditEntry.getState().name() : null);
                ps.setString(7, auditEntry.getClassName());
                ps.setString(8, auditEntry.getMethodName());
                ps.setString(9, auditEntry.getSourceFile());
                ps.setString(10, auditEntry.getMetadata() != null ? auditEntry.getMetadata().toString() : null);
                ps.setLong(11, auditEntry.getExecutionMillis());
                ps.setString(12, auditEntry.getExecutionHostname());
                ps.setString(13, auditEntry.getErrorTrace());
                ps.setString(14, auditEntry.getType() != null ? auditEntry.getType().name() : null);
                ps.setString(15, auditEntry.getTxType() != null ? auditEntry.getTxType().name() : null);
                ps.setString(16, auditEntry.getTargetSystemId());
                ps.setString(17, auditEntry.getOrder());
                ps.setString(18, auditEntry.getRecoveryStrategy() != null ? auditEntry.getRecoveryStrategy().name() : null);
                ps.setObject(19, auditEntry.getTransactionFlag());
                ps.setObject(20, auditEntry.getSystemChange());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add audit entry", e);
        }
        // Log but don't throw
    }

    @Override
    public List<AuditEntry> getAuditEntries() {
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(dialectHelper.getSelectHistorySqlString(auditTableName))) {
            while (rs.next()) {
                AuditEntry entry = toAuditEntry(rs);
                entries.add(entry);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read audit history", e);
        }
        return entries;
    }

    @Override
    public List<AuditEntry> getAuditEntriesForChange(String changeId) {
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(dialectHelper.getSelectHistoryByChangeIdSqlString(auditTableName))) {
            ps.setString(1, changeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditEntry entry = toAuditEntry(rs);
                    entries.add(entry);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read audit history", e);
        }
        return entries;
    }

    @Override
    public long countAuditEntriesWithStatus(AuditEntry.Status status) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(dialectHelper.getCountByStatusSqlString(auditTableName))) {
            ps.setString(1, status.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count audit entries with status: " + status, e);
        }
        return 0;
    }

    @Override
    public boolean hasAuditEntries() {
        return !this.getAuditEntries().isEmpty();
    }

    @Override
    public void clear() {
        try(Connection connection = dataSource.getConnection();
            Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(String.format("DELETE FROM %s", auditTableName));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear audit entries", e);
        }
    }

    private AuditEntry toAuditEntry(ResultSet rs) throws SQLException {
        return new AuditEntry(
            rs.getString("execution_id"),
            rs.getString("stage_id"),
            rs.getString("change_id"),
            rs.getString("author"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getString("state") != null ? AuditEntry.Status.valueOf(rs.getString("state")) : null,
            rs.getString("type") != null ? AuditEntry.ChangeType.valueOf(rs.getString("type")) : null,
            rs.getString("invoked_class"),
            rs.getString("invoked_method"),
            rs.getString("source_file"),
            rs.getLong("execution_millis"),
            rs.getString("execution_hostname"),
            rs.getString("metadata"),
            rs.getBoolean("system_change"),
            rs.getString("error_trace"),
            AuditTxType.fromString(rs.getString("tx_strategy")),
            rs.getString("target_system_id"),
            rs.getString("change_order"),
            rs.getString("recovery_strategy") != null ? io.flamingock.api.RecoveryStrategy.valueOf(rs.getString("recovery_strategy")) : null,
            rs.getObject("transaction_flag") != null ? rs.getBoolean("transaction_flag") : null
        );
    }
}
