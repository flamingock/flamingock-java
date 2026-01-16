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

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.external.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.util.Result;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SqlAuditor implements LifecycleAuditWriter, AuditReader {

    private final DataSource dataSource;
    private final String auditTableName;
    private final boolean autoCreate;
    private final SqlAuditorDialectHelper dialectHelper;

    public SqlAuditor(DataSource dataSource, String auditTableName, boolean autoCreate) {
        this.dataSource = dataSource;
        this.auditTableName = auditTableName;
        this.autoCreate = autoCreate;
        this.dialectHelper = new SqlAuditorDialectHelper(dataSource);
    }

    public void initialize() {
        if (autoCreate) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(dialectHelper.getCreateTableSqlString(auditTableName));
            } catch (SQLException e) {
                // Firebird throws an error when table already exists; ignore that specific case
                if (dialectHelper.getSqlDialect() == io.flamingock.internal.common.sql.SqlDialect.FIREBIRD) {
                    int errorCode = e.getErrorCode();
                    String sqlState = e.getSQLState();
                    String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

                    if (errorCode == 335544351 || "42000".equals(sqlState) || msg.contains("already exists")) {
                        return;
                    }
                }
                throw new RuntimeException("Failed to initialize audit table", e);
            }
        }
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();

            // For Informix, ensure autoCommit is enabled for audit writes
            if (dialectHelper.getSqlDialect() == SqlDialect.INFORMIX) {
                conn.setAutoCommit(true);
            }

            try (PreparedStatement ps = conn.prepareStatement(
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
                ps.setBoolean(21, auditEntry.isLegacy());
                ps.executeUpdate();
            }
            return Result.OK();
        } catch (SQLException e) {
            return new Result.Error(e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Log but don't throw
                }
            }
        }
    }


    @Override
    public List<AuditEntry> getAuditHistory() {
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(dialectHelper.getSelectHistorySqlString(auditTableName))) {
            while (rs.next()) {
                AuditEntry entry = new AuditEntry(
                        rs.getString("execution_id"),
                        rs.getString("stage_id"),
                        rs.getString("change_id"),
                        rs.getString("author"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("state") != null ? AuditEntry.Status.valueOf(rs.getString("state")) : null,
                        rs.getString("type") != null ? AuditEntry.ExecutionType.valueOf(rs.getString("type")) : null,
                        rs.getString("invoked_class"),
                        rs.getString("invoked_method"),
                        rs.getString("source_file"),
                        rs.getLong("execution_millis"),
                        rs.getString("execution_hostname"),
                        rs.getString("metadata"),
                        rs.getBoolean("system_change"),
                        rs.getBoolean("legacy"),
                        rs.getString("error_trace"),
                        AuditTxType.fromString(rs.getString("tx_strategy")),
                        rs.getString("target_system_id"),
                        rs.getString("change_order"),
                        rs.getString("recovery_strategy") != null ? io.flamingock.api.RecoveryStrategy.valueOf(rs.getString("recovery_strategy")) : null,
                        rs.getObject("transaction_flag") != null ? rs.getBoolean("transaction_flag") : null
                );
                entries.add(entry);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read audit history", e);
        }
        return entries;
    }
}
