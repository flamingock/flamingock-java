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

package io.flamingock.oss.driver.dynamodb;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import io.flamingock.commons.utils.Trio;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.engine.audit.writer.AuditEntry;
import io.flamingock.internal.core.legacy.MongockLegacyIdGenerator;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.oss.driver.dynamodb.changes._0_mongock_create_authors_collection;
import io.flamingock.oss.driver.dynamodb.changes._1_create_client_collection_happy;
import io.flamingock.oss.driver.dynamodb.changes._2_insert_federico_happy_non_transactional;
import io.flamingock.oss.driver.dynamodb.changes._3_insert_jorge_happy_non_transactional;
import io.flamingock.oss.driver.dynamodb.internal.mongock.ChangeEntryDynamoDB;
import io.flamingock.oss.driver.dynamodb.internal.mongock.MongockImporterChangeUnit;
import io.mongock.runner.standalone.MongockStandalone;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static io.flamingock.internal.core.builder.core.CoreConfiguration.ImporterConfiguration;
import static io.flamingock.commons.utils.DynamoDBConstants.AUDIT_LOG_TABLE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;


class DynamoDBImporterTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBImporterTest.class);

    private static DynamoDBProxyServer dynamoDBLocal;
    private static DynamoDbClient client;
    private static AmazonDynamoDBClient amazonClient;

    private static DynamoDBTestHelper dynamoDBTestHelper;

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

        client = DynamoDbClient.builder()
                .region(Region.EU_WEST_1)
                .endpointOverride(new URI("http://localhost:8000"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("dummye", "dummye")
                        )
                )
                .build();
        amazonClient = (AmazonDynamoDBClient) AmazonDynamoDBClientBuilder
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

        dynamoDBTestHelper = new DynamoDBTestHelper(client);
    }

    @AfterEach
    void AfterEach() throws Exception {
        logger.info("Stopping DynamoDB Local...");
        dynamoDBLocal.stop();
    }

    @Test
    @DisplayName("When standalone runs the driver with mongock importer should run migration")
    void shouldRunMongockImporter() {
        //Given
        io.mongock.driver.dynamodb.driver.DynamoDBDriver mongockDriver = io.mongock.driver.dynamodb.driver.DynamoDBDriver.withDefaultLock(amazonClient);
        MongockStandalone.builder()
                .setDriver(mongockDriver)
                .addMigrationClass(_0_mongock_create_authors_collection.class)
                .addDependency(client)
                .setTransactional(false)
                .buildRunner()
                .execute();

        List<ChangeEntryDynamoDB> mongockDbState = dynamoDBTestHelper.getChangeEntriesSorted(mongockDriver.getMigrationRepositoryName());

        Assertions.assertEquals(4, mongockDbState.size());
        assertEquals("system-change-00001_before", mongockDbState.get(0).getChangeId());
        assertEquals("system-change-00001", mongockDbState.get(1).getChangeId());
        assertEquals("client-initializer_before", mongockDbState.get(2).getChangeId());
        assertEquals("client-initializer", mongockDbState.get(3).getChangeId());

        //When

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(_0_mongock_create_authors_collection.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_1_create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_2_insert_federico_happy_non_transactional.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_3_insert_jorge_happy_non_transactional.class, Collections.singletonList(DynamoDbClient.class), Collections.singletonList(DynamoDbClient.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    .withImporter(ImporterConfiguration.withSource(mongockDriver.getMigrationRepositoryName()))
                    //.addStage(new Stage("stage-name")
//                        .addCodePackage("io.flamingock.oss.driver.dynamodb.changes.happyPathWithTransaction"))
                    .addDependency(client)
                    .build()
                    .run();
        }



        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(AUDIT_LOG_TABLE_NAME);
        assertEquals(8, auditLog.size());
        checkAuditEntry(
                auditLog.get(0),
                mongockDbState.get(0).getExecutionId(),
                null,
                MongockLegacyIdGenerator.getNewId( mongockDbState.get(0).getChangeId(), mongockDbState.get(0).getAuthor()),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(0).getChangeLogClass(),
                mongockDbState.get(0).getChangeSetMethod(),
                AuditEntry.ExecutionType.BEFORE_EXECUTION,
                true);
        checkAuditEntry(
                auditLog.get(1),
                mongockDbState.get(1).getExecutionId(),
                null,
                MongockLegacyIdGenerator.getNewId( mongockDbState.get(1).getChangeId(), mongockDbState.get(1).getAuthor()),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(1).getChangeLogClass(),
                mongockDbState.get(1).getChangeSetMethod(),
                AuditEntry.ExecutionType.EXECUTION,
                true);

        checkAuditEntry(
                auditLog.get(2),
                mongockDbState.get(2).getExecutionId(),
                null,
                MongockLegacyIdGenerator.getNewId( mongockDbState.get(2).getChangeId(), mongockDbState.get(2).getAuthor()),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(2).getChangeLogClass(),
                mongockDbState.get(2).getChangeSetMethod(),
                AuditEntry.ExecutionType.BEFORE_EXECUTION,
                false);

        checkAuditEntry(
                auditLog.get(3),
                mongockDbState.get(3).getExecutionId(),
                null,
                MongockLegacyIdGenerator.getNewId( mongockDbState.get(3).getChangeId(), mongockDbState.get(3).getAuthor()),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(3).getChangeLogClass(),
                mongockDbState.get(3).getChangeSetMethod(),
                AuditEntry.ExecutionType.EXECUTION,
                false);

        checkAuditEntry(
                auditLog.get(4),
                auditLog.get(4).getExecutionId(),
                "dynamodb-local-legacy-importer",
                "mongock-local-legacy-importer-dynamodb",
                AuditEntry.Status.EXECUTED,
                MongockImporterChangeUnit.class.getName(),
                "execution",
                AuditEntry.ExecutionType.EXECUTION,
                false);


    }

    private void checkAuditEntry(AuditEntry actualAuditEntry,
                                 String expectedExecutionId,
                                 String expectedStageId,
                                 String expectedTaskId,
                                 AuditEntry.Status expectedStatus,
                                 String expectedClassName,
                                 String expectedMethodName,
                                 AuditEntry.ExecutionType expectedExecutionType,
                                 boolean expectedSystemChange) {
        assertEquals(expectedExecutionId, actualAuditEntry.getExecutionId());
        assertEquals(expectedStageId, actualAuditEntry.getStageId());
        assertEquals(expectedTaskId, actualAuditEntry.getTaskId());
        assertEquals(expectedStatus, actualAuditEntry.getState());
        assertEquals(expectedClassName, actualAuditEntry.getClassName());
        assertEquals(expectedMethodName, actualAuditEntry.getMethodName());
        assertEquals(expectedExecutionType, actualAuditEntry.getType());
        assertEquals(expectedSystemChange, actualAuditEntry.getSystemChange());

    }

}