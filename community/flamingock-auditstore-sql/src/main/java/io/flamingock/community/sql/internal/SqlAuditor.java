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

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.util.Result;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SqlAuditor implements LifecycleAuditWriter, AuditReader {

    private final DataSource dataSource;
    private final String auditTableName;
    private final boolean autoCreate;

    public SqlAuditor(DataSource dataSource, String auditTableName, boolean autoCreate) {
        this.dataSource = dataSource;
        this.auditTableName = auditTableName;
        this.autoCreate = autoCreate;
    }

    public void initialize() {
        if (autoCreate) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS " + auditTableName + " (" +
                                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                                "execution_id VARCHAR(255)," +
                                "author VARCHAR(255)," +
                                "task_id VARCHAR(255)," +
                                "state VARCHAR(255)," +
                                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                                ")"
                );
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize audit table", e);
            }
        }
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + auditTableName + " (execution_id, author, task_id, state, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, auditEntry.getExecutionId());
            ps.setString(2, auditEntry.getAuthor());
            ps.setString(3, auditEntry.getTaskId());
            ps.setString(4, auditEntry.getState().name());
            ps.setTimestamp(5, Timestamp.valueOf(auditEntry.getCreatedAt()));
            ps.executeUpdate();
            return Result.OK();
        } catch (SQLException e) {
            return new Result.Error(e);
        }
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT execution_id, author, task_id, state, created_at FROM " + auditTableName + " ORDER BY created_at DESC")) {
            while (rs.next()) {
                AuditEntry entry = new AuditEntry(
                        rs.getString("execution_id"),
                        null,
                        rs.getString("task_id"),
                        rs.getString("author"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        AuditEntry.Status.valueOf(rs.getString("state")),
                        null, null, null, 0L, null, null, false, null, null
                );
                entries.add(entry);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read audit history", e);
        }
        return entries;
    }
}
