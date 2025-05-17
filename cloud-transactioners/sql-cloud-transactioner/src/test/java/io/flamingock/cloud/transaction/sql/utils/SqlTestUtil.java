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

package io.flamingock.cloud.transaction.sql.utils;

import io.flamingock.internal.core.engine.audit.domain.AuditItem;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class SqlTestUtil {


    private final static String SQL_INSERT = "INSERT INTO ONGOING_TASKS(task_id, operation) values(?, ?)";


    private SqlTestUtil() {
    }


    public static MySQLContainer<?> getMysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:5.7.34"));
    }

    public static Connection getConnection(MySQLContainer<?> mysql) throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    public static void cleanTable(MySQLContainer<?> mysql, String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM " + table)) {
            connection.setAutoCommit(false);
            preparedStatement.executeUpdate();
            connection.commit();
        }
    }

    public static void insertOngoingExecution(Connection connection, String taskId) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT)) {
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet resultSet = meta.getTables(null, null, "ONGOING_TASKS", new String[]{"TABLE"});
            if (!resultSet.next()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE ONGOING_TASKS (" +
                            "task_id VARCHAR(255) not NULL, " +
                            " operation VARCHAR(10), " +
                            " PRIMARY KEY ( task_id )" +
                            ")");
                }
            }

            connection.setAutoCommit(false);
            preparedStatement.setString(1, taskId);
            preparedStatement.setString(2, AuditItem.Operation.EXECUTION.toString());
            preparedStatement.executeUpdate();
            connection.commit();
            checkAtLeastOneOngoingTask(connection);
        }
    }

    public static void checkCount(Connection connection, String tableName, int count) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT count(id) FROM "  + tableName)) {
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            assertEquals(count, resultSet.getInt(1));
        }
    }
    public static void checkAtLeastOneOngoingTask(Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT count(task_id) FROM ONGOING_TASKS")) {
            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            Assertions.assertEquals(1, resultSet.getInt(1));
        }
    }

    public static void dropTableSafe(MySQLContainer<?> mysql, String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             PreparedStatement preparedStatement = connection.prepareStatement("DROP TABLE " + tableName)) {
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet resultSet = meta.getTables(null, null, tableName, new String[]{"TABLE"});
            if (resultSet.next()) {
                connection.setAutoCommit(false);
                preparedStatement.executeUpdate();
                connection.commit();
            }


        }
    }
}
