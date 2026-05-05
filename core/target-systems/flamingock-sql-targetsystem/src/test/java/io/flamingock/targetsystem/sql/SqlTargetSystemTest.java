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
package io.flamingock.targetsystem.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.flamingock.targetsystem.sql.changes.happypath._001__HappyCreateClientsTableChange;
import io.flamingock.targetsystem.sql.changes.happypath._002__HappyInsertClientsChange;
import io.flamingock.targetsystem.sql.changes.unhappypath._001__UnhappyCreateClientsTableChange;
import io.flamingock.targetsystem.sql.changes.unhappypath._002__UnhappyInsertClientsChange;
import io.flamingock.common.test.cloud.AuditRequestExpectation;
import io.flamingock.common.test.cloud.MockRunnerServer;
import io.flamingock.common.test.cloud.execution.ExecutionContinueRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionPlanRequestResponseMock;
import io.flamingock.common.test.cloud.mock.MockRequestResponseChange;
import io.flamingock.common.test.cloud.prototype.PrototypeClientSubmission;
import io.flamingock.common.test.cloud.prototype.PrototypeStage;
import io.flamingock.internal.util.Trio;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.builder.CloudChangeRunnerBuilder;
import io.flamingock.internal.common.core.metadata.MetadataLoader;
import io.flamingock.internal.core.operation.OperationException;
import io.flamingock.internal.core.builder.runner.Runner;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;

import static io.flamingock.cloud.api.vo.CloudAuditStatus.APPLIED;
import static io.flamingock.cloud.api.vo.CloudAuditStatus.FAILED;
import static io.flamingock.cloud.api.vo.CloudAuditStatus.ROLLED_BACK;


@Testcontainers
public class SqlTargetSystemTest {

    private static final String CLIENTS_TABLE = "client_table";
    private static final String ONGOING_CHANGES_TABLE = "flamingock_ongoing_changes";

    private static DataSource dataSource;
    private static MySQLTestHelper mysqlTestHelper;

    private final String apiToken = "FAKE_API_TOKEN";
    private final long organisationId = 1L;
    private final String organisationName = "MyOrganisation";
    private final long projectId = 2L;
    private final String projectName = "MyOrganisation";
    private final String serviceName = "clients-service";
    private final String environmentName = "development";
    private final long serviceId = 3L;
    private final long environmentId = 4L;
    private final long credentialId = 5L;
    private final int runnerServerPort = 8888;
    private final String jwt = "fake_jwt";

    private MockRunnerServer mockRunnerServer;
    private CloudChangeRunnerBuilder flamingockBuilder;

