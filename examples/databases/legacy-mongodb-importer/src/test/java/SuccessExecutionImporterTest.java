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

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.flamingock.importer.cloud.mongodb.MongoDBLegacyImporter;
import io.flamingock.importer.cloud.mongodb.MongockLegacyImporterChangeUnit;
import io.flamingock.common.test.cloud.AuditEntryExpectation;
import io.flamingock.common.test.cloud.MockRunnerServer;
import io.flamingock.common.test.cloud.MongockLegacyAuditEntry;
import io.flamingock.core.cloud.api.audit.AuditEntryRequest;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.configurator.standalone.StandaloneCloudBuilder;
import io.flamingock.core.runner.PipelineExecutionException;
import io.flamingock.core.runner.Runner;
import io.flamingock.examples.community.CommunityStandaloneMongodbSyncApp;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;

import static io.flamingock.oss.driver.common.mongodb.MongoDBDriverConfiguration.DEFAULT_MIGRATION_REPOSITORY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class SuccessExecutionImporterTest {
    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));

    private static MongoClient mongoClient;

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
    private final static String DATABASE_NAME = "test";
    private MockRunnerServer mockRunnerServer;
    private StandaloneCloudBuilder flamingockBuilder;

    private static final List<AuditEntryExpectation> auditEntryExpectations = new LinkedList<>();

    private static final List<MongockLegacyAuditEntry> legacyAuditEntryExpectations = new LinkedList<>();

    @BeforeAll
    static void beforeAll() {
        mongoClient = MongoClients.create(MongoClientSettings
                .builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .build());
        new CommunityStandaloneMongodbSyncApp().run(mongoClient, DATABASE_NAME);

        auditEntryExpectations.add(new

                AuditEntryExpectation(
                "mongock-legacy-importer-mongodb-v1",
                AuditEntryRequest.Status.EXECUTED,
                MongockLegacyImporterChangeUnit.class.getName(),
                "execution"
        ));
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
                .addSystemModule(new MongoDBLegacyImporter(DEFAULT_MIGRATION_REPOSITORY_NAME))
                .addDependency(mongoClient.getDatabase(DATABASE_NAME))
                .setEnvironment(environmentName);
    }

    @AfterEach
    void afterEach() {
        //tear down
        mockRunnerServer.stop();
    }

    @Test
    @DisplayName("SHOULD import the Flamingock legacy history")
    void flamingockLegacyAuditImporterOkTest() {
        ArrayList<Document> flamingockDocuments = mongoClient.getDatabase(DATABASE_NAME)
                .getCollection(DEFAULT_MIGRATION_REPOSITORY_NAME)
                .find()
                .into(new ArrayList<>());

        Document aCreateCollection = flamingockDocuments.get(0);
        assertEquals("create-collection", aCreateCollection.get("changeId"));
        assertEquals("EXECUTED", aCreateCollection.get("state"));
        assertEquals("io.flamingock.examples.community.changes.ACreateCollection", aCreateCollection.get("changeLogClass"));

        Document bInsertDocument = flamingockDocuments.get(1);
        assertEquals("insert-document", bInsertDocument.get("changeId"));
        assertEquals("EXECUTED", bInsertDocument.get("state"));
        assertEquals("io.flamingock.examples.community.changes.BInsertDocument", bInsertDocument.get("changeLogClass"));

        Document cInsertAnotherDocument = flamingockDocuments.get(2);
        assertEquals("insert-another-document", cInsertAnotherDocument.get("changeId"));
        assertEquals("EXECUTED", cInsertAnotherDocument.get("state"));
        assertEquals("io.flamingock.examples.community.changes.CInsertAnotherDocument", cInsertAnotherDocument.get("changeLogClass"));

        assertEquals(3, flamingockDocuments.size());

        flamingockDocuments.forEach(m -> legacyAuditEntryExpectations.add(new MongockLegacyAuditEntry(
                m.get("_id"),
                m.getString("executionId"),
                m.getString("changeId"),
                m.getString("state"),
                m.getString("type"),
                m.getString("author"),
                m.get("timestamp"),
                m.getString("changeLogClass"),
                m.getString("changeSetMethod"),
                m.get("metadata"),
                m.getLong("executionMillis"),
                m.getString("executionHostname"),
                m.getString("errorTrace"),
                m.getBoolean("systemChange")
        )));

        String executionId = "execution-1";
        mockRunnerServer
                .addSimpleStageExecutionPlan(executionId, "mongodb-legacy-importer", auditEntryExpectations)
                .addSuccessfulImporterCall(legacyAuditEntryExpectations)
                .addExecutionWithAllTasksRequestResponse(executionId)
                .addExecutionContinueRequestResponse();

        mockRunnerServer.start();

        //WHEN
        //THEN
        Runner runner = flamingockBuilder
                .build();

        runner.execute();
    }

    @Test
    @DisplayName("SHOULD not import the Flamingock legacy history")
    void flamingockLegacyAuditImporterFailureTest() {
        ArrayList<Document> flamingockDocuments = mongoClient.getDatabase(DATABASE_NAME)
                .getCollection(DEFAULT_MIGRATION_REPOSITORY_NAME)
                .find()
                .into(new ArrayList<>());

        Document aCreateCollection = flamingockDocuments.get(0);
        assertEquals("create-collection", aCreateCollection.get("changeId"));
        assertEquals("EXECUTED", aCreateCollection.get("state"));
        assertEquals("io.flamingock.examples.community.changes.ACreateCollection", aCreateCollection.get("changeLogClass"));

        Document bInsertDocument = flamingockDocuments.get(1);
        assertEquals("insert-document", bInsertDocument.get("changeId"));
        assertEquals("EXECUTED", bInsertDocument.get("state"));
        assertEquals("io.flamingock.examples.community.changes.BInsertDocument", bInsertDocument.get("changeLogClass"));

        Document cInsertAnotherDocument = flamingockDocuments.get(2);
        assertEquals("insert-another-document", cInsertAnotherDocument.get("changeId"));
        assertEquals("EXECUTED", cInsertAnotherDocument.get("state"));
        assertEquals("io.flamingock.examples.community.changes.CInsertAnotherDocument", cInsertAnotherDocument.get("changeLogClass"));

        assertEquals(3, flamingockDocuments.size());

        flamingockDocuments.forEach(m -> legacyAuditEntryExpectations.add(new MongockLegacyAuditEntry(
                m.get("_id"),
                m.getString("executionId"),
                m.getString("changeId"),
                m.getString("state"),
                m.getString("type"),
                m.getString("author"),
                m.get("timestamp"),
                m.getString("changeLogClass"),
                m.getString("changeSetMethod"),
                m.get("metadata"),
                m.getLong("executionMillis"),
                m.getString("executionHostname"),
                m.getString("errorTrace"),
                m.getBoolean("systemChange")
        )));

        String executionId = "execution-1";
        mockRunnerServer
                .addSimpleStageExecutionPlan(executionId, "mongodb-legacy-importer", auditEntryExpectations)
                .addFailureImporterCall(Collections.emptyList())
                .addExecutionWithAllTasksRequestResponse(executionId)
                .addExecutionContinueRequestResponse();

        mockRunnerServer.start();

        //WHEN
        //THEN
        Runner runner = flamingockBuilder
                .build();

        Exception exception = Assertions.assertThrows(PipelineExecutionException.class, runner::execute);
    }
}
