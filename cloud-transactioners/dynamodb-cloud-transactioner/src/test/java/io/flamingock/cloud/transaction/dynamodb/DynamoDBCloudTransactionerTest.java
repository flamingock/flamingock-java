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

package io.flamingock.cloud.transaction.dynamodb;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import io.flamingock.cloud.transaction.dynamodb.changes.common.UserEntity;
import io.flamingock.cloud.transaction.dynamodb.changes.happypath.HappyCreateTableClientsChange;
import io.flamingock.cloud.transaction.dynamodb.changes.happypath.HappyInsertClientsChange;
import io.flamingock.cloud.transaction.dynamodb.changes.unhappypath.UnhappyCreateTableClientsChange;
import io.flamingock.cloud.transaction.dynamodb.changes.unhappypath.UnhappyInsertionClientsChange;
import io.flamingock.common.test.cloud.AuditRequestExpectation;
import io.flamingock.common.test.cloud.MockRunnerServer;
import io.flamingock.common.test.cloud.execution.ExecutionContinueRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionPlanRequestResponseMock;
import io.flamingock.common.test.cloud.mock.MockRequestResponseTask;
import io.flamingock.common.test.cloud.prototype.PrototypeClientSubmission;
import io.flamingock.common.test.cloud.prototype.PrototypeStage;
import io.flamingock.commons.utils.Trio;
import io.flamingock.core.cloud.api.vo.OngoingStatus;
import io.flamingock.core.builder.Flamingock;
import io.flamingock.core.builder.CloudFlamingockBuilder;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.core.runner.PipelineExecutionException;
import io.flamingock.core.runner.Runner;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.UUID;

import static io.flamingock.core.cloud.api.audit.AuditEntryRequest.Status.*;

public class DynamoDBCloudTransactionerTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBCloudTransactionerTest.class);

    private static DynamoDBProxyServer dynamoDBLocal;
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
    private CloudFlamingockBuilder flamingockBuilder;

    @BeforeEach
    void beforeEach() throws Exception {
        logger.info("Starting DynamoDB Local...");
        dynamoDBLocal = ServerRunner.createServerFromCommandLineArgs(
                new String[]{
                        "-inMemory",
                        "-port",
                        "8000"
                }
        );
        dynamoDBLocal.start();

        client = getDynamoDbClient();

        //We use different client, as the transactioner will close it
        dynamoDBTestHelper = new DynamoDBTestHelper(getDynamoDbClient());

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

        flamingockBuilder = Flamingock.cloud()
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

        logger.info("Stopping DynamoDB Local...");
        if(dynamoDBLocal != null) {
            dynamoDBLocal.stop();
        }
    }

    @Test
    @DisplayName("Should follow the transactioner lifecycle")
    void happyPath() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addTask("create-table-clients", HappyCreateTableClientsChange.class.getName(), "execution", false)
                        .addTask("insert-clients", HappyInsertClientsChange.class.getName(), "execution", true)
        );

        //GIVEN
        try (
                DynamoDBCloudTransactioner transactioner = new DynamoDBCloudTransactioner(client);
                MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)
        ) {
            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "create-table-clients", EXECUTED),
                            new AuditRequestExpectation(executionId, "insert-clients", EXECUTED)
                    ).start();

            DynamoDBCloudTransactioner dynamoDBCloudTransactioner = Mockito.spy(transactioner);

            //WHEN
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(HappyCreateTableClientsChange.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(HappyInsertClientsChange.class, Collections.singletonList(DynamoDbClient.class))
            ));
            flamingockBuilder
                    .setCloudTransactioner(dynamoDBCloudTransactioner)
                    //.addStage(new Stage(stageName)
