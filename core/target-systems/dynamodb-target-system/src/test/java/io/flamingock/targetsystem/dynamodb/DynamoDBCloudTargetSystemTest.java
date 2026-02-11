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
package io.flamingock.targetsystem.dynamodb;

import io.flamingock.dynamodb.kit.DynamoDBTestContainer;
import io.flamingock.targetsystem.dynamodb.changes.common.UserEntity;
import io.flamingock.targetsystem.dynamodb.changes.happypath._001__HappyCreateTableClientsChange;
import io.flamingock.targetsystem.dynamodb.changes.happypath._002__HappyInsertClientsChange;
import io.flamingock.targetsystem.dynamodb.changes.unhappypath._001__UnhappyCreateTableClientsChange;
import io.flamingock.targetsystem.dynamodb.changes.unhappypath._002__UnhappyInsertionClientsChange;
import io.flamingock.common.test.cloud.AuditRequestExpectation;
import io.flamingock.common.test.cloud.MockRunnerServer;
import io.flamingock.common.test.cloud.execution.ExecutionContinueRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionPlanRequestResponseMock;
import io.flamingock.common.test.cloud.mock.MockRequestResponseTask;
import io.flamingock.common.test.cloud.prototype.PrototypeClientSubmission;
import io.flamingock.common.test.cloud.prototype.PrototypeStage;
import io.flamingock.internal.util.Trio;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.builder.CloudChangeRunnerBuilder;
import io.flamingock.internal.common.core.util.Deserializer;
import io.flamingock.internal.core.operation.OperationException;
import io.flamingock.internal.core.builder.runner.Runner;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Collections;
import java.util.UUID;

import static io.flamingock.internal.common.cloud.audit.AuditEntryRequest.Status.APPLIED;
import static io.flamingock.internal.common.cloud.audit.AuditEntryRequest.Status.FAILED;
import static io.flamingock.internal.common.cloud.audit.AuditEntryRequest.Status.ROLLED_BACK;

@Testcontainers
public class DynamoDBCloudTargetSystemTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBCloudTargetSystemTest.class);

    @Container
    static GenericContainer<?> dynamoContainer = DynamoDBTestContainer.createContainer();

    private static DynamoDbClient client;
    private static DynamoDBTestHelper dynamoDBTestHelper;

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
    private CloudChangeRunnerBuilder flamingockBuilder;

    @BeforeEach
    void beforeEach() throws Exception {
        logger.info("Creating DynamoDB client from TestContainer...");
        client = DynamoDBTestContainer.createClient(dynamoContainer);

        //We use different client, as the transactioner will close it
        dynamoDBTestHelper = new DynamoDBTestHelper(DynamoDBTestContainer.createClient(dynamoContainer));

        logger.info("Starting Mock Server...");
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
        //tear down
        logger.info("Stopping Mock Server...");
        mockRunnerServer.stop();
    }

    @Test
    @DisplayName("Should follow the transactioner lifecycle")
    void happyPath() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addTask("create-table-clients", _001__HappyCreateTableClientsChange.class.getName(), "apply", false)
                        .addTask("insert-clients", _002__HappyInsertClientsChange.class.getName(), "apply", true)
        );

        //GIVEN
        try (
            MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)
        ) {

            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "create-table-clients", APPLIED),
                            new AuditRequestExpectation(executionId, "insert-clients", APPLIED)
                    ).start();

            DynamoDBTargetSystem dynamoTargetSystem = new DynamoDBTargetSystem("dynamodb-ts", dynamoDBTestHelper.getDynamoDBClient());
            //WHEN
            mocked.when(Deserializer::readMetadataFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(_001__HappyCreateTableClientsChange.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002__HappyInsertClientsChange.class, Collections.singletonList(DynamoDbClient.class))
            ));
            flamingockBuilder
                    .addTargetSystem(dynamoTargetSystem)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            // check clients changes
            client.close();
            dynamoDBTestHelper.checkCount(
                    DynamoDbEnhancedClient.builder()
                            .dynamoDbClient(dynamoDBTestHelper.getDynamoDBClient())
                            .build()
                            .table(UserEntity.tableName, TableSchema.fromBean(UserEntity.class)),
                    1);

            //TODO when cloud enabled
            // check ongoing status
