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

package io.flamingock.cloud.transaction.mongodb.sync.v4;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.cloud.transaction.mongodb.sync.v4.changes.happypath.HappyCreateClientsCollectionChange;
import io.flamingock.cloud.transaction.mongodb.sync.v4.changes.happypath.HappyInsertClientsChange;
import io.flamingock.cloud.transaction.mongodb.sync.v4.changes.unhappypath.UnhappyCreateClientsCollectionChange;
import io.flamingock.cloud.transaction.mongodb.sync.v4.changes.unhappypath.UnhappyInsertClientsChange;
import io.flamingock.common.test.cloud.deprecated.AuditEntryMatcher;
import io.flamingock.common.test.cloud.deprecated.MockRunnerServerOld;
import io.flamingock.core.cloud.api.vo.OngoingStatus;
import io.flamingock.core.cloud.transaction.TaskWithOngoingStatus;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.configurator.standalone.StandaloneCloudBuilder;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.runner.PipelineExecutionException;
import io.flamingock.core.runner.Runner;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static io.flamingock.core.cloud.api.audit.AuditEntryRequest.Status.*;

@Testcontainers
@Disabled
public class MongoSync4CloudTransactionerTest {

    private static final Logger logger = LoggerFactory.getLogger(MongoSync4CloudTransactionerTest.class);

    private static final String DB_NAME = "test";
    private static final String CLIENTS_COLLECTION = "clientCollection";

    private static MongoClient mongoClient;
    private static MongoDatabase testDatabase;
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

