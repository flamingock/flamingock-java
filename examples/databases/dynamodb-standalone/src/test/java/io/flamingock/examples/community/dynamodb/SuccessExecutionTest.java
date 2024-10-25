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

package io.flamingock.examples.community.dynamodb;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import io.flamingock.examples.community.dynamodb.changes.UserEntity;
import io.flamingock.examples.community.dynamodb.events.FailureEventListener;
import io.flamingock.examples.community.dynamodb.events.StartedEventListener;
import io.flamingock.examples.community.dynamodb.events.SuccessEventListener;
import io.flamingock.oss.driver.dynamodb.internal.entities.AuditEntryEntity;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoClients;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoDBConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class SuccessExecutionTest {

    private static final Logger logger = LoggerFactory.getLogger(SuccessExecutionTest.class);

    private static DynamoDBProxyServer dynamoDBLocal;
    private static DynamoDbClient client;
    private static DynamoDbEnhancedClient enhancedClient;

    @BeforeAll
    static void beforeAll() throws Exception {
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

        enhancedClient = new DynamoClients(client).getEnhancedClient();

        new CommunityStandaloneDynamoDBApp().run(client);
    }

    @AfterAll
    static void afterAll() throws Exception {
        logger.info("Stopping DynamoDB Local...");
        dynamoDBLocal.stop();
    }

    @Test
    @DisplayName("SHOULD create 'test_table' table and insert two users")
    void functionalTest() {
        List<String> tables = client.listTables().tableNames();
        assertTrue(tables.contains("test_table"));

        List<String> rows = enhancedClient
                .table("test_table", TableSchema.fromBean(UserEntity.class))
                .scan().items().stream()
                .map(UserEntity::getPartitionKey)
                .collect(Collectors.toList());

        assertEquals(2, rows.size());
        assertTrue(rows.contains("Pepe Pérez"));
        assertTrue(rows.contains("Pablo López"));
    }

    @Test
    @DisplayName("SHOULD insert the Flamingock change history")
    void flamingockLogsTest() {
        List<AuditEntryEntity> rows = enhancedClient
                .table(DynamoDBConstants.AUDIT_LOG_TABLE_NAME, TableSchema.fromBean(AuditEntryEntity.class))
                .scan().items().stream()
                .collect(Collectors.toList());

        List<String> taskIds = rows.stream()
                .map(AuditEntryEntity::getTaskId)
                .collect(Collectors.toList());
        assertTrue(taskIds.contains("table-create"));
        assertTrue(taskIds.contains("insert-user"));
        assertTrue(taskIds.contains("insert-another-user"));

        List<String> states = rows.stream()
                .map(AuditEntryEntity::getState)
                .collect(Collectors.toList());
        assertTrue(states.contains("EXECUTED"));

        List<String> classes = rows.stream()
                .map(AuditEntryEntity::getClassName)
                .collect(Collectors.toList());
        assertTrue(classes.contains("io.flamingock.examples.community.dynamodb.changes.ACreateTable"));
        assertTrue(classes.contains("io.flamingock.examples.community.dynamodb.changes.BInsertRow"));
        assertTrue(classes.contains("io.flamingock.examples.community.dynamodb.changes.CInsertAnotherRow"));

        assertEquals(3, rows.size());
    }

    @Test
    @DisplayName("SHOULD trigger start and success event WHEN executed IF happy path")
    void events() {
        assertTrue(StartedEventListener.executed);
        assertTrue(SuccessEventListener.executed);
        assertFalse(FailureEventListener.executed);
    }
}
