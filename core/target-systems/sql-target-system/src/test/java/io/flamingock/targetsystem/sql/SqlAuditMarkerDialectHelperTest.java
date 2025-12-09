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
package io.flamingock.targetsystem.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.internal.common.sql.SqlDialect;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.transaction.TransactionManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class SqlAuditMarkerDialectHelperTest {

    private static final String ONGOING_TASKS_TABLE = "FLAMINGOCK_ONGOING_TASKS";

    private DataSource dataSource;
    private SqlTargetSystemAuditMarker sqlTargetSystemAuditMarker;
    private TransactionManager<Connection> txManager;

    private JdbcDatabaseContainer<?> createContainerForDialect(SqlDialect dialect) {
        switch (dialect) {
            case MYSQL:
            case MARIADB:
                return new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpass");
            case POSTGRESQL:
                return new PostgreSQLContainer<>(DockerImageName.parse("postgres:14"))
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpass");
            case SQLSERVER:
            case SYBASE:
                return new MSSQLServerContainer<>(DockerImageName.parse("mcr.microsoft.com/mssql/server:2019-latest"))
                    .withPassword("YourStrong!Passw0rd")
                    .acceptLicense();
            case ORACLE:
                return new OracleContainer("gvenzl/oracle-free:slim-faststart")
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpass");
            default:
                return null;
        }
    }

    private void initForDialect(SqlDialect dialect, JdbcDatabaseContainer<?> container) {
        if (container != null) {
            container.start();
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(container.getJdbcUrl());
            config.setUsername(container.getUsername());
            config.setPassword(container.getPassword());
            try {
                config.setDriverClassName(container.getDriverClassName());
            } catch (Exception ignored) {
            }
            dataSource = new HikariDataSource(config);
        } else {
            HikariConfig config = new HikariConfig();
            switch (dialect) {
                case H2:
                    config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
                    config.setUsername("testuser");
                    config.setPassword("");
                    config.setDriverClassName("org.h2.Driver");
                    break;
                case SQLITE:
                    config.setJdbcUrl("jdbc:sqlite:file:memdb?mode=memory&cache=shared");
                    config.setUsername("");
                    config.setPassword("");
                    config.setDriverClassName("org.sqlite.JDBC");
                    break;
                case DB2:
                    config.setJdbcUrl("jdbc:h2:mem:testdb;MODE=DB2;DB_CLOSE_DELAY=-1");
                    config.setUsername("testuser");
                    config.setPassword("");
                    config.setDriverClassName("org.h2.Driver");
                    break;
                default:
                    dataSource = null;
                    break;
            }
            if (dataSource == null && config.getJdbcUrl() != null) {
                dataSource = new HikariDataSource(config);
            }
        }

        txManager = new TransactionManager<>(() -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        sqlTargetSystemAuditMarker = SqlTargetSystemAuditMarker.builder(dataSource, txManager)
            .withTableName(ONGOING_TASKS_TABLE)
            .build();
    }

    private void dropTable() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS " + ONGOING_TASKS_TABLE);
        }
    }

    static Stream<Arguments> dialectProvider() {
        String enabledDialects = System.getProperty("sql.test.dialects", "mysql");
        Set<String> enabled = Arrays.stream(enabledDialects.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        Stream<Arguments> allDialects = Stream.of(
                Arguments.of(SqlDialect.MYSQL, "mysql"),
                Arguments.of(SqlDialect.SQLSERVER, "sqlserver"),
                Arguments.of(SqlDialect.ORACLE, "oracle"),
                Arguments.of(SqlDialect.POSTGRESQL, "postgresql"),
                Arguments.of(SqlDialect.MARIADB, "mariadb"),
                Arguments.of(SqlDialect.H2, "h2"),
                Arguments.of(SqlDialect.SQLITE, "sqlite"),
                Arguments.of(SqlDialect.INFORMIX, "informix"),
                Arguments.of(SqlDialect.FIREBIRD, "firebird")
        );

        return allDialects.filter(args -> {
            String dialectName = (String) args.get()[1];
            return enabled.contains(dialectName);
        });
    }

    @ParameterizedTest(name = "[{index}] dialect={0} - Should add and list two marks successfully")
    @MethodSource("dialectProvider")
    void addOngoingTaskMark(SqlDialect dialect) {
        JdbcDatabaseContainer<?> container = createContainerForDialect(dialect);
        Assumptions.assumeTrue(container != null ||
                (dialect != SqlDialect.FIREBIRD && dialect != SqlDialect.INFORMIX),
            "No Test support for " + dialect.name());

        try {
            initForDialect(dialect, container);

            // GIVEN
            String taskId1 = "test-task-id1";
            String taskId2 = "test-task-id2";
            io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType operation = io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType.ROLLBACK;

            TargetSystemAuditMark mark1 = new TargetSystemAuditMark(taskId1, operation);
            TargetSystemAuditMark mark2 = new TargetSystemAuditMark(taskId2, operation);

            // WHEN
            txManager.startSession(taskId1);
            sqlTargetSystemAuditMarker.mark(mark1);
            txManager.closeSession(taskId1);
            txManager.startSession(taskId2);
            sqlTargetSystemAuditMarker.mark(mark2);
            txManager.closeSession(taskId2);
            Set<TargetSystemAuditMark> marks = sqlTargetSystemAuditMarker.listAll();

            // THEN
            Assertions.assertEquals(2, marks.size());
        } finally {
            if (dataSource != null) {
                try {
                    dropTable();
                } catch (SQLException ignored) {
                }
                if (dataSource instanceof HikariDataSource) {
                    ((HikariDataSource) dataSource).close();
                }
            }
            if (container != null && container.isRunning()) {
                container.stop();
            }
        }
    }

    @ParameterizedTest(name = "[{index}] dialect={0} - Should remove all marks successfully")
    @MethodSource("dialectProvider")
    void removeOngoingTaskMark(SqlDialect dialect) {
        JdbcDatabaseContainer<?> container = createContainerForDialect(dialect);
        Assumptions.assumeTrue(container != null ||
                (dialect != SqlDialect.FIREBIRD && dialect != SqlDialect.INFORMIX),
            "No Test support for " + dialect.name());

        try {
            initForDialect(dialect, container);

            // GIVEN
            String taskId1 = "test-task-id1";
            String taskId2 = "test-task-id2";
            io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType operation = io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType.ROLLBACK;

            TargetSystemAuditMark mark1 = new TargetSystemAuditMark(taskId1, operation);
            TargetSystemAuditMark mark2 = new TargetSystemAuditMark(taskId2, operation);
            txManager.startSession(taskId1);
            sqlTargetSystemAuditMarker.mark(mark1);
            txManager.closeSession(taskId1);
            txManager.startSession(taskId2);
            sqlTargetSystemAuditMarker.mark(mark2);
            txManager.closeSession(taskId2);

            Set<TargetSystemAuditMark> marks = sqlTargetSystemAuditMarker.listAll();

            // WHEN
            for (TargetSystemAuditMark mark : marks) {
                sqlTargetSystemAuditMarker.clearMark(mark.getTaskId());
            }

            // THEN
            Assertions.assertEquals(0, sqlTargetSystemAuditMarker.listAll().size());
        } finally {
            if (dataSource != null) {
                try {
                    dropTable();
                } catch (SQLException ignored) {
                }
                if (dataSource instanceof HikariDataSource) {
                    ((HikariDataSource) dataSource).close();
                }
            }
            if (container != null && container.isRunning()) {
                container.stop();
            }
        }
    }
}