    private MockRunnerServerOld mockRunnerServer;
    private StandaloneCloudBuilder flamingockBuilder;

    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));

    @BeforeAll
    static void beforeAll() {
        mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        testDatabase = mongoClient.getDatabase(DB_NAME);
        mongoDBTestHelper = new MongoDBTestHelper(testDatabase);
    }

    @BeforeEach
    void beforeEach() throws Exception {
        mockRunnerServer = new MockRunnerServerOld()
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
    void afterEach() throws Exception {
        //tear down
        mockRunnerServer.stop();

        testDatabase.getCollection(CLIENTS_COLLECTION).drop();
    }

    @Test
    @DisplayName("Should follow the transactioner lifecycle")
    void happyPath() {
        List<AuditEntryMatcher> auditEntryExpectations = new LinkedList<>();
        auditEntryExpectations.add(new
                AuditEntryMatcher(
                "create-clients-collection",
                EXECUTED,
                HappyCreateClientsCollectionChange.class.getName(),
                "execution",
                false
        ));
        auditEntryExpectations.add(new
                AuditEntryMatcher(
                "insert-clients",
                EXECUTED,
                HappyInsertClientsChange.class.getName(),
                "execution"
        ));

        //GIVEN
        try (
                MongoSync4CloudTransactioner transactioner = new MongoSync4CloudTransactioner(mongoClient, DB_NAME)
        ) {
            String executionId = "execution-1";
            String stageName = "stage-1";
            mockRunnerServer
                    .addSimpleStageExecutionPlan(executionId, stageName, auditEntryExpectations)
                    .addExecutionWithAllTasksRequestResponse(executionId)
                    .addExecutionContinueRequestResponse()
                    .start();

            MongoSync4CloudTransactioner mongoSync4CloudTransactioner = Mockito.spy(transactioner);

            //WHEN
            flamingockBuilder
                    .setCloudTransactioner(mongoSync4CloudTransactioner)
                    .addStage(new Stage(stageName)
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.mongodb.sync.v4.changes.happypath")))
                    .addDependency(testDatabase)
                    .build()
                    .execute();

            // check clients changes
            mongoDBTestHelper.checkCount(testDatabase.getCollection(CLIENTS_COLLECTION), 1);
            // check ongoing status
            mongoDBTestHelper.checkOngoingTask(ongoingCount -> ongoingCount == 0);
        }
    }

    @Test
    @DisplayName("Should rollback the ongoing deletion when a task fails")
    void failedTasks() {
        List<AuditEntryMatcher> auditEntryExpectations = new LinkedList<>();
        auditEntryExpectations.add(new
                AuditEntryMatcher(
                "create-clients-collection",
                EXECUTED,
                UnhappyCreateClientsCollectionChange.class.getName(),
                "execution",
                false
        ));

        auditEntryExpectations.add(new
                AuditEntryMatcher(
                "insert-clients",
                EXECUTION_FAILED,
                UnhappyInsertClientsChange.class.getName(),
                "execution"
        ));

        auditEntryExpectations.add(new
                AuditEntryMatcher(
                "insert-clients",
                ROLLED_BACK,
                UnhappyInsertClientsChange.class.getName(),
                "native_db_engine"
        ));

        //GIVEN
        try (
                MongoSync4CloudTransactioner transactioner = new MongoSync4CloudTransactioner(mongoClient, DB_NAME)
        ) {
            String executionId = "execution-1";
            String stageName = "stage-1";
            mockRunnerServer
                    .addSimpleStageExecutionPlan(executionId, stageName, auditEntryExpectations)
                    .addExecutionWithAllTasksRequestResponse(executionId)
                    .addExecutionContinueRequestResponse()
                    .start();

            MongoSync4CloudTransactioner mongoSync4CloudTransactioner = Mockito.spy(transactioner);

            //WHEN
            Runner runner = flamingockBuilder
                    .setCloudTransactioner(mongoSync4CloudTransactioner)
                    .addStage(new Stage(stageName)
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.mongodb.sync.v4.changes.unhappypath")))
                    .addDependency(testDatabase)
                    .build();
            PipelineExecutionException ex = Assertions.assertThrows(PipelineExecutionException.class, runner::run);

            // check clients changes
            mongoDBTestHelper.checkCount(testDatabase.getCollection(CLIENTS_COLLECTION), 0);
            // check ongoing status
            mongoDBTestHelper.checkAtLeastOneOngoingTask();
        }
    }


    //TODO verify the server is called with the right parameters. among other, it sends the ongoing status
    @Test
    @DisplayName("Should send ongoing task in execution when is present in local database")
    void shouldSendOngoingTaskInExecutionPlan() {
        List<AuditEntryMatcher> auditEntryExpectations = new LinkedList<>();
        auditEntryExpectations.add(new
                AuditEntryMatcher(
                "create-clients-collection",
                EXECUTED,
                UnhappyCreateClientsCollectionChange.class.getName(),
                "execution",
                false
        ));

        auditEntryExpectations.add(new
                AuditEntryMatcher(
                "insert-clients",
                EXECUTION_FAILED,
                UnhappyInsertClientsChange.class.getName(),
                "execution"
        ));

        auditEntryExpectations.add(new
                AuditEntryMatcher(
                "insert-clients",
                ROLLED_BACK,
                UnhappyInsertClientsChange.class.getName(),
                "native_db_engine"
        ));
        String executionId = "execution-1";
        String stageName = "stage-1";

        //GIVEN
        try (
                MongoSync4CloudTransactioner transactioner = new MongoSync4CloudTransactioner(mongoClient, DB_NAME)
        ) {
            mongoDBTestHelper.insertOngoingExecution("failed-insert-clients");
            List<TaskWithOngoingStatus> ongoingStatuses = Collections.singletonList(new TaskWithOngoingStatus("failed-insert-clients", OngoingStatus.EXECUTION));
            mockRunnerServer
                    .addSimpleStageExecutionPlan(executionId, stageName, auditEntryExpectations, ongoingStatuses)
                    .addExecutionWithAllTasksRequestResponse(executionId)
                    .addExecutionContinueRequestResponse()
                    .start();

            MongoSync4CloudTransactioner mongoSync4CloudTransactioner = Mockito.spy(transactioner);

            //WHEN
            Runner runner = flamingockBuilder
                    .setCloudTransactioner(mongoSync4CloudTransactioner)
                    .addStage(new Stage(stageName)
                            .setCodePackages(Collections.singletonList("io.flamingock.cloud.transaction.mongodb.sync.v4.changes.unhappypath")))
                    .addDependency(testDatabase)
                    .build();

            //then
            PipelineExecutionException ex = Assertions.assertThrows(PipelineExecutionException.class, runner::run);
        }
    }
}
