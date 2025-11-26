/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.importer.mongock.dynamodb;

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.community.dynamodb.driver.DynamoDBAuditStore;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.dynamodb.kit.DynamoDBTableFactory;
import io.flamingock.dynamodb.kit.DynamoDBTestKit;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.runner.Runner;
import io.flamingock.support.mongock.annotations.MongockSupport;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeyType;

import java.net.URI;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static io.flamingock.internal.common.core.metadata.Constants.DEFAULT_MONGOCK_ORIGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@MongockSupport(targetSystem = "dynamodb-target-system")
@EnableFlamingock(stages = {@Stage(location = "io.flamingock.importer.mongock.dynamodb.changes")})
public class DynamoDBImporterTest {

    @Container
    public static final GenericContainer<?> dynamoDBContainer = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000);

    private static DynamoDbClient client;
    private DynamoDBMongockTestHelper mongockTestHelper;
    private TestKit testKit;
    private AuditTestHelper auditHelper;

    @BeforeEach
    void setUp() {
        String endpoint = String.format("http://%s:%d",
                dynamoDBContainer.getHost(),
                dynamoDBContainer.getMappedPort(8000));
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("dummy", "dummy")
                        )
                )
                .build();

        // Create required tables using DynamoDBTableFactory
        DynamoDBTableFactory.createMongockTable(client, DEFAULT_MONGOCK_ORIGIN);

        mongockTestHelper = new DynamoDBMongockTestHelper(client, DEFAULT_MONGOCK_ORIGIN);

        // Initialize TestKit for unified testing
        testKit = DynamoDBTestKit.create(client, new DynamoDBAuditStore(client));
        auditHelper = testKit.getAuditHelper();

    }

    @AfterEach
    void tearDown() {
        // DynamoDB local doesn't need explicit cleanup between tests
        // Tables are automatically cleaned by Testcontainers on restart
        mongockTestHelper.reset();
        testKit.cleanUp();
        client.close();
    }

    @Test
    @DisplayName("GIVEN all Mongock changeUnits already executed" +
            "WHEN migrating to Flamingock Community " +
            "THEN should import the entire history " +
            "AND execute the pending flamingock changes")
    void GIVEN_allMongockChangeUnitsAlreadyExecuted_WHEN_migratingToFlamingockCommunity_THEN_shouldImportEntireHistory() {
        // Setup Mongock entries
        mongockTestHelper.setupBasicScenario();

        DynamoDBTargetSystem dynamodbTargetSystem = new DynamoDBTargetSystem("dynamodb-target-system", client);

        Runner flamingock = testKit.createBuilder()
                .addTargetSystem(dynamodbTargetSystem)
                .build();

        flamingock.run();

        // Verify audit sequence: 9 total entries
        // Legacy imports only show APPLIED (imported from Mongock), new changes show STARTED+APPLIED
        auditHelper.verifyAuditSequenceStrict(
                // Legacy imports from Mongock (APPLIED only - no STARTED for imported changes)
                APPLIED("system-change-00001_before"),
                APPLIED("system-change-00001"),
                APPLIED("mongock-change-1_before"),
                APPLIED("mongock-change-1"),
                APPLIED("mongock-change-2"),

                // System stage - actual system importer change
                STARTED("migration-mongock-to-flamingock-community"),
                APPLIED("migration-mongock-to-flamingock-community"),

                // Application stage - new change created by code
                STARTED("create-users-table"),
                APPLIED("create-users-table")
        );

        // Validate actual table creation
        assertTrue(client.listTables().tableNames().contains("users"), "Users table should exist");

        // Verify table structure
        DescribeTableResponse tableDescription = client.describeTable(
                DescribeTableRequest.builder().tableName("users").build()
        );
        assertEquals("email", tableDescription.table().keySchema().get(0).attributeName());
        assertEquals(KeyType.HASH, tableDescription.table().keySchema().get(0).keyType());
    }

    @Test
    @DisplayName("GIVEN some Mongock changeUnits already executed " +
            "AND some other Mongock changeUnits pending for execution" +
            "WHEN migrating to Flamingock Community" +
            "THEN migrates the history with the executed changeUnits " +
            "AND executes the pending Mongock changeUnits " +
            "AND executes the pending Flamingock changes")
    void GIVEN_someChangeUnitsAlreadyExecuted_WHEN_migratingToFlamingockCommunity_THEN_shouldImportEntireHistory() {
        // Setup Mongock entries
        mongockTestHelper.setupWithOnlyOneChange();

        DynamoDBTargetSystem dynamodbTargetSystem = new DynamoDBTargetSystem("dynamodb-target-system", client);

        Runner flamingock = testKit.createBuilder()
                .addTargetSystem(dynamodbTargetSystem)
                .build();

        flamingock.run();

        // Verify audit sequence: 9 total entries
        // Legacy imports only show APPLIED (imported from Mongock), new changes show STARTED+APPLIED
        auditHelper.verifyAuditSequenceStrict(
                // Legacy imports from Mongock (APPLIED only - no STARTED for imported changes)
                APPLIED("system-change-00001_before"),
                APPLIED("system-change-00001"),
                APPLIED("mongock-change-1_before"),
                APPLIED("mongock-change-1"),

                // System stage - actual system importer change
                STARTED("migration-mongock-to-flamingock-community"),
                APPLIED("migration-mongock-to-flamingock-community"),


                STARTED("mongock-change-2"),
                APPLIED("mongock-change-2"),
                // Application stage - new change created by code
                STARTED("create-users-table"),
                APPLIED("create-users-table")
        );

        // Validate actual table creation
        assertTrue(client.listTables().tableNames().contains("users"), "Users table should exist");

        // Verify table structure
        DescribeTableResponse tableDescription = client.describeTable(
                DescribeTableRequest.builder().tableName("users").build()
        );
        assertEquals("email", tableDescription.table().keySchema().get(0).attributeName());
        assertEquals(KeyType.HASH, tableDescription.table().keySchema().get(0).keyType());
    }

    @Test
    @DisplayName("GIVEN mongock audit history empty " +
            "WHEN migrating to Flamingock Community" +
            "THEN should throw exception")
    void GIVEN_mongockAuditHistoryEmpty_WHEN_migratingToFlamingockCommunity_THEN_shouldThrowException() {
        // Setup Mongock entries

        DynamoDBTargetSystem dynamodbTargetSystem = new DynamoDBTargetSystem("dynamodb-target-system", client);

        Runner flamingock = testKit.createBuilder()
                .addTargetSystem(dynamodbTargetSystem)
                .build();

        FlamingockException ex = assertThrows(FlamingockException.class, flamingock::run);
        assertEquals("No audit entries found when importing from 'dynamodb-target-system'.", ex.getMessage());

    }

}
