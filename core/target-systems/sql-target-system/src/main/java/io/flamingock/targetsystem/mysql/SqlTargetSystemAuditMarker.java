/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.targetsystem.mysql;

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.store.audit.domain.AuditContextBundle;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;


public class SqlTargetSystemAuditMarker implements TargetSystemAuditMarker {

    private final String tableName;
    private final DataSource dataSource;
    private final TransactionManager<Connection> txManager;

    public static Builder builder(DataSource dataSource, TransactionManager<Connection> txManager) {
        return new Builder(dataSource, txManager);
    }

    public SqlTargetSystemAuditMarker(DataSource dataSource,
                                      String tableName,
                                      TransactionManager<Connection> txManager) {
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.txManager = txManager;
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {
        String sql = String.format("SELECT task_id, operation FROM %s", tableName);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            ResultSet resultSet = preparedStatement.executeQuery();

            Set<TargetSystemAuditMark> ongoingStatuses = new HashSet<>();
            while (resultSet.next()) {
                String taskId = resultSet.getString("task_id");
                AuditContextBundle.Operation operation = AuditContextBundle.Operation.valueOf(resultSet.getString("operation"));
                ongoingStatuses.add(new TargetSystemAuditMark(taskId, operation.toOngoingStatusOperation()));
            }
            return ongoingStatuses;
        } catch (SQLException ex) {
            throw new FlamingockException(ex);
        }
    }

    @Override
    public void clearMark(String changeId) {
        String sql = String.format("DELETE FROM %s WHERE task_id = ?", tableName);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, changeId);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new FlamingockException(ex);
        }
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {
        String sql = String.format(
                "INSERT INTO %s (task_id, operation) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE operation = VALUES(operation)", tableName);
        Connection connection = txManager.getSessionOrThrow(auditMark.getTaskId());
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, auditMark.getTaskId());
            preparedStatement.setString(2, auditMark.getOperation().toString());
            preparedStatement.executeUpdate();
            if(!connection.getAutoCommit())  {
                connection.commit();
            }
        } catch (SQLException ex) {
            throw new FlamingockException(ex);
        }
    }

    public static class Builder {
        private final DataSource dataSource;
        private final TransactionManager<Connection> txManager;
        private String tableName = "FLAMINGOCK_ONGOING_TASKS";
        private boolean autoCreate = true;

        public Builder(DataSource dataSource, TransactionManager<Connection> txManager) {
            this.dataSource = dataSource;
            this.txManager = txManager;
        }

        public Builder withTableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder withAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public SqlTargetSystemAuditMarker build() {
            if (autoCreate) {
                createTableIfNotExists();
            }
            return new SqlTargetSystemAuditMarker(dataSource, tableName, txManager);
        }

        private void createTableIfNotExists() {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {

                DatabaseMetaData meta = connection.getMetaData();
                ResultSet resultSet = meta.getTables(null, null, tableName, new String[]{"TABLE"});
                if (!resultSet.next()) {
                    String createTableSql = String.format(
                            "CREATE TABLE %s (" +
                                    "task_id VARCHAR(255) PRIMARY KEY, " +
                                    "operation VARCHAR(50) NOT NULL" +
                                    ")", tableName);
                    statement.executeUpdate(createTableSql);
                }
            } catch (SQLException ex) {
                throw new FlamingockException(ex);
            }
        }
    }
}