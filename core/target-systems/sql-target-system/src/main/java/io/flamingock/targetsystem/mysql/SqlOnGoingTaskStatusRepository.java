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

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.engine.audit.domain.AuditContextBundle;
import io.flamingock.internal.core.targets.OngoingTaskStatus;
import io.flamingock.internal.core.targets.OngoingTaskStatusRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;


public class SqlOnGoingTaskStatusRepository implements OngoingTaskStatusRepository {

    private final DataSource dataSource;
    private final String tableName;

    public static Builder builder(DataSource dataSource) {
        return new Builder(dataSource);
    }

    public SqlOnGoingTaskStatusRepository(DataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = tableName;
    }

    @Override
    public Set<OngoingTaskStatus> getAll() {
        String sql = String.format("SELECT task_id, operation FROM %s", tableName);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            ResultSet resultSet = preparedStatement.executeQuery();

            Set<OngoingTaskStatus> ongoingStatuses = new HashSet<>();
            while (resultSet.next()) {
                String taskId = resultSet.getString("task_id");
                AuditContextBundle.Operation operation = AuditContextBundle.Operation.valueOf(resultSet.getString("operation"));
                ongoingStatuses.add(new OngoingTaskStatus(taskId, operation.toOngoingStatusOperation()));
            }
            return ongoingStatuses;
        } catch (SQLException ex) {
            throw new FlamingockException(ex);
        }
    }

    @Override
    public void clean(String taskId, ContextResolver contextResolver) {
        Connection connection = contextResolver.getRequiredDependencyValue(Connection.class);
        String sql = String.format("DELETE FROM %s WHERE task_id = ?", tableName);
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, taskId);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new FlamingockException(ex);
        }
    }

    @Override
    public void register(OngoingTaskStatus status) {
        String sql = String.format(
                "INSERT INTO %s (task_id, operation) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE operation = VALUES(operation)", tableName);

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, status.getTaskId());
            preparedStatement.setString(2, status.getOperation().toString());
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
        private String tableName = "FLAMINGOCK_ONGOING_TASKS";
        private boolean autoCreate = true;

        public Builder(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public Builder withTableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder withAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public SqlOnGoingTaskStatusRepository build() {
            if (autoCreate) {
                createTableIfNotExists();
            }
            return new SqlOnGoingTaskStatusRepository(dataSource, tableName);
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