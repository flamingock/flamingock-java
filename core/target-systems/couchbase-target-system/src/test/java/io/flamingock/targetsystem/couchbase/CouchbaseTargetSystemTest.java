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
package io.flamingock.targetsystem.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import io.flamingock.api.targets.TargetSystem;
import io.flamingock.common.test.cloud.AuditRequestExpectation;
import io.flamingock.common.test.cloud.MockRunnerServer;
import io.flamingock.common.test.cloud.execution.ExecutionContinueRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionPlanRequestResponseMock;
import io.flamingock.common.test.cloud.mock.MockRequestResponseTask;
import io.flamingock.common.test.cloud.prototype.PrototypeClientSubmission;
import io.flamingock.common.test.cloud.prototype.PrototypeStage;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.core.builder.change.CloudChangeRunnerBuilder;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.internal.core.runner.Runner;
import io.flamingock.internal.util.Trio;
import io.flamingock.targetsystem.couchbase.changes.happypath.HappyCreateClientsCollectionChange;
import io.flamingock.targetsystem.couchbase.changes.happypath.HappyInsertClientsChange;
import io.flamingock.targetsystem.couchbase.changes.unhappypath.UnhappyCreateClientsCollectionChange;
import io.flamingock.targetsystem.couchbase.changes.unhappypath.UnhappyInsertClientsChange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static io.flamingock.internal.common.cloud.audit.AuditEntryRequest.Status.APPLIED;
import static io.flamingock.internal.common.cloud.audit.AuditEntryRequest.Status.FAILED;
import static io.flamingock.internal.common.cloud.audit.AuditEntryRequest.Status.ROLLED_BACK;

@Testcontainers
public class CouchbaseTargetSystemTest {

    private static final Logger logger = LoggerFactory.getLogger(CouchbaseTargetSystemTest.class);

    private static final String BUCKET_NAME = "test";
    private static final String SCOPE_NAME = CollectionIdentifier.DEFAULT_SCOPE;
    private static final String CLIENTS_COLLECTION = "clientCollection";

    private static Cluster cluster;
    private static Bucket bucket;
    private static CouchbaseTestHelper couchbaseTestHelper;

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
    public static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:7.2.4")
            .withBucket(new BucketDefinition(BUCKET_NAME));

    @BeforeAll
    static void beforeAll() {
        couchbaseContainer.start();
        cluster = Cluster.connect(
                couchbaseContainer.getConnectionString(),
                couchbaseContainer.getUsername(),
                couchbaseContainer.getPassword());

        bucket = cluster.bucket(BUCKET_NAME);
        bucket.waitUntilReady(Duration.ofSeconds(10));
        couchbaseTestHelper = new CouchbaseTestHelper(cluster, bucket, SCOPE_NAME, CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME);
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
        CouchbaseCollectionHelper.dropCollectionIfExists(cluster, BUCKET_NAME, SCOPE_NAME, CLIENTS_COLLECTION);
    }

    @Test
    @DisplayName("Should follow the targetSystem lifecycle")
    void happyPath() {
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage(stageName, 0)
                        .addTask("create-clients-collection", HappyCreateClientsCollectionChange.class.getName(), "execution", false)
                        .addTask("insert-clients", HappyInsertClientsChange.class.getName(), "execution", true)
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
                    new Trio<>(HappyCreateClientsCollectionChange.class, Collections.singletonList(Bucket.class)),
                    new Trio<>(HappyInsertClientsChange.class, Collections.singletonList(Bucket.class))
            ));


            TargetSystem couchbaseTargetSystem = new CouchbaseTargetSystem("couchbase-ts", cluster, BUCKET_NAME);
            flamingockBuilder
                    .addTargetSystem(couchbaseTargetSystem)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            // check clients changes
            couchbaseTestHelper.checkCount(bucket.scope(SCOPE_NAME).collection(CLIENTS_COLLECTION), 1);
            //TODO add when cloud added
            // check ongoing status
//            couchbaseTestHelper.checkOngoingTask(ongoingCount -> ongoingCount == 0);
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
                        .addTask("create-clients-collection", UnhappyCreateClientsCollectionChange.class.getName(), "execution", false)
                        .addTask("insert-clients", UnhappyInsertClientsChange.class.getName(), "execution", true)
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
                    new Trio<>(UnhappyCreateClientsCollectionChange.class, Collections.singletonList(Bucket.class)),
                    new Trio<>(UnhappyInsertClientsChange.class, Collections.singletonList(Bucket.class))
            ));

            TargetSystem couchbaseTargetSystem = new CouchbaseTargetSystem("couchbase-ts", cluster, BUCKET_NAME);

            Runner runner = flamingockBuilder
                    .addTargetSystem(couchbaseTargetSystem)
                    .build();

            //THEN
            mockRunnerServer.verifyAllCalls();

            PipelineExecutionException ex = Assertions.assertThrows(PipelineExecutionException.class, runner::run);

            // check clients changes
            couchbaseTestHelper.checkCount(bucket.scope(SCOPE_NAME).collection(CLIENTS_COLLECTION), 0);

            //TODO when cloud enabled
            // check ongoing status
//            couchbaseTestHelper.checkEmptyTargetSystemAuditMarker();
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
                        .addTask("create-clients-collection", HappyCreateClientsCollectionChange.class.getName(), "execution", false)
                        .addTask("insert-clients", HappyInsertClientsChange.class.getName(), "execution", true)
        );

        //GIVEN
        try (
                MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)
        ) {
            couchbaseTestHelper.insertOngoingExecution("insert-clients");
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
                    new Trio<>(HappyCreateClientsCollectionChange.class, Collections.singletonList(Bucket.class)),
                    new Trio<>(HappyInsertClientsChange.class, Collections.singletonList(Bucket.class))
            ));

            TargetSystem couchbaseTargetSystem = new CouchbaseTargetSystem("couchbase-ts", cluster, BUCKET_NAME);

            flamingockBuilder
                    .addTargetSystem(couchbaseTargetSystem)
                    .build()
                    .execute();

            //THEN
            mockRunnerServer.verifyAllCalls();

            // check clients changes
            couchbaseTestHelper.checkCount(bucket.scope(SCOPE_NAME).collection(CLIENTS_COLLECTION), 1);
            // check ongoing status
            couchbaseTestHelper.checkOngoingTask(ongoingCount -> ongoingCount == 0);
        }
    }
}
