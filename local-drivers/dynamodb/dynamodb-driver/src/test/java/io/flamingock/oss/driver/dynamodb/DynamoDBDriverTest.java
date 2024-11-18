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

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.runner.PipelineExecutionException;
import io.flamingock.oss.driver.dynamodb.changes.common.UserEntity;
import io.flamingock.oss.driver.dynamodb.driver.DynamoDBDriver;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoDBConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


class DynamoDBDriverTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBDriverTest.class);

    private static DynamoDBProxyServer dynamoDBLocal;
    private static DynamoDbClient client;

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
    @DisplayName("When standalone runs the driver related tables should exists")
    void testTablesExistence() {
        //Given-When
        FlamingockStandalone.local()
                .setDriver(new DynamoDBDriver(client))
                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.happyPathWithTransaction"))
                .addDependency(client)
                .setTrackIgnored(true)
                .setTransactionEnabled(true)
                .build()
                .run();

        //Then
        assertTrue(dynamoDBTestHelper.tableExists(DynamoDBConstants.AUDIT_LOG_TABLE_NAME));
        assertTrue(dynamoDBTestHelper.tableExists(DynamoDBConstants.LOCK_TABLE_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions enabled should persist the audit logs and the user's table updated")
    void happyPathWithTransaction() {
        //Given-When
        FlamingockStandalone.local()
                .setDriver(new DynamoDBDriver(client))
                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.happyPathWithTransaction"))
                .addDependency(client)
                .setTrackIgnored(true)
                .setTransactionEnabled(true)
                .build()
                .run();

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(DynamoDBConstants.AUDIT_LOG_TABLE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("table-create", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-user", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-another-user", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());

        //Checking user table
        List<String> rows = dynamoDBTestHelper.client.getEnhancedClient()
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
        FlamingockStandalone.local()
                .setDriver(new DynamoDBDriver(client))
                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.happyPathWithoutTransaction"))
                .addDependency(client)
                .setTrackIgnored(true)
                .setTransactionEnabled(false)
                .build()
                .run();

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(DynamoDBConstants.AUDIT_LOG_TABLE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("table-create", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-user", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-another-user", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());

        //Checking user table
        List<String> rows = dynamoDBTestHelper.client.getEnhancedClient()
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
        assertThrows(PipelineExecutionException.class, () -> {
            FlamingockStandalone.local()
                    .setDriver(new DynamoDBDriver(client))
                    .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.failedWithTransaction"))
                    .addDependency(client)
                    .setTrackIgnored(true)
                    .setTransactionEnabled(true)
                    .build()
                    .run();
        });

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(DynamoDBConstants.AUDIT_LOG_TABLE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("table-create", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-user", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.ROLLED_BACK, auditLog.get(2).getState());

        //Checking user table
        List<String> rows = dynamoDBTestHelper.client.getEnhancedClient()
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
        assertThrows(PipelineExecutionException.class, () -> {
            FlamingockStandalone.local()
                    .setDriver(new DynamoDBDriver(client))
                    .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.failedWithoutTransactionWithRollback"))
                    .addDependency(client)
                    .setTrackIgnored(true)
                    .setTransactionEnabled(false)
                    .build()
                    .run();
        });

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(DynamoDBConstants.AUDIT_LOG_TABLE_NAME);
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
        List<String> rows = dynamoDBTestHelper.client.getEnhancedClient()
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
        assertThrows(PipelineExecutionException.class, () -> {
            FlamingockStandalone.local()
                    .setDriver(new DynamoDBDriver(client))
                    .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.dynamodb.changes.failedWithoutTransactionWithoutRollback"))
                    .addDependency(client)
                    .setTrackIgnored(true)
                    .setTransactionEnabled(false)
                    .build()
                    .run();
        });

        //Then
        //Checking auditLog
        List<AuditEntry> auditLog = dynamoDBTestHelper.getAuditEntriesSorted(DynamoDBConstants.AUDIT_LOG_TABLE_NAME);
        assertEquals(3, auditLog.size());
        assertEquals("table-create", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-user", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTION_FAILED, auditLog.get(2).getState());

        //Checking user table
        List<String> rows = dynamoDBTestHelper.client.getEnhancedClient()
                .table("test_table", TableSchema.fromBean(UserEntity.class))
                .scan().items().stream()
                .map(UserEntity::getPartitionKey)
                .collect(Collectors.toList());

        assertEquals(2, rows.size());
        assertTrue(rows.contains("Pepe Pérez"));
        assertTrue(rows.contains("Pablo López"));
    }
}