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
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.oss.driver.dynamodb.driver.DynamoDBDriver;
import io.flamingock.oss.driver.dynamodb.internal.entities.AuditEntryEntity;
import io.flamingock.oss.driver.dynamodb.internal.mongock.ChangeEntryDynamoDB;
import io.flamingock.oss.driver.dynamodb.mongock.ClientInitializerChangeUnit;
import io.mongock.runner.standalone.MongockStandalone;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.core.configurator.core.CoreConfiguration.MongockImporterConfiguration;
import static io.flamingock.oss.driver.dynamodb.internal.util.DynamoDBConstants.AUDIT_LOG_TABLE_NAME;
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
                .addMigrationClass(ClientInitializerChangeUnit.class)
                .addDependency(client)
                .setTrackIgnored(true)
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
        FlamingockStandalone.local()
                .setMongockImporterConfiguration(MongockImporterConfiguration.withSource(mongockDriver.getMigrationRepositoryName()))
                .setDriver(new DynamoDBDriver(client))
                .addStage(new Stage("stage-name")
                        .addCodePackage("io.flamingock.oss.driver.dynamodb.changes.happyPathWithTransaction"))
                .addDependency(client)
                .setTrackIgnored(true)
                .setTransactionEnabled(true)
                .build()
                .run();


        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(AUDIT_LOG_TABLE_NAME);
        assertEquals(8, auditLog.size());
        checkAuditEntry(
                auditLog.get(0),
                mongockDbState.get(0).getExecutionId(),
                null,
                mongockDbState.get(0).getChangeId(),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(0).getChangeLogClass(),
                mongockDbState.get(0).getChangeSetMethod(),
                AuditEntry.ExecutionType.BEFORE_EXECUTION,
                true);
        checkAuditEntry(
                auditLog.get(1),
                mongockDbState.get(1).getExecutionId(),
                null,
                mongockDbState.get(1).getChangeId(),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(1).getChangeLogClass(),
                mongockDbState.get(1).getChangeSetMethod(),
                AuditEntry.ExecutionType.EXECUTION,
                true);

        checkAuditEntry(
                auditLog.get(2),
                mongockDbState.get(2).getExecutionId(),
                null,
                mongockDbState.get(2).getChangeId(),
                AuditEntry.Status.EXECUTED,
                mongockDbState.get(2).getChangeLogClass(),
                mongockDbState.get(2).getChangeSetMethod(),
                AuditEntry.ExecutionType.BEFORE_EXECUTION,
                false);

        checkAuditEntry(
                auditLog.get(3),
                mongockDbState.get(3).getExecutionId(),
                null,
                mongockDbState.get(3).getChangeId(),
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
                "io.flamingock.oss.driver.dynamodb.internal.mongock.MongockLocalLegacyImporterChangeUnit",
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