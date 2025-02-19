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

import io.flamingock.cloud.transaction.sql.changes.happypath.HappyCreateTableClientsChange;
import io.flamingock.cloud.transaction.sql.changes.happypath.HappyInsertClientsChange;
import io.flamingock.cloud.transaction.sql.changes.unhappypath.UnhappyCreateTableClientsChange;
import io.flamingock.cloud.transaction.sql.utils.SqlTestUtil;
import io.flamingock.common.test.cloud.*;
import io.flamingock.common.test.cloud.deprecated.MockRunnerServerOld;
import io.flamingock.common.test.cloud.execution.ExecutionContinueRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionPlanRequestResponseMock;
import io.flamingock.common.test.cloud.mock.MockRequestResponseTask;
import io.flamingock.common.test.cloud.prototype.PrototypeClientSubmission;
import io.flamingock.common.test.cloud.prototype.PrototypeStage;
import io.flamingock.core.cloud.api.vo.OngoingStatus;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.configurator.standalone.StandaloneCloudBuilder;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.runner.PipelineExecutionException;
import io.flamingock.core.runner.Runner;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.UUID;

import static io.flamingock.core.cloud.api.audit.AuditEntryRequest.Status.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MysqlSqlCloudTransactionerTest {

    private static final MySQLContainer<?> mysql = SqlTestUtil.getMysqlContainer();
    private static final String STAGE_NAME_1 = "stage-1";
    public static final String EXECUTION_ID_1 = "execution-1";


    private final String apiToken = "FAKE_API_TOKEN";
    private final String organisationId = UUID.randomUUID().toString();
    private final String organisationName = "MyOrganisation";

    private final String projectId = UUID.randomUUID().toString();
    private final String projectName = "MyOrganisation";

    private final String serviceId = "clients-service-id";
    private final String serviceName = "clients-service";
    private final String environmentId = "development-env-id";
    private final String environmentName = "development";

    private final String credentialId = UUID.randomUUID().toString();
    private final int runnerServerPort = 8888;
    private final String jwt = "fake_jwt";

    private final PrototypeClientSubmission HAPPY_PROTOTYPE_CLIENT_SUBMISSION = new PrototypeClientSubmission(
            new PrototypeStage(STAGE_NAME_1, 0)
                    .addTask("create-table-clients", HappyCreateTableClientsChange.class.getName(), "execution", false)
                    .addTask("insert-clients", HappyInsertClientsChange.class.getName(), "execution", true)
    );

    private final PrototypeClientSubmission UNHAPPY_PROTOTYPE_CLIENT_SUBMISSION = new PrototypeClientSubmission(
            new PrototypeStage(STAGE_NAME_1, 0)
                    .addTask("unhappy-create-table-clients", UnhappyCreateTableClientsChange.class.getName(), "execution", false)
                    .addTask("unhappy-insert-clients", UnhappyCreateTableClientsChange.class.getName(), "execution", true)
    );

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
        //GIVEN
        try (
                Connection connection = SqlTestUtil.getConnection(mysql);
                SqlCloudTransactioner transactioner = new SqlCloudTransactioner()
        ) {
            mockRunnerServer
                    .withClientSubmissionBase(HAPPY_PROTOTYPE_CLIENT_SUBMISSION)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(EXECUTION_ID_1),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(EXECUTION_ID_1, "create-table-clients", EXECUTED),
                            new AuditRequestExpectation(EXECUTION_ID_1, "insert-clients", EXECUTED))
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
                    .addDependency(connection)
                    .addStage(new Stage(STAGE_NAME_1)
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
    void failedTasks() throws SQLException {
        //GIVEN
        try (
                Connection connection = SqlTestUtil.getConnection(mysql);
                SqlCloudTransactioner transactioner = new SqlCloudTransactioner()
        ) {
            mockRunnerServer
                    .withClientSubmissionBase(UNHAPPY_PROTOTYPE_CLIENT_SUBMISSION)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(EXECUTION_ID_1),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(EXECUTION_ID_1, "unhappy-create-table-clients", EXECUTED),
                            new AuditRequestExpectation(EXECUTION_ID_1, "unhappy-insert-clients", EXECUTION_FAILED),
                    new AuditRequestExpectation(EXECUTION_ID_1, "unhappy-insert-clients", ROLLED_BACK))
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
                    .addDependency(connection)
                    .addStage(new Stage(STAGE_NAME_1)
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
            SqlTestUtil.insertOngoingExecution(connection, "insert-clients");
            mockRunnerServer
                    .withClientSubmissionBase(HAPPY_PROTOTYPE_CLIENT_SUBMISSION)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(EXECUTION_ID_1, new MockRequestResponseTask("insert-clients", OngoingStatus.EXECUTION)),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(EXECUTION_ID_1, "create-table-clients", EXECUTED),
                            new AuditRequestExpectation(EXECUTION_ID_1, "insert-clients", EXECUTED))
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
                    .addDependency(connection)
                    .addStage(new Stage(STAGE_NAME_1)
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.sql.changes.happypath")))
                    .build()
                    .run();

            //THEN
            mockRunnerServer.verifyAllCalls();

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


}
