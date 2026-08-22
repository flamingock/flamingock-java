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
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.common.sql.dialectHelpers.SqlAuditorDialectHelper;
import io.flamingock.internal.util.Result;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SqlAuditRepository {

    private final DataSource dataSource;
    private final String auditTableName;
    private SqlAuditorDialectHelper dialectHelper = null;

    public SqlAuditRepository(DataSource dataSource, String auditTableName) {
        JournalEventConstants.validateIdentifier(auditTableName, "auditTableName");
        this.dataSource = dataSource;
        this.auditTableName = auditTableName;
    }

    public synchronized void initialize(boolean autoCreate) {
        try (Connection conn = dataSource.getConnection()) {
            this.dialectHelper = new SqlAuditorDialectHelper(conn);
            if (!tableExists(conn.getMetaData())) {
                if (!autoCreate) {
                    throw new IllegalStateException("SQL audit table '" + auditTableName + "' does not exist");
                }
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(dialectHelper.getCreateTableSqlString(auditTableName));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize audit table", e);
        }
    }

    public Result writeEntry(AuditEntry auditEntry) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();

            // For Informix, ensure autoCommit is enabled for audit writes
            if (dialectHelper != null && dialectHelper.getSqlDialect() == SqlDialect.INFORMIX) {
                conn.setAutoCommit(true);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    dialectHelper.getInsertSqlString(auditTableName))) {
                AuditEntryMapper.bind(ps, auditEntry, 1, getNullableBooleanJdbcType());
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

    /**
     * Replaces the local current state for a change on a caller-owned transaction connection.
     *
     * <p>This operation is used only when Journal Events are enabled. The journal retains every transition,
     * while the audit table keeps one current row per change. No commit is performed here; the caller owns the
     * transaction that also appends the corresponding event.</p>
     *
     * @param connection transaction-scoped connection
     * @param auditEntry new current state
     * @return successful result after the update or zero-row insert completes
     */
    Result replaceCurrentState(Connection connection, AuditEntry auditEntry) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        if (auditEntry == null) {
            throw new IllegalArgumentException("auditEntry must not be null");
        }
        JournalEventConstants.validateIdentifier(auditTableName, "auditTableName");
        if (auditEntry.getChangeId() == null || auditEntry.getChangeId().trim().isEmpty()) {
            throw new IllegalArgumentException("changeId must not be blank");
        }
        if (dialectHelper == null) {
            throw new IllegalStateException("SQL auditor is not initialized");
        }

        StringBuilder updateSql = new StringBuilder("UPDATE ")
                .append(auditTableName)
                .append(" SET ");
        for (String columnName : AuditEntryMapper.columnNames()) {
            if (updateSql.charAt(updateSql.length() - 1) != ' ') {
                updateSql.append(", ");
            }
            updateSql.append(columnName).append(" = ?");
        }
        updateSql.append(" WHERE change_id = ?");

        try (PreparedStatement update = connection.prepareStatement(updateSql.toString())) {
            int nullableBooleanJdbcType = getNullableBooleanJdbcType();
            AuditEntryMapper.bind(update, auditEntry, 1, nullableBooleanJdbcType);
            update.setString(AuditEntryMapper.columnNames().size() + 1, auditEntry.getChangeId());
            int updatedRows = update.executeUpdate();

            if (updatedRows > 1) {
                throw new IllegalStateException("Current audit state update matched " + updatedRows
                        + " rows for changeId '" + auditEntry.getChangeId() + "'");
            }
            if (updatedRows == 0) {
                try (PreparedStatement insert = connection.prepareStatement(
                        dialectHelper.getInsertSqlString(auditTableName))) {
                    AuditEntryMapper.bind(insert, auditEntry, 1, nullableBooleanJdbcType);
                    insert.executeUpdate();
                }
            }
            return Result.OK();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to replace local current audit state", exception);
        }
    }

    public List<AuditEntry> getAuditHistory() {
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(dialectHelper.getSelectHistorySqlString(auditTableName))) {
            while (rs.next()) {
                entries.add(AuditEntryMapper.fromResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read audit history", e);
        }
        return entries;
    }

    private boolean tableExists(DatabaseMetaData metadata) throws SQLException {
        try (ResultSet resultSet = metadata.getTables(null, null, null, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                if (auditTableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getNullableBooleanJdbcType() {
        switch (dialectHelper.getSqlDialect()) {
            case MYSQL:
            case MARIADB:
                return Types.TINYINT;
            case POSTGRESQL:
            case H2:
            case FIREBIRD:
            case INFORMIX:
                return Types.BOOLEAN;
            case SQLITE:
                return Types.INTEGER;
            case SQLSERVER:
            case SYBASE:
                return Types.BIT;
            case ORACLE:
                return Types.NUMERIC;
            case DB2:
            default:
                return Types.SMALLINT;
        }
    }
}
