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

import io.flamingock.cloud.transaction.sql.changes.unhappypath.UnhappyCreateTableClientsChange;
import io.flamingock.cloud.transaction.sql.changes.unhappypath.UnhappyInsertionClientsChange;
import io.flamingock.cloud.transaction.sql.changes.happypath.HappyCreateTableClientsChange;
import io.flamingock.cloud.transaction.sql.changes.happypath.HappyInsertClientsChange;
import io.flamingock.cloud.transaction.sql.utils.SqlTestUtil;
import io.flamingock.common.test.cloud.AuditEntryExpectation;
import io.flamingock.common.test.cloud.MockRunnerServer;

import static io.flamingock.core.cloud.api.audit.AuditEntryRequest.Status.EXECUTED;
import static io.flamingock.core.cloud.api.audit.AuditEntryRequest.Status.EXECUTION_FAILED;
import static io.flamingock.core.cloud.api.audit.AuditEntryRequest.Status.ROLLED_BACK;

import io.flamingock.core.cloud.api.transaction.OngoingStatus;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.configurator.standalone.StandaloneCloudBuilder;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.runner.PipelineExecutionException;
import io.flamingock.core.runner.Runner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

public class MysqlSqlCloudTransactionerTest {

    private static final MySQLContainer<?> mysql = SqlTestUtil.getMysqlContainer();


    private final String apiToken = "FAKE_API_TOKEN";
    private final String organisationId = UUID.randomUUID().toString();
    private final String organisationName = "MyOrganisation";

    private final String projectId = UUID.randomUUID().toString();
    private final String projectName = "MyOrganisation";

    private final String serviceName = "clients-service";
    private final String environmentName = "development";
    private final String serviceId = "clients-service-id";
    private final String environmentId = "development-env-id";
    private final String credentialId = UUID.randomUUID().toString();
    private final int runnerServerPort = 8888;
    private final String jwt = "fake_jwt";

    private MockRunnerServer mockRunnerServer;
    private StandaloneCloudBuilder flamingockBuilder;

    @BeforeAll
    static void beforeAll() {
        mysql.start();
    }


    @BeforeEach
    void beforeEach() {
        mockRunnerServer = new MockRunnerServer()
                .setServerPort(runnerServerPort)
                .setOrganisationId(organisationId)
                .setOrganisationName(organisationName)
                .setProjectId(projectId)
                .setProjectName(projectName)
                .setServiceId(serviceId)
                .setServiceName(serviceName)
                .setEnvironmentId(environmentId)
                .setEnvironmentName(environmentName)
                .setCredentialId(credentialId)
                .setApiToken(apiToken)
                .setJwt(jwt);

        flamingockBuilder = FlamingockStandalone.cloud()
                .setApiToken(apiToken)
                .setHost("http://localhost:" + runnerServerPort)
                .setService(serviceName)
                .setEnvironment(environmentName);
    }

    @AfterEach
    void afterEach() throws SQLException {
        //tear down
        mockRunnerServer.stop();
        SqlTestUtil.cleanTable(mysql, "ONGOING_TASKS");
        SqlTestUtil.dropTableSafe(mysql, "CLIENTS");
        SqlTestUtil.dropTableSafe(mysql, "CLIENTS_2");
    }

    @Test
    @DisplayName("Should follow the transactioner lifecycle")
    void happyPath() throws SQLException {
        List<AuditEntryExpectation> auditEntryExpectations = new LinkedList<>();
        auditEntryExpectations.add(new
                AuditEntryExpectation(
                "create-table-clients",
                EXECUTED,
                HappyCreateTableClientsChange.class.getName(),
                "execution"
        ));
        auditEntryExpectations.add(new
                AuditEntryExpectation(
                "insert-clients",
                EXECUTED,
                HappyInsertClientsChange.class.getName(),
                "execution"
        ));
        //GIVEN
        try (
                Connection connection = SqlTestUtil.getConnection(mysql);
                SqlCloudTransactioner transactioner = new SqlCloudTransactioner()
        ) {


            String executionId = "execution-1";
            String stageName = "stage-1";
            mockRunnerServer
                    .addSimpleStageExecutionPlan(executionId, stageName, auditEntryExpectations)
                    .addExecutionWithAllTasksRequestResponse(executionId)
                    .addExecutionContinueRequestResponse()
                    .start();

            SqlCloudTransactioner sqlCloudTransactioner = Mockito.spy(transactioner
                    .setUrl(mysql.getJdbcUrl())
                    .setUser(mysql.getUsername())
                    .setPassword(mysql.getPassword())
                    .setDialect(SqlDialect.MYSQL)
            );

            //WHEN
            flamingockBuilder
                    .setCloudTransactioner(sqlCloudTransactioner)
                    .addStage(new Stage(stageName)
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.sql.changes.happypath")))
                    .build()
                    .execute();

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
    void failedTasks()  throws SQLException {
        List<AuditEntryExpectation> auditEntryExpectations = new LinkedList<>();
        auditEntryExpectations.add(new
                AuditEntryExpectation(
                "unhappy-create-table-clients",
                EXECUTED,
                UnhappyCreateTableClientsChange.class.getName(),
                "execution"
        ));

        auditEntryExpectations.add(new
                AuditEntryExpectation(
                "unhappy-insert-clients",
                EXECUTION_FAILED,
                UnhappyInsertionClientsChange.class.getName(),
                "execution"
        ));

        auditEntryExpectations.add(new
                AuditEntryExpectation(
                "unhappy-insert-clients",
                ROLLED_BACK,
                UnhappyInsertionClientsChange.class.getName(),
                "native_db_engine"
        ));
        //GIVEN
        try (
                Connection connection = SqlTestUtil.getConnection(mysql);
                SqlCloudTransactioner transactioner = new SqlCloudTransactioner()
        ) {
            String executionId = "execution-1";
            String stageName = "stage-1";
            mockRunnerServer
                    .addSimpleStageExecutionPlan(executionId, stageName, auditEntryExpectations)
                    .addExecutionWithAllTasksRequestResponse(executionId)
                    .addExecutionContinueRequestResponse()
                    .start();

            SqlCloudTransactioner sqlCloudTransactioner = Mockito.spy(transactioner
                    .setUrl(mysql.getJdbcUrl())
                    .setUser(mysql.getUsername())
                    .setPassword(mysql.getPassword())
                    .setDialect(SqlDialect.MYSQL)
            );

            //WHEN
            Runner runner = flamingockBuilder
                    .setCloudTransactioner(sqlCloudTransactioner)
                    .addStage(new Stage(stageName)
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.sql.changes.unhappypath")))
                    .build();
            PipelineExecutionException ex = Assertions.assertThrows(PipelineExecutionException.class, runner::run);

            // check clients changes
            SqlTestUtil.checkCount(connection, "CLIENTS_2", 0);
            // check ongoing status
            SqlTestUtil.checkAtLeastOneOngoingTask(connection);


        }
    }


    //TODO verify the server is called with the right parameters. among other, it sends the ongoing status
    @Test
    @DisplayName("Should send ongoing task in execution when is present in local database")
    void shouldSendOngoingTaskInExecutionPlan() throws SQLException {
        //GIVEN
        try (
                Connection connection = SqlTestUtil.getConnection(mysql);
                SqlCloudTransactioner transactioner = new SqlCloudTransactioner()
        ) {
            List<AuditEntryExpectation> auditEntryExpectations = new LinkedList<>();
            auditEntryExpectations.add(new
                    AuditEntryExpectation(
                    "unhappy-create-table-clients",
                    EXECUTED,
                    UnhappyCreateTableClientsChange.class.getName(),
                    "execution"
            ));

            auditEntryExpectations.add(new
                    AuditEntryExpectation(
                    "unhappy-insert-clients",
                    EXECUTION_FAILED,
                    UnhappyInsertionClientsChange.class.getName(),
                    "execution"
            ));

            auditEntryExpectations.add(new
                    AuditEntryExpectation(
                    "unhappy-insert-clients",
                    ROLLED_BACK,
                    UnhappyInsertionClientsChange.class.getName(),
                    "native_db_engine"
            ));
            String executionId = "execution-1";
            String stageName = "stage-1";


            SqlTestUtil.insertOngoingExecution(connection, "failed-insert-clients");
            List<OngoingStatus> ongoingStatuses = Collections.singletonList(new OngoingStatus("failed-insert-clients", OngoingStatus.Operation.EXECUTION));
            mockRunnerServer
                    .addSimpleStageExecutionPlan(executionId, stageName, auditEntryExpectations, ongoingStatuses)
                    .addExecutionWithAllTasksRequestResponse(executionId)
                    .addExecutionContinueRequestResponse()
                    .start();

            SqlCloudTransactioner sqlCloudTransactioner = Mockito.spy(transactioner
                    .setUrl(mysql.getJdbcUrl())
                    .setUser(mysql.getUsername())
                    .setPassword(mysql.getPassword())
                    .setDialect(SqlDialect.MYSQL)
            );

            //WHEN


            Runner runner = flamingockBuilder
                    .setCloudTransactioner(sqlCloudTransactioner)
                    .addStage(new Stage(stageName)
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.sql.changes.unhappypath")))
                    .build();

            //then
            PipelineExecutionException ex = Assertions.assertThrows(PipelineExecutionException.class, runner::run);


        }
    }


}
