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

import org.junit.jupiter.api.Assertions;

import javax.sql.DataSource;
import java.sql.*;
import java.util.function.Function;

public class MySQLTestHelper {

    private final DataSource dataSource;
    private static final String ONGOING_TASKS_TABLE = "FLAMINGOCK_ONGOING_TASKS";

    public MySQLTestHelper(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void checkTableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet resultSet = meta.getTables(null, null, tableName, new String[]{"TABLE"});
            Assertions.assertTrue(resultSet.next(), "Table " + tableName + " should exist");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void checkCount(String tableName, int expectedCount) {
        String sql = String.format("SELECT COUNT(*) FROM %s", tableName);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int actualCount = rs.getInt(1);
                Assertions.assertEquals(expectedCount, actualCount,
                        String.format("Expected %d rows in table %s, but found %d", expectedCount, tableName, actualCount));
            }
        } catch (SQLException e) {
            if (expectedCount == 0) {
                return;
            }
            throw new RuntimeException(e);
        }
    }

    public void checkOngoingTask(Function<Integer, Boolean> predicate) {
        try (Connection connection = dataSource.getConnection()) {
            String sql = String.format("SELECT COUNT(*) FROM %s", ONGOING_TASKS_TABLE);
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int count = rs.getInt(1);
                    Assertions.assertTrue(predicate.apply(count),
                            String.format("Ongoing task count predicate failed for count: %d", count));
                }
            }
        } catch (SQLException e) {
            Assertions.assertTrue(predicate.apply(0),
                    "Ongoing task count predicate failed for count: 0 (table doesn't exist)");
        }
    }

    public void insertOngoingExecution(String taskId) {
        try (Connection connection = dataSource.getConnection()) {
            // Create table if it doesn't exist
            createOngoingTasksTableIfNotExists(connection);

            String sql = String.format("INSERT INTO %s (task_id, operation) VALUES (?, ?)", ONGOING_TASKS_TABLE);
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, taskId);
                stmt.setString(2, "EXECUTION");
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void dropTable(String tableName) {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName);
        } catch (SQLException e) {
        }
    }

    public void checkEmptyTargetSystemAudiMarker() {
        checkOngoingTask(ongoingCount -> ongoingCount == 0);
    }

    private void createOngoingTasksTableIfNotExists(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet resultSet = meta.getTables(null, null, ONGOING_TASKS_TABLE, new String[]{"TABLE"});
        if (!resultSet.next()) {
            String createTableSql = String.format(
                    "CREATE TABLE %s (" +
                            "task_id VARCHAR(255) PRIMARY KEY, " +
                            "operation VARCHAR(50) NOT NULL" +
                            ")", ONGOING_TASKS_TABLE);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(createTableSql);
            }
        }
    }
}