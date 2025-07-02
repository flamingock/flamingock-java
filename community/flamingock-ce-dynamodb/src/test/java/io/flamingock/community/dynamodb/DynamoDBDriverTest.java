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

package io.flamingock.community.dynamodb;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import io.flamingock.community.dynamodb.changes._001_create_client_collection_happy;
import io.flamingock.community.dynamodb.changes._002_insert_federico_happy_non_transactional;
import io.flamingock.community.dynamodb.changes._002_insert_federico_happy_transactional;
import io.flamingock.community.dynamodb.changes._003_insert_jorge_failed_non_transactional_non_rollback;
import io.flamingock.community.dynamodb.changes._003_insert_jorge_failed_non_transactional_rollback;
import io.flamingock.community.dynamodb.changes._003_insert_jorge_failed_transactional_non_rollback;
import io.flamingock.community.dynamodb.changes._004_insert_jorge_happy_non_transactional;
import io.flamingock.community.dynamodb.changes._004_insert_jorge_happy_transactional;
import io.flamingock.community.dynamodb.changes.common.UserEntity;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.community.Constants;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.internal.util.Trio;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DynamoDBDriverTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBDriverTest.class);

    private static DynamoDBProxyServer dynamoDBLocal;
    private static DynamoDbClient client;

    private static final String CUSTOM_AUDIT_REPOSITORY_NAME = "testFlamingockAudit";
    private static final String CUSTOM_LOCK_REPOSITORY_NAME = "testFlamingockLock";

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
        dynamoDBTestHelper = new DynamoDBTestHelper(client);
    }

    @AfterEach
    void AfterEach() throws Exception {
        logger.info("Stopping DynamoDB Local...");
        dynamoDBLocal.stop();
    }


    @Test
    @DisplayName("When standalone runs the driver with DEFAULT repository names related tables should exists")
    void happyPathWithDefaultRepositoryNames() {
        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002_insert_federico_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)),
                    new Trio<>(_004_insert_jorge_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)))
            );


            FlamingockFactory.getCommunityBuilder()
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.happyPathWithTransaction"))
                    .addDependency(client)
                    .build()
                    .run();
        }


        //Then
        assertTrue(dynamoDBTestHelper.tableExists(Constants.DEFAULT_AUDIT_STORE_NAME));
        assertTrue(dynamoDBTestHelper.tableExists(Constants.DEFAULT_LOCK_STORE_NAME));

        assertFalse(dynamoDBTestHelper.tableExists(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertFalse(dynamoDBTestHelper.tableExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with CUSTOM config properties all properties are correctly set")
    void happyPathWithCustomConfigOptions() {
        //Given-When

        DynamoDBUtil dynamoDBUtil = new DynamoDBUtil(client);
        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions(Constants.AUDIT_LOG_PK, null),
                dynamoDBUtil.getKeySchemas(Constants.AUDIT_LOG_PK, null),
                dynamoDBUtil.getProvisionedThroughput(1L, 2L),
                CUSTOM_AUDIT_REPOSITORY_NAME,
                emptyList(),
                emptyList()
        );
        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions(Constants.LOCK_PK, null),
                dynamoDBUtil.getKeySchemas(Constants.LOCK_PK, null),
                dynamoDBUtil.getProvisionedThroughput(1L, 2L),
                CUSTOM_LOCK_REPOSITORY_NAME,
                emptyList(),
                emptyList()
        );

        DynamoDBConfiguration config = new DynamoDBConfiguration();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002_insert_federico_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)),
                    new Trio<>(_004_insert_jorge_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    .addDependency(config)
                    .setProperty("dynamodb.autoCreate", false)
                    .setProperty("dynamodb.auditRepositoryName", CUSTOM_AUDIT_REPOSITORY_NAME)
                    .setProperty("dynamodb.lockRepositoryName", CUSTOM_LOCK_REPOSITORY_NAME)
                    .setProperty("dynamodb.readCapacityUnits", 1L)
                    .setProperty("dynamodb.writeCapacityUnits", 2L)
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.happyPathWithTransaction"))
                    .addDependency(client)
                    .build()
                    .run();
        }

        assertFalse(config.isAutoCreate());
        assertEquals(CUSTOM_AUDIT_REPOSITORY_NAME, config.getAuditRepositoryName());
        assertEquals(CUSTOM_LOCK_REPOSITORY_NAME, config.getLockRepositoryName());
        assertEquals(1L, config.getReadCapacityUnits());
        assertEquals(2L, config.getWriteCapacityUnits());

        assertFalse(dynamoDBTestHelper.tableExists(Constants.DEFAULT_AUDIT_STORE_NAME));
        assertFalse(dynamoDBTestHelper.tableExists(Constants.DEFAULT_LOCK_STORE_NAME));

        assertTrue(dynamoDBTestHelper.tableExists(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertTrue(dynamoDBTestHelper.tableExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions enabled should persist the audit logs and the user's table updated")
    void happyPathWithTransaction() {
        //Given-When


        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002_insert_federico_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)),
                    new Trio<>(_004_insert_jorge_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.happyPathWithTransaction"))
                    .addDependency(client)
                    .build()
                    .run();
        }

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("table-create", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-user", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-another-user", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());

        //Checking user table
        List<String> rows = dynamoDBTestHelper.dynamoDBUtil.getEnhancedClient()
                .table("test_table", TableSchema.fromBean(UserEntity.class))
                .scan().items().stream()
                .map(UserEntity::getPartitionKey)
                .collect(Collectors.toList());

        assertEquals(2, rows.size());
        assertTrue(rows.contains("Pepe Pérez"));
        assertTrue(rows.contains("Pablo López"));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions disabled should persist the audit logs and the user's table updated")
    void happyPathWithoutTransaction() {
        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002_insert_federico_happy_non_transactional.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_004_insert_jorge_happy_non_transactional.class, Collections.singletonList(DynamoDbClient.class)))
            );
            FlamingockFactory.getCommunityBuilder()
                    //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.happyPathWithoutTransaction"))
                    .addDependency(client)
                    .disableTransaction()
                    .build()
                    .run();
        }


        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("table-create", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-user", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-another-user", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());

        //Checking user table
        List<String> rows = dynamoDBTestHelper.dynamoDBUtil.getEnhancedClient()
                .table("test_table", TableSchema.fromBean(UserEntity.class))
                .scan().items().stream()
                .map(UserEntity::getPartitionKey)
                .collect(Collectors.toList());

        assertEquals(2, rows.size());
        assertTrue(rows.contains("Pepe Pérez"));
        assertTrue(rows.contains("Pablo López"));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions enabled and execution fails should persist only the executed audit logs")
    void failedWithTransaction() {
        //Given-When

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002_insert_federico_happy_non_transactional.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_003_insert_jorge_failed_transactional_non_rollback.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.failedWithTransaction"))
                        .addDependency(client)
                        .build()
                        .run();
            });
        }


        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("table-create", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-user", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.ROLLED_BACK, auditLog.get(2).getState());

        //Checking user table
        List<String> rows = dynamoDBTestHelper.dynamoDBUtil.getEnhancedClient()
                .table("test_table", TableSchema.fromBean(UserEntity.class))
                .scan().items().stream()
                .map(UserEntity::getPartitionKey)
                .collect(Collectors.toList());

        assertEquals(1, rows.size());
        assertTrue(rows.contains("Pepe Pérez"));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions disabled and execution fails (with rollback method) should persist all the audit logs up to the failed one (ROLLED_BACK)")
    void failedWithoutTransactionWithRollback() {
        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002_insert_federico_happy_non_transactional.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_003_insert_jorge_failed_non_transactional_rollback.class, Collections.singletonList(DynamoDbClient.class), Collections.singletonList(DynamoDbClient.class)))
            );
            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.failedWithoutTransactionWithRollback"))
                        .addDependency(client)
                        .disableTransaction()
                        .build()
                        .run();
            });
        }

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(4, auditLog.size());
        assertEquals("table-create", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-user", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTION_FAILED, auditLog.get(2).getState());
        assertEquals("execution-with-exception", auditLog.get(3).getTaskId());
        assertEquals(AuditEntry.Status.ROLLED_BACK, auditLog.get(3).getState());

        //Checking user table
        List<String> rows = dynamoDBTestHelper.dynamoDBUtil.getEnhancedClient()
                .table("test_table", TableSchema.fromBean(UserEntity.class))
                .scan().items().stream()
                .map(UserEntity::getPartitionKey)
                .collect(Collectors.toList());

        assertEquals(1, rows.size());
        assertTrue(rows.contains("Pepe Pérez"));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions disabled and execution fails (without rollback method) should persist all the audit logs up to the failed one (FAILED)")
    void failedWithoutTransactionWithoutRollback() {
        //Given-When
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(_001_create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_002_insert_federico_happy_non_transactional.class, Collections.singletonList(DynamoDbClient.class)),
                    new Trio<>(_003_insert_jorge_failed_non_transactional_non_rollback.class, Collections.singletonList(DynamoDbClient.class), Collections.singletonList(DynamoDbClient.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                        //.addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.failedWithoutTransactionWithoutRollback"))
                        .addDependency(client)
                        .disableTransaction()
                        .build()
                        .run();
            });
        }


        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("table-create", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-user", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTION_FAILED, auditLog.get(2).getState());

        //Checking user table
        List<String> rows = dynamoDBTestHelper.dynamoDBUtil.getEnhancedClient()
                .table("test_table", TableSchema.fromBean(UserEntity.class))
                .scan().items().stream()
                .map(UserEntity::getPartitionKey)
                .collect(Collectors.toList());

        assertEquals(2, rows.size());
        assertTrue(rows.contains("Pepe Pérez"));
        assertTrue(rows.contains("Pablo López"));
    }
}