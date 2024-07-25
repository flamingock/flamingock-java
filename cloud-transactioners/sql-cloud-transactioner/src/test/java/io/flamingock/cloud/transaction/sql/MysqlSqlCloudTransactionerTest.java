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

import io.flamingock.cloud.transaction.sql.utils.CloudMockBuilder;
import io.flamingock.cloud.transaction.sql.utils.SqlTestUtil;
import io.flamingock.core.cloud.planner.ExecutionPlanRequest;
import io.flamingock.core.cloud.planner.ExecutionPlanResponse;
import io.flamingock.core.cloud.planner.StageRequest;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.runner.Runner;
import io.flamingock.core.util.http.Http;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

public class MysqlSqlCloudTransactionerTest {

    private static final MySQLContainer<?> mysql = SqlTestUtil.getMysqlContainer();

    @BeforeAll
    static void beforeAll() {
        mysql.start();
    }

    @AfterEach
    void afterEch() throws SQLException {
        SqlTestUtil.cleanTable(mysql, "ONGOING_TASKS");
        SqlTestUtil.dropTableSafe(mysql, "CLIENTS");
        SqlTestUtil.dropTableSafe(mysql, "CLIENTS_2");
    }

    @Test
    @DisplayName("Should follow the transactioner lifecycle")
    void happyPath() throws SQLException {

        //GIVEN
        try (
                MockedStatic<Http> http = Mockito.mockStatic(Http.class);
                Connection connection = SqlTestUtil.getConnection(mysql)
        ) {
            CloudMockBuilder cloudMockBuilder = new CloudMockBuilder();
            cloudMockBuilder
                    .addSingleExecutionPlanResponse("stage1", "create-table-clients", "insert-clients")
                    .addContinueExecutionPlanResponse()
                    .setHttp(http)
                    .mockServer();

            SqlCloudTransactioner sqlCloudTransactioner = Mockito.spy(new SqlCloudTransactioner()
                    .setUrl(mysql.getJdbcUrl())
                    .setUser(mysql.getUsername())
                    .setPassword(mysql.getPassword())
                    .setDialect(SqlDialect.MYSQL));

            Runner runner = FlamingockStandalone.cloud()
                    .setApiToken("FAKE_API_TOKEN")
                    .setHost("https://fake-cloud-server.io")
                    .setService("test-service")
                    .setEnvironment("test-environment")
                    .setCloudTransactioner(sqlCloudTransactioner)
                    .addStage(new Stage("stage-name")
                            .setName("stage1")
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.sql.changes.happypath")))
                    .build();

            //WHEN
            runner.execute();

            //THEN
            //2 execution plans: First to execute and second to continue
            verify(cloudMockBuilder.getRequestWithBody(), new Times(2)).execute(ExecutionPlanResponse.class);
            //2 audit writes
            verify(cloudMockBuilder.getRequestWithBody(), new Times(2)).execute();
            //DELETE LOCK
            verify(cloudMockBuilder.getBasicRequest(), new Times(1)).execute();

            // check clients changes
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT id, name FROM CLIENTS")) {
                ResultSet resultSet = preparedStatement.executeQuery();
                int counter = 0;
                while (resultSet.next()) {
                    assertEquals(1, resultSet.getInt(1));
                    assertEquals("Robert Mccoy", resultSet.getString(2));
                    counter++;
                }
                assertEquals(1, counter);
            }

            // check ongoing status
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT count(task_id) FROM ONGOING_TASKS")) {
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    assertEquals(0, resultSet.getInt(1));

                }
            }
        }
    }

    @Test
    @DisplayName("Should rollback the ongoing deletion when a task fails")
    void failedTasks() throws SQLException {
        //GIVEN
        try (
                MockedStatic<Http> http = Mockito.mockStatic(Http.class);
                Connection connection = SqlTestUtil.getConnection(mysql)
        ) {
            CloudMockBuilder cloudMockBuilder = new CloudMockBuilder();
            cloudMockBuilder
                    .addSingleExecutionPlanResponse("stage1", "create-table-clients", "failed-insert-clients")
                    .addContinueExecutionPlanResponse()
                    .setHttp(http)
                    .mockServer();

            SqlCloudTransactioner sqlCloudTransactioner = Mockito.spy(new SqlCloudTransactioner()
                    .setUrl(mysql.getJdbcUrl())
                    .setUser(mysql.getUsername())
                    .setPassword(mysql.getPassword())
                    .setDialect(SqlDialect.MYSQL));

            Runner runner = FlamingockStandalone.cloud()
                    .setApiToken("FAKE_API_TOKEN")
                    .setHost("https://fake-cloud-server.io")
                    .setService("test-service")
                    .setEnvironment("test-environment")
                    .setCloudTransactioner(sqlCloudTransactioner)
                    .addStage(new Stage("stage-name")
                            .setName("stage1")
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.sql.changes.failed")))
                    .build();
            //WHEN
            Assertions.assertThrows(RuntimeException.class, runner::run);

            //THEN
            //1 execution plans: First to execute and second(to continue) is not performed because the first execution failed in second task
            verify(cloudMockBuilder.getRequestWithBody(), new Times(1)).execute(ExecutionPlanResponse.class);
            //1 audit writes: Only one because second task failed
            verify(cloudMockBuilder.getRequestWithBody(), new Times(3)).execute();
            //DELETE LOCK
            verify(cloudMockBuilder.getBasicRequest(), new Times(1)).execute();

            // check clients changes
            SqlTestUtil.checkCount(connection, "CLIENTS_2", 0);
            // check ongoing status
            SqlTestUtil.checkAtLeastOneOngoingTask(connection);
        }
    }

    @Test
    @DisplayName("Should send ongoing task in execution when is present in local database")
    void shouldSendOngoingTaskInExecutionPlan() throws SQLException {
        //GIVEN
        try (
                MockedStatic<Http> http = Mockito.mockStatic(Http.class);
                Connection connection = SqlTestUtil.getConnection(mysql)
        ) {
            SqlTestUtil.insertOngoingTask(connection, "failed-insert-clients");

            CloudMockBuilder cloudMockBuilder = new CloudMockBuilder();
            cloudMockBuilder
                    .addSingleExecutionPlanResponse("stage1", "create-table-clients", "failed-insert-clients")
                    .addContinueExecutionPlanResponse()
                    .setHttp(http)
                    .mockServer();

            SqlCloudTransactioner sqlCloudTransactioner = Mockito.spy(new SqlCloudTransactioner()
                    .setUrl(mysql.getJdbcUrl())
                    .setUser(mysql.getUsername())
                    .setPassword(mysql.getPassword())
                    .setDialect(SqlDialect.MYSQL));

            Runner runner = FlamingockStandalone.cloud()
                    .setApiToken("FAKE_API_TOKEN")
                    .setHost("https://fake-cloud-server.io")
                    .setService("test-service")
                    .setEnvironment("test-environment")
                    .setCloudTransactioner(sqlCloudTransactioner)
                    .addStage(new Stage("stage-name")
                            .setName("stage1")
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.sql.changes.failed")))
                    .build();

            //WHEN
            Assertions.assertThrows(RuntimeException.class, runner::run);

            //1 execution plans: First is tokenRequest, second to execute and third(to continue) is not performed because
            // the first execution failed in second task
            ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
            verify(cloudMockBuilder.getRequestWithBody(), new Times(5)).setBody(bodyCaptor.capture());

            ExecutionPlanRequest planRequest = (ExecutionPlanRequest) bodyCaptor.getAllValues().get(1);
            List<StageRequest.Task> tasks = planRequest.getClientSubmission().getStages().get(0).getTasks();
            assertEquals("create-table-clients", tasks.get(0).getId());
            assertEquals(StageRequest.TaskOngoingStatus.NONE, tasks.get(0).getOngoingStatus());
            assertEquals("failed-insert-clients", tasks.get(1).getId());
            assertEquals(StageRequest.TaskOngoingStatus.EXECUTION, tasks.get(1).getOngoingStatus());
        }
    }


}
