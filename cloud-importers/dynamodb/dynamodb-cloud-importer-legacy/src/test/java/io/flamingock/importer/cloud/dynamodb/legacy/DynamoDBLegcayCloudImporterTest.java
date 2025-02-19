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

package io.flamingock.importer.cloud.dynamodb.legacy;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import io.flamingock.common.test.cloud.AuditRequestExpectation;
import io.flamingock.common.test.cloud.MockRunnerServer;
import io.flamingock.common.test.cloud.execution.ExecutionContinueRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionPlanRequestResponseMock;
import io.flamingock.common.test.cloud.prototype.PrototypeClientSubmission;
import io.flamingock.common.test.cloud.prototype.PrototypeStage;
import io.flamingock.commons.utils.DynamoDBUtil;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.configurator.standalone.StandaloneCloudBuilder;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.importer.cloud.common.ImporterChangeUnit;
import io.flamingock.importer.cloud.common.MongockLegacyAuditEntry;
import io.flamingock.importer.cloud.dynamodb.legacy.changes.ACreateCollection;
import io.flamingock.importer.cloud.dynamodb.legacy.entities.ChangeEntryEntity;
import io.flamingock.importer.cloud.dynamodb.legacy.mongock.ClientInitializerChangeUnit;
import io.mongock.runner.standalone.MongockStandalone;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static io.flamingock.core.cloud.api.audit.AuditEntryRequest.Status.EXECUTED;
import static org.junit.jupiter.api.Assertions.assertEquals;


class DynamoDBLegcayCloudImporterTest {

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

    private static DynamoDBProxyServer dynamoDBLocal;
    private static DynamoDbClient client;
    private static AmazonDynamoDBClient amazonClient;
    private static DynamoDBUtil dynamoDBUtil;

    private static MockRunnerServer mockRunnerServer;
    private static StandaloneCloudBuilder flamingockBuilder;

    private final Logger logger = LoggerFactory.getLogger(DynamoDBLegcayCloudImporterTest.class);

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

    private static AmazonDynamoDBClient getAmazonDynamoDBClient() {
        return (AmazonDynamoDBClient) AmazonDynamoDBClientBuilder
                .standard()
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(
                                "http://localhost:8000", Region.EU_WEST_1.toString()
                        )
                )
                .withCredentials(
                        new AWSStaticCredentialsProvider(
                                new BasicAWSCredentials("dummye", "dummye")
                        )
                )
                .build();
    }

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

        dynamoDBUtil = new DynamoDBUtil(client);

        amazonClient = getAmazonDynamoDBClient();

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
    void afterEach() throws Exception {
        //tear down
        mockRunnerServer.stop();

        if (dynamoDBLocal != null) {
            dynamoDBLocal.stop();
        }
    }

    @Test
    @DisplayName("When standalone runs cloud with mongock importer should run migration")
    void shouldRunMongockImporter() {
        //Create a previous Mongock records
        io.mongock.driver.dynamodb.driver.DynamoDBDriver mongockDynamoDBDriver = io.mongock.driver.dynamodb.driver.DynamoDBDriver.withDefaultLock(amazonClient);
        MongockStandalone.builder()
                .setDriver(mongockDynamoDBDriver)
                .addMigrationClass(ClientInitializerChangeUnit.class)
                .addDependency(client)
                .setTrackIgnored(true)
                .setTransactional(false)
                .buildRunner()
                .execute();

        List<ChangeEntryEntity> mongockDbState = dynamoDBUtil.getEnhancedClient().table(mongockDynamoDBDriver.getMigrationRepositoryName(), TableSchema.fromBean(ChangeEntryEntity.class))
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .sorted(Comparator.comparing(ChangeEntryEntity::getTimestamp))
                .collect(Collectors.toList());

        //Check if Mongock works properly
        Assertions.assertEquals(4, mongockDbState.size());
        assertEquals("system-change-00001_before", mongockDbState.get(0).getChangeId());
        assertEquals("system-change-00001", mongockDbState.get(1).getChangeId());
        assertEquals("client-initializer_before", mongockDbState.get(2).getChangeId());
        assertEquals("client-initializer", mongockDbState.get(3).getChangeId());

        //Prepare expectations for Mocked Server
        List<AuditEntry> importExpectations = mongockDbState
                .stream()
                .map(DynamoDBLegacyAuditReader::toMongockLegacyAuditEntry)
                .map(MongockLegacyAuditEntry::toAuditEntry)
                .collect(Collectors.toList());

        DynamoDBLegacyImporter dynamoDBLegacyImporter = new DynamoDBLegacyImporter(dynamoDBUtil.getEnhancedClient());

        //Run Mocked Server
        String executionId = "execution-1";
        String stageName = "stage-1";

        PrototypeClientSubmission prototypeClientSubmission = new PrototypeClientSubmission(
                new PrototypeStage("importer", 0)
                        .addTask("importer-v1", ImporterChangeUnit.class.getName(), "execution", true),
                new PrototypeStage(stageName, 1)
                        .addTask("create-table", ACreateCollection.class.getName(), "execution", false)
        );

        mockRunnerServer
                .withClientSubmissionBase(prototypeClientSubmission)
                .withExecutionPlanRequestsExpectation(
                        new ExecutionPlanRequestResponseMock(executionId),
                        new ExecutionContinueRequestResponseMock()
                ).withAuditRequestsExpectation(
                        new AuditRequestExpectation(executionId, "importer-v1", EXECUTED),
                        new AuditRequestExpectation(executionId, "create-table", EXECUTED)
                ).addSuccessfulImporterCall(importExpectations)
                .start();

        //Finally run Flamingock changes with Cloud Importer
        flamingockBuilder
                .addSystemModule(dynamoDBLegacyImporter)
                .addStage(new Stage(stageName).addCodePackage("io.flamingock.importer.cloud.dynamodb.legacy.changes"))
                .addDependency(client)
                .setTrackIgnored(true)
                .build()
                .execute();
    }
}