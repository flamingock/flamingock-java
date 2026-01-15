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
package io.flamingock.targetsystem.mongodb.springdata;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.external.targets.TargetSystem;
import io.flamingock.targetsystem.mongodb.springdata.changes.happypath._001__HappyCreateClientsCollectionChange;
import io.flamingock.targetsystem.mongodb.springdata.changes.happypath._002__HappyInsertClientsChange;
import io.flamingock.targetsystem.mongodb.springdata.changes.unhappypath._001__UnhappyCreateClientsCollectionChange;
import io.flamingock.targetsystem.mongodb.springdata.changes.unhappypath._002__UnhappyInsertClientsChange;
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
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.internal.core.runner.Runner;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.UUID;

import static io.flamingock.internal.common.cloud.audit.AuditEntryRequest.Status.APPLIED;
import static io.flamingock.internal.common.cloud.audit.AuditEntryRequest.Status.FAILED;
import static io.flamingock.internal.common.cloud.audit.AuditEntryRequest.Status.ROLLED_BACK;

@Testcontainers
public class MongoDBSpringDataTargetSystemTest {

    private static final Logger logger = LoggerFactory.getLogger(MongoDBSpringDataTargetSystemTest.class);

    private static final String DB_NAME = "test";
    private static final String CLIENTS_COLLECTION = "clientCollection";

    private static MongoDatabase testDatabase;
    private static MongoTemplate mongoTemplate;
    private static MongoDBTestHelper mongoDBTestHelper;

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

    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"));

    @BeforeAll
    static void beforeAll() {
        MongoClient mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        testDatabase = mongoClient.getDatabase(DB_NAME);
        mongoTemplate = new MongoTemplate(mongoClient, DB_NAME);
        mongoDBTestHelper = new MongoDBTestHelper(testDatabase);
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
        //tear down
        mockRunnerServer.stop();

        testDatabase.getCollection(CLIENTS_COLLECTION).drop();
    }

    @Test
    @DisplayName("Should follow the targetSystem lifecycle")
    void happyPath() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addTask("create-clients-collection", _001__HappyCreateClientsCollectionChange.class.getName(), "apply", false)
                        .addTask("insert-clients", _002__HappyInsertClientsChange.class.getName(), "apply", true)
        );

        //GIVEN
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "create-clients-collection", APPLIED),
                            new AuditRequestExpectation(executionId, "insert-clients", APPLIED)
                    ).start();


            //WHEN
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(_001__HappyCreateClientsCollectionChange.class, Collections.singletonList(MongoDatabase.class)),
                    new Trio<>(_002__HappyInsertClientsChange.class, Collections.singletonList(MongoDatabase.class))
            ));


            TargetSystem mongoDBTargetSystem = new MongoDBSpringDataTargetSystem("mongodb-ts", mongoTemplate).addDependency(testDatabase);

            flamingockBuilder
                    .addTargetSystem(mongoDBTargetSystem)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            // check clients changes
            mongoDBTestHelper.checkCount(testDatabase.getCollection(CLIENTS_COLLECTION), 1);
            // check ongoing status
            mongoDBTestHelper.checkOngoingTask(ongoingCount -> ongoingCount == 0);
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
                        .addTask("create-clients-collection", _001__UnhappyCreateClientsCollectionChange.class.getName(), "apply", false)
                        .addTask("insert-clients", _002__UnhappyInsertClientsChange.class.getName(), "apply", true)
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
                            new AuditRequestExpectation(executionId, "create-clients-collection", APPLIED),
                            new AuditRequestExpectation(executionId, "insert-clients", FAILED),
                            new AuditRequestExpectation(executionId, "insert-clients", ROLLED_BACK)
                    ).start();

            //WHEN
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(_001__UnhappyCreateClientsCollectionChange.class, Collections.singletonList(MongoDatabase.class)),
                    new Trio<>(_002__UnhappyInsertClientsChange.class, Collections.singletonList(MongoDatabase.class))
            ));

            TargetSystem mongoDBTargetSystem = new MongoDBSpringDataTargetSystem("mongodb-ts", mongoTemplate).addDependency(testDatabase);

            Runner runner = flamingockBuilder
                    .addTargetSystem(mongoDBTargetSystem)
                    .build();

            //THEN
            mockRunnerServer.verifyAllCalls();

            PipelineExecutionException ex = Assertions.assertThrows(PipelineExecutionException.class, runner::run);

            // check clients changes
            mongoDBTestHelper.checkCount(testDatabase.getCollection(CLIENTS_COLLECTION), 0);

            //TODO when cloud enabled
            // check ongoing status
//            mongoDBTestHelper.checkEmptyTargetSystemAudiMarker();
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
                        .addTask("create-clients-collection", _001__HappyCreateClientsCollectionChange.class.getName(), "apply", false)
                        .addTask("insert-clients", _002__HappyInsertClientsChange.class.getName(), "apply", true)
        );

        //GIVEN
        try (
                MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)

        ) {
            MongoDBSpringDataTargetSystem mongoTargetSystem = new MongoDBSpringDataTargetSystem("mongodb-ts", mongoTemplate).addDependency(testDatabase);

            mongoDBTestHelper.insertOngoingExecution("insert-clients");
            mockRunnerServer
                    .withClientSubmissionBase(prototypeClientSubmission)
                    .withExecutionPlanRequestsExpectation(
                            new ExecutionPlanRequestResponseMock(executionId, new MockRequestResponseTask("insert-clients", TargetSystemAuditMarkType.APPLIED)),
                            new ExecutionContinueRequestResponseMock()
                    ).withAuditRequestsExpectation(
                            new AuditRequestExpectation(executionId, "create-clients-collection", APPLIED),
                            new AuditRequestExpectation(executionId, "insert-clients", APPLIED)
                    ).start();


            //WHEN
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    "stage-1",
                    new Trio<>(_001__HappyCreateClientsCollectionChange.class, Collections.singletonList(MongoDatabase.class)),
                    new Trio<>(_002__HappyInsertClientsChange.class, Collections.singletonList(MongoDatabase.class))
            ));
            TargetSystem mongoDBTargetSystem = new MongoDBSpringDataTargetSystem("mongodb-ts", mongoTemplate).addDependency(testDatabase);

            flamingockBuilder
                    .addTargetSystem(mongoDBTargetSystem)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            // check clients changes
            mongoDBTestHelper.checkCount(testDatabase.getCollection(CLIENTS_COLLECTION), 1);
            // check ongoing status
            mongoDBTestHelper.checkOngoingTask(ongoingCount -> ongoingCount == 0);
        }
    }
}