//            dynamoDBTestHelper.checkOngoingTask(ongoingCount -> ongoingCount == 0);
        }
    }

    @Test
    @Disabled("adapt when adding cloud support")
    @DisplayName("Should rollback the ongoing deletion when a task fails")
    void failedTasks() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addTask("unhappy-create-table-clients", _001__UnhappyCreateTableClientsChange.class.getName(), "apply", false)
                        .addTask("unhappy-insert-clients", _002__UnhappyInsertionClientsChange.class.getName(), "apply", true)
        );

        //GIVEN
        try (
                MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)
        ) {

            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "unhappy-create-table-clients", APPLIED),
                            new AuditRequestExpectation(executionId, "unhappy-insert-clients", FAILED),
                            new AuditRequestExpectation(executionId, "unhappy-insert-clients", ROLLED_BACK)
                    ).start();

            DynamoDBTargetSystem dynamoTargetSystem = new DynamoDBTargetSystem("dynamodb-ts", dynamoDBTestHelper.getDynamoDBClient());

            //WHEN
            mocked.when(Deserializer::readMetadataFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(_001__UnhappyCreateTableClientsChange.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002__UnhappyInsertionClientsChange.class, Collections.singletonList(DynamoDbClient.class))
            ));
            Runner runner = flamingockBuilder
                    .addTargetSystem(dynamoTargetSystem)
                    .build();

            //THEN
            mockRunnerServer.verifyAllCalls();

            OperationException ex = Assertions.assertThrows(OperationException.class, runner::run);

            // check clients changes
            dynamoDBTestHelper.checkCount(
                    DynamoDbEnhancedClient.builder()
                            .dynamoDbClient(dynamoDBTestHelper.getDynamoDBClient())
                            .build()
                            .table(UserEntity.tableName, TableSchema.fromBean(UserEntity.class)),
                    0);
            // check ongoing status
            dynamoDBTestHelper.checkEmptyTargetSystemAudiMarker();
        }
    }


    //TODO verify the server is called with the right parameters. among other, it sends the ongoing status
    @Test
    @Disabled("adapt when adding cloud support")
    @DisplayName("Should send ongoing task in execution when is present in local database")
    void shouldSendOngoingTaskInExecutionPlan() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addTask("create-table-clients", _001__HappyCreateTableClientsChange.class.getName(), "apply", false)
                        .addTask("insert-clients", _002__HappyInsertClientsChange.class.getName(), "apply", true)
        );

        //GIVEN
        try (
                MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)
        ) {

            dynamoDBTestHelper.insertOngoingExecution("insert-clients");
            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId, new MockRequestResponseTask("insert-clients", TargetSystemAuditMarkType.APPLIED)),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "create-table-clients", APPLIED),
                            new AuditRequestExpectation(executionId, "insert-clients", APPLIED)
                    ).start();

            DynamoDBTargetSystem dynamoTargetSystem = new DynamoDBTargetSystem("dynamodb-ts", dynamoDBTestHelper.getDynamoDBClient());

            //WHEN
            mocked.when(Deserializer::readMetadataFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(_001__HappyCreateTableClientsChange.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002__HappyInsertClientsChange.class, Collections.singletonList(DynamoDbClient.class))
            ));
            flamingockBuilder
                    .addTargetSystem(dynamoTargetSystem)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            // check clients changes
            client.close();
            dynamoDBTestHelper.checkCount(
                    DynamoDbEnhancedClient.builder()
                            .dynamoDbClient(dynamoDBTestHelper.getDynamoDBClient())
                            .build()
                            .table(UserEntity.tableName, TableSchema.fromBean(UserEntity.class)),
                    1);
            // check ongoing status
            dynamoDBTestHelper.checkOngoingTask(ongoingCount -> ongoingCount == 0);
        }
    }

}
