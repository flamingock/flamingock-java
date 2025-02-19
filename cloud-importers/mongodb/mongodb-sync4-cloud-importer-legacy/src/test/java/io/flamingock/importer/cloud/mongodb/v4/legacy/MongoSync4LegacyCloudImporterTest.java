/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.importer.cloud.mongodb.v4.legacy;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.common.test.cloud.AuditRequestExpectation;
import io.flamingock.common.test.cloud.MockRunnerServer;
import io.flamingock.common.test.cloud.execution.ExecutionContinueRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionPlanRequestResponseMock;
import io.flamingock.common.test.cloud.prototype.PrototypeClientSubmission;
import io.flamingock.common.test.cloud.prototype.PrototypeStage;
import io.flamingock.commons.utils.TimeUtil;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.configurator.standalone.StandaloneCloudBuilder;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.importer.cloud.common.ImporterChangeUnit;
import io.flamingock.importer.cloud.common.MongockLegacyAuditEntry;
import io.flamingock.importer.cloud.mongodb.v4.legacy.changes.ACreateCollection;
import io.flamingock.importer.cloud.mongodb.v4.legacy.mongock.ClientInitializerChangeUnit;
import io.mongock.runner.standalone.MongockStandalone;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;
import java.util.stream.Collectors;

import static io.flamingock.core.cloud.api.audit.AuditEntryRequest.Status.EXECUTED;
import static io.flamingock.core.local.AuditEntryField.KEY_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;


@Testcontainers
class MongoSync4LegacyCloudImporterTest {

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));

    private static final String DB_NAME = "test";
    private static final String MONGOCK_CLIENTS_COLLECTION = "mongockClientCollection";
    private static final String FLAMINGOCK_CLIENTS_COLLECTION = "flamingockClientCollection";

    private static final String apiToken = "FAKE_API_TOKEN";
    private static final String organisationId = UUID.randomUUID().toString();
    private static final String organisationName = "MyOrganisation";
    private static final String projectId = UUID.randomUUID().toString();
    private static final String projectName = "MyOrganisation";
    private static final String serviceName = "clients-service";
    private static final String environmentName = "development";
    private static final String serviceId = "clients-service-id";
    private static final String environmentId = "development-env-id";
    private static final String credentialId = UUID.randomUUID().toString();
    private static final String jwt = "fake_jwt";
    private static final int runnerServerPort = 8888;

    private static MongoClient mongoClient;
    private static MongoDatabase testDatabase;

    private static MockRunnerServer mockRunnerServer;
    private static StandaloneCloudBuilder flamingockBuilder;

    private final Logger logger = LoggerFactory.getLogger(MongoSync4LegacyCloudImporterTest.class);

    @BeforeAll
    static void beforeAll() {
        mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        testDatabase = mongoClient.getDatabase(DB_NAME);
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
    void afterEach() {
        //tear down
        mockRunnerServer.stop();

        testDatabase.getCollection(MONGOCK_CLIENTS_COLLECTION).drop();
        testDatabase.getCollection(FLAMINGOCK_CLIENTS_COLLECTION).drop();
    }

    @Test
    @DisplayName("When standalone runs cloud with mongock importer should run migration")
    void shouldRunMongockImporter() {
        //Create a previous Mongock records
        io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver mongockMongoSync4Driver = io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver
                .withDefaultLock(mongoClient, DB_NAME);
        MongockStandalone.builder()
                .setDriver(mongockMongoSync4Driver)
                .addMigrationClass(ClientInitializerChangeUnit.class)
                .setTrackIgnored(true)
                .setTransactional(false)
                .buildRunner()
                .execute();

        List<Document> mongockDbState = testDatabase.getCollection(mongockMongoSync4Driver.getMigrationRepositoryName())
                .find()
                .into(new ArrayList<>())
                .stream()
                .sorted(Comparator.comparing(d -> TimeUtil.toLocalDateTime(d.get(KEY_TIMESTAMP))))
                .collect(Collectors.toList());

        //Check if Mongock works properly
        Assertions.assertEquals(4, mongockDbState.size());
        assertEquals("system-change-00001_before", mongockDbState.get(0).get("changeId"));
        assertEquals("system-change-00001", mongockDbState.get(1).get("changeId"));
        assertEquals("client-initializer_before", mongockDbState.get(2).get("changeId"));
        assertEquals("client-initializer", mongockDbState.get(3).get("changeId"));

        //Prepare expectations for Mocked Server
        List<AuditEntry> importExpectations = mongockDbState
                .stream()
                .map(MongoDBLegacyAuditReader::toMongockLegacyAuditEntry)
                .map(MongockLegacyAuditEntry::toAuditEntry)
                .collect(Collectors.toList());

        MongoDBLegacyImporter mongoDBLegacyImporter = new MongoDBLegacyImporter(mongoClient, DB_NAME);

        //Run Mocked Server
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage("importer", 0)
                        .addTask("importer-v1", ImporterChangeUnit.class.getName(), "execution", true),
                new PrototypeStage(stageName, 1)
                        .addTask("create-collection", ACreateCollection.class.getName(), "execution", false)
        );

        mockRunnerServer
                .withClientSubmissionBase(prototypeClientSubmission)
                .withExecutionPlanRequestsExpectation(
                        new ExecutionPlanRequestResponseMock(executionId),
                        new ExecutionContinueRequestResponseMock()
                ).withAuditRequestsExpectation(
                        new AuditRequestExpectation(executionId, "importer-v1", EXECUTED),
                        new AuditRequestExpectation(executionId, "create-collection", EXECUTED)
                ).addSuccessfulImporterCall(importExpectations)
                .start();

        //Finally run Flamingock changes with Cloud Importer
        flamingockBuilder
                .addSystemModule(mongoDBLegacyImporter)
                .addStage(new Stage(stageName).addCodePackage("io.flamingock.importer.cloud.mongodb.v4.legacy.changes"))
                .addDependency(testDatabase)
                .setTrackIgnored(true)
                .build()
                .execute();
    }
}