//                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.dynamodb.changes.happypath")))
                    .addDependency(client)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            // check clients changes
            client.close();
            dynamoDBTestHelper.checkCount(
                    DynamoDbEnhancedClient.builder()
                            .dynamoDbClient(dynamoDBTestHelper.getDynamoDbClient())
                            .build()
                            .table(UserEntity.tableName, TableSchema.fromBean(UserEntity.class)),
                    1);
            // check ongoing status
            dynamoDBTestHelper.checkOngoingTask(ongoingCount -> ongoingCount == 0);
        }
    }

    @Test
    @DisplayName("Should rollback the ongoing deletion when a task fails")
    void failedTasks() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addTask("unhappy-create-table-clients", UnhappyCreateTableClientsChange.class.getName(), "execution", false)
                        .addTask("unhappy-insert-clients", UnhappyInsertionClientsChange.class.getName(), "execution", true)
        );

        //GIVEN
        try (
                DynamoDBCloudTransactioner transactioner = new DynamoDBCloudTransactioner(client);
                MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)
        ) {
            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "unhappy-create-table-clients", EXECUTED),
                            new AuditRequestExpectation(executionId, "unhappy-insert-clients", EXECUTION_FAILED),
                            new AuditRequestExpectation(executionId, "unhappy-insert-clients", ROLLED_BACK)
                    ).start();

            DynamoDBCloudTransactioner dynamoDBCloudTransactioner = Mockito.spy(transactioner);

            //WHEN
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(UnhappyCreateTableClientsChange.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(UnhappyInsertionClientsChange.class, Collections.singletonList(DynamoDbClient.class))
            ));
            Runner runner = flamingockBuilder
                    .setCloudTransactioner(dynamoDBCloudTransactioner)
                    //.addStage(new Stage(stageName)
//                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.dynamodb.changes.unhappypath")))
                    .addDependency(client)
                    .build();

            //THEN
            mockRunnerServer.verifyAllCalls();

            PipelineExecutionException ex = Assertions.assertThrows(PipelineExecutionException.class, runner::run);

            // check clients changes
            dynamoDBTestHelper.checkCount(
                    DynamoDbEnhancedClient.builder()
                            .dynamoDbClient(dynamoDBTestHelper.getDynamoDbClient())
                            .build()
                            .table(UserEntity.tableName, TableSchema.fromBean(UserEntity.class)),
                    0);
            // check ongoing status
            dynamoDBTestHelper.checkAtLeastOneOngoingTask();
        }
    }


    //TODO verify the server is called with the right parameters. among other, it sends the ongoing status
    @Test
    @DisplayName("Should send ongoing task in execution when is present in local database")
    void shouldSendOngoingTaskInExecutionPlan() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addTask("create-table-clients", HappyCreateTableClientsChange.class.getName(), "execution", false)
                        .addTask("insert-clients", HappyInsertClientsChange.class.getName(), "execution", true)
        );

        //GIVEN
        try (
                DynamoDBCloudTransactioner transactioner = new DynamoDBCloudTransactioner(client);
                MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)
        ) {
            dynamoDBTestHelper.insertOngoingExecution("insert-clients");
            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId, new MockRequestResponseTask("insert-clients", OngoingStatus.EXECUTION)),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "create-table-clients", EXECUTED),
                            new AuditRequestExpectation(executionId, "insert-clients", EXECUTED)
                    ).start();

            DynamoDBCloudTransactioner dynamoDBCloudTransactioner = Mockito.spy(transactioner);

            //WHEN
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(HappyCreateTableClientsChange.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(HappyInsertClientsChange.class, Collections.singletonList(DynamoDbClient.class))
            ));
            flamingockBuilder
                    .setCloudTransactioner(dynamoDBCloudTransactioner)
                    //.addStage(new Stage(stageName)
//                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.dynamodb.changes.happypath")))
                    .addDependency(client)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            // check clients changes
            client.close();
            dynamoDBTestHelper.checkCount(
                    DynamoDbEnhancedClient.builder()
                            .dynamoDbClient(dynamoDBTestHelper.getDynamoDbClient())
                            .build()
                            .table(UserEntity.tableName, TableSchema.fromBean(UserEntity.class)),
                    1);
            // check ongoing status
            dynamoDBTestHelper.checkOngoingTask(ongoingCount -> ongoingCount == 0);
        }
    }

    private static DynamoDbClient getDynamoDbClient() {
        try {
            return DynamoDbClient.builder()
                    .region(Region.EU_WEST_1)
                    .endpointOverride(new URI("http://localhost:8000"))
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create("dummye", "dummye")
                            )
                    )
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
