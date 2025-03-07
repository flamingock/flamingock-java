/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.cloud.transaction.sql;

import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.cloud.transaction.TaskWithOngoingStatus;
import io.flamingock.core.engine.audit.domain.AuditItem;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.descriptor.LoadedTask;
import io.flamingock.core.task.navigation.step.FailedStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class SqlCloudTransactioner implements CloudTransactioner {
    private static final Logger logger = LoggerFactory.getLogger(SqlCloudTransactioner.class);

    private Connection connection;

    private String url;

    private String user;

    private String password;

    private SqlDialect dialect;

    public SqlCloudTransactioner setUrl(String url) {
        this.url = url;
        return this;
    }

    public SqlCloudTransactioner setUser(String user) {
        this.user = user;
        return this;
    }

    public SqlCloudTransactioner setPassword(String password) {
        this.password = password;
        return this;
    }

    public SqlCloudTransactioner setDialect(SqlDialect dialect) {
        this.dialect = dialect;
        return this;
    }

    @Override
    public void initialize() {

        if(dialect == null) {
            throw new FlamingockException("Sql dialect is mandatory. Please set the dialect with method `SqlCloudTransactioner.setDialect(...)`");
        }
        try {
            connection = DriverManager.getConnection(url, user, password);
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try (Statement statement = connection.createStatement()) {
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet resultSet = meta.getTables(null, null, dialect.getOngoingTasksTableName(), new String[]{"TABLE"});
            if (!resultSet.next()) {
                statement.executeUpdate(dialect.getCreateOngoingTasksTable());
                connection.commit();
                logger.info("table {} created successfully", dialect.getOngoingTasksTableName());
            } else {
                logger.debug("Table {} already created", dialect.getOngoingTasksTableName());
            }

        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Set<TaskWithOngoingStatus> getOngoingStatuses() {
        try (PreparedStatement preparedStatement = connection.prepareStatement(dialect.getSelectIdOngoingTask())) {
            ResultSet resultSet = preparedStatement.executeQuery();

            Set<TaskWithOngoingStatus> ongoingStatuses = new HashSet<>();
            while (resultSet.next()) {
                String taskId = resultSet.getString("task_id");
                AuditItem.Operation operation = AuditItem.Operation.valueOf(resultSet.getString("operation"));
                ongoingStatuses.add(new TaskWithOngoingStatus(taskId, operation.toOngoingStatusOperation()));
            }
            return ongoingStatuses;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cleanOngoingStatus(String taskId) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(dialect.getDeleteOngoingTask())) {
            preparedStatement.setString(1, taskId);
            int rows = preparedStatement.executeUpdate();
            logger.trace("removed ongoing task[{}]: [{}] rows affected", taskId, rows);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveOngoingStatus(TaskWithOngoingStatus status) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(dialect.getUpsertOngoingTask())) {
            preparedStatement.setString(1, status.getTaskId());
            preparedStatement.setString(2, status.getOperation().toString());
            int rows = preparedStatement.executeUpdate();
            connection.commit();
            logger.debug("saved ongoing task[{}]: [{}] rows affected", status.getTaskId(), rows);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T wrapInTransaction(LoadedTask loadedTask,
                                   DependencyInjectable dependencyInjectable,
                                   Supplier<T> operation) {
        Dependency dependency = new Dependency(Connection.class, connection);
        try {
            dependencyInjectable.addDependency(dependency);
            T result = operation.get();
            if (result instanceof FailedStep) {
                connection.rollback();
            } else {
                connection.commit();
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dependencyInjectable.removeDependencyByRef(dependency);
        }
    }

    @Override
    public void close() {
        try {
            if(connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warn("Flamingock SQL Cloud trasactioner connection cannot be closed", e);
        }
    }

}