    @Container
    public static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @BeforeAll
    static void beforeAll() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mysqlContainer.getJdbcUrl());
        config.setUsername(mysqlContainer.getUsername());
        config.setPassword(mysqlContainer.getPassword());
        config.setDriverClassName(mysqlContainer.getDriverClassName());

        dataSource = new HikariDataSource(config);
        mysqlTestHelper = new MySQLTestHelper(dataSource);
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

        flamingockBuilder = FlamingockFactory.getCloudBuilder()
                .setApiToken(apiToken)
                .setHost("http://localhost:" + runnerServerPort)
                .setService(serviceName)
                .setEnvironment(environmentName);
    }

    @AfterEach
    void afterEach() throws Exception {
        mockRunnerServer.stop();
        mysqlTestHelper.dropTable(CLIENTS_TABLE);
        mysqlTestHelper.dropTable(ONGOING_CHANGES_TABLE);
    }

    @Test
    @DisplayName("Should follow the transactioner lifecycle")
    void happyPath() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addChange("create-clients-table", _001__HappyCreateClientsTableChange.class.getName(), "apply", false)
                        .addChange("insert-clients", _002__HappyInsertClientsChange.class.getName(), "apply", true)
        );

        //GIVEN
        try (MockedStatic<MetadataLoader> mocked = Mockito.mockStatic(MetadataLoader.class)) {
            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "create-clients-table", APPLIED),
                            new AuditRequestExpectation(executionId, "insert-clients", APPLIED)
                    ).start();

            //WHEN
            mocked.when(MetadataLoader::loadAggregated).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(_001__HappyCreateClientsTableChange.class, Collections.singletonList(Connection.class), null),
                    new Trio<>(_002__HappyInsertClientsChange.class, Collections.singletonList(Connection.class), null)
            ));

            SqlTargetSystem targetSystem = new SqlTargetSystem("mysql-ts", dataSource);

            flamingockBuilder
                    .addTargetSystem(targetSystem)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            mysqlTestHelper.checkTableExists(CLIENTS_TABLE);
            mysqlTestHelper.checkCount(CLIENTS_TABLE, 1);
            mysqlTestHelper.checkOngoingChange(ongoingCount -> ongoingCount == 0);
        }
    }

    @Test
    @DisplayName("Should keep the ongoing change when change fails")
    void failedChanges() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addChange("create-clients-table", _001__UnhappyCreateClientsTableChange.class.getName(), "apply", false)
                        .addChange("insert-clients", _002__UnhappyInsertClientsChange.class.getName(), "apply", true)
        );

        //GIVEN
        try (MockedStatic<MetadataLoader> mocked = Mockito.mockStatic(MetadataLoader.class)) {
            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "create-clients-table", APPLIED),
                            new AuditRequestExpectation(executionId, "insert-clients", FAILED),
                            new AuditRequestExpectation(executionId, "insert-clients", ROLLED_BACK)
                    ).start();

            //WHEN
            mocked.when(MetadataLoader::loadAggregated).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(_001__UnhappyCreateClientsTableChange.class, Collections.singletonList(Connection.class), null),
                    new Trio<>(_002__UnhappyInsertClientsChange.class, Collections.singletonList(Connection.class), Collections.singletonList(Connection.class))
            ));
            SqlTargetSystem targetSystem = new SqlTargetSystem("mysql-ts", dataSource);

            Runner runner = flamingockBuilder
                    .addTargetSystem(targetSystem)
                    .build();

            //THEN
            OperationException ex = Assertions.assertThrows(OperationException.class, runner::run);

            mockRunnerServer.verifyAllCalls();

            mysqlTestHelper.checkTableExists(CLIENTS_TABLE);
            mysqlTestHelper.checkCount(CLIENTS_TABLE, 0);
            mysqlTestHelper.checkEmptyTargetSystemAudiMarker();
        }
    }

    @Test
    @Disabled("adapt when adding cloud support")
    @DisplayName("Should send ongoing change in execution when is present in local database")
    void shouldSendOngoingChangeInExecutionPlan() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addChange("create-clients-table", _001__HappyCreateClientsTableChange.class.getName(), "apply", false)
                        .addChange("insert-clients", _002__HappyInsertClientsChange.class.getName(), "apply", true)
        );

        //GIVEN
        try (MockedStatic<MetadataLoader> mocked = Mockito.mockStatic(MetadataLoader.class)) {
            mysqlTestHelper.insertOngoingExecution("insert-clients");
            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId, new MockRequestResponseChange("insert-clients", TargetSystemAuditMarkType.APPLIED)),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "create-clients-table", APPLIED),
                            new AuditRequestExpectation(executionId, "insert-clients", APPLIED)
                    ).start();

            //WHEN
            mocked.when(MetadataLoader::loadAggregated).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(_001__HappyCreateClientsTableChange.class, Collections.singletonList(Connection.class), null),
                    new Trio<>(_002__HappyInsertClientsChange.class, Collections.singletonList(Connection.class), null)
            ));
            SqlTargetSystem targetSystem = new SqlTargetSystem("mysql-ts", dataSource);

            flamingockBuilder
                    .addTargetSystem(targetSystem)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            mysqlTestHelper.checkTableExists(CLIENTS_TABLE);
            mysqlTestHelper.checkCount(CLIENTS_TABLE, 1);
            mysqlTestHelper.checkOngoingChange(ongoingCount -> ongoingCount == 0);
        }
    }
}
