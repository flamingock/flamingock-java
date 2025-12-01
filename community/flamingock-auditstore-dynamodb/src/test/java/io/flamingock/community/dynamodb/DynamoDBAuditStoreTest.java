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
package io.flamingock.community.dynamodb;

import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import io.flamingock.community.dynamodb.changes._001__create_client_collection_happy;
import io.flamingock.community.dynamodb.changes._002__insert_federico_happy_non_transactional;
import io.flamingock.community.dynamodb.changes._002__insert_federico_happy_transactional;
import io.flamingock.community.dynamodb.changes._003__insert_jorge_failed_transactional_non_rollback;
import io.flamingock.community.dynamodb.changes._004__insert_jorge_happy_transactional;
import io.flamingock.community.dynamodb.changes.common.UserEntity;
import io.flamingock.community.dynamodb.driver.DynamoDBAuditStore;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.dynamodb.kit.DynamoDBTestContainer;
import io.flamingock.dynamodb.kit.DynamoDBTestKit;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.internal.util.dynamodb.DynamoDBConstants;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.FAILED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.ROLLED_BACK;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.auditEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class DynamoDBAuditStoreTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBAuditStoreTest.class);

    @Container
    private static GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDBTestKit testKit;
    private DynamoDbClient client;
    private AuditTestHelper auditHelper;
    private static final String CUSTOM_AUDIT_REPOSITORY_NAME = "testFlamingockAudit";
    private static final String CUSTOM_LOCK_REPOSITORY_NAME = "testFlamingockLock";

    @BeforeEach
    void beforeEach() {
        logger.info("Setting up DynamoDB client and audit helper for container...");
        client = DynamoDBTestContainer.createClient(dynamoDBContainer);

        // Initialize test kit with DynamoDB persistence using the same client as the driver
        testKit = DynamoDBTestKit.create(client, DynamoDBAuditStore.from(new DynamoDBTargetSystem("dynamodb", client)));
        auditHelper = testKit.getAuditHelper();
    }

    @AfterEach
    void afterEach() {
        testKit.cleanUp();
    }


    @Test
    @DisplayName("When standalone runs the driver with DEFAULT repository names related tables should exists")
    void happyPathWithDefaultRepositoryNames() {
        // Given-When
        DynamoDBTargetSystem dynamoDBTargetSystem = new DynamoDBTargetSystem("dynamodb", client);
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)),
                        new CodeChangeTestDefinition(_004__insert_jorge_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)))
                .WHEN(() -> {
                    FlamingockFactory.getCommunityBuilder()
                            .setAuditStore(DynamoDBAuditStore.from(dynamoDBTargetSystem))
                            .addTargetSystem(dynamoDBTargetSystem)
                            .build()
                            .run();
                })
                .run();

        // Then - Verify table existence
        List<String> tableNames = client.listTables().tableNames();
        assertTrue(tableNames.contains(CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME));
        assertTrue(tableNames.contains(CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME));
        assertFalse(tableNames.contains(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertFalse(tableNames.contains(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the driver with transactions enabled should persist the audit logs and the user's table updated")
    void happyPathWithTransaction() {
        // Given-When-Then
        DynamoDBTargetSystem dynamoDBTargetSystem = new DynamoDBTargetSystem("dynamodb", client);
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)),
                        new CodeChangeTestDefinition(_004__insert_jorge_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)))
                .WHEN(() -> {
                    // Run pipeline twice to verify repeated execution
                    FlamingockFactory.getCommunityBuilder()
                            .setAuditStore(DynamoDBAuditStore.from(dynamoDBTargetSystem))
                            .addTargetSystem(dynamoDBTargetSystem)
                            .build()
                            .run();

                    FlamingockFactory.getCommunityBuilder()
                            .setAuditStore(DynamoDBAuditStore.from(dynamoDBTargetSystem))
                            .addTargetSystem(dynamoDBTargetSystem)
                            .build()
                            .run();
                })
                .THEN_VerifyAuditSequenceStrict(
                        auditEntry().withTaskId("table-create").withState(AuditEntry.Status.STARTED),
                        auditEntry().withTaskId("table-create").withState(AuditEntry.Status.APPLIED),
                        auditEntry().withTaskId("insert-user").withState(AuditEntry.Status.STARTED),
                        auditEntry().withTaskId("insert-user").withState(AuditEntry.Status.APPLIED),
                        auditEntry().withTaskId("insert-another-user").withState(AuditEntry.Status.STARTED),
                        auditEntry().withTaskId("insert-another-user").withState(AuditEntry.Status.APPLIED)
                )
                .run();

        // Verify user table data
        DynamoDBUtil dynamoDBUtil = new DynamoDBUtil(client);
        List<String> rows = dynamoDBUtil.getEnhancedClient()
                .table("test_table", TableSchema.fromBean(UserEntity.class))
                .scan().items().stream()
                .map(UserEntity::getPartitionKey)
                .collect(Collectors.toList());

        assertEquals(2, rows.size());
        assertTrue(rows.contains("Pepe Pérez"));
        assertTrue(rows.contains("Pablo López"));
    }

    @Test
    @DisplayName("When standalone runs the driver and execution fails should persist only the applied audit logs")
    void failedWithTransaction() {
        // Given-When-Then - Test failure scenario with audit verification
        DynamoDBTargetSystem dynamoDBTargetSystem = new DynamoDBTargetSystem("dynamodb", client);
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_non_transactional.class, Collections.singletonList(DynamoDbClient.class)),
                        new CodeChangeTestDefinition(_003__insert_jorge_failed_transactional_non_rollback.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)))
                .WHEN(() -> {
                    assertThrows(PipelineExecutionException.class, () -> {
                        FlamingockFactory.getCommunityBuilder()
                                .setAuditStore(DynamoDBAuditStore.from(dynamoDBTargetSystem))
                                .addTargetSystem(dynamoDBTargetSystem)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("table-create"),
                        APPLIED("table-create"),

                        STARTED("insert-user"),
                        APPLIED("insert-user"),

                        STARTED("execution-with-exception"),
                        FAILED("execution-with-exception"),
                        ROLLED_BACK("execution-with-exception")
                )
                .run();

        // Verify user table - only one user should remain due to rollback
        DynamoDBUtil dynamoDBUtil = new DynamoDBUtil(client);
        List<String> rows = dynamoDBUtil.getEnhancedClient()
                .table("test_table", TableSchema.fromBean(UserEntity.class))
                .scan().items().stream()
                .map(UserEntity::getPartitionKey)
                .collect(Collectors.toList());

        assertEquals(1, rows.size());
        assertTrue(rows.contains("Pepe Pérez"));
    }

    @Test
    @DisplayName("DynamoDBAuditStore withAutoCreate(false) does not create tables automatically")
    void auditStoreWithAutoCreateFalseDoesNotCreateTables() {
        List<String> initialTables = client.listTables().tableNames();
        assertFalse(initialTables.contains(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertFalse(initialTables.contains(CUSTOM_LOCK_REPOSITORY_NAME));

        DynamoDBUtil dynamoDBUtil = new DynamoDBUtil(client);
        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions(DynamoDBConstants.AUDIT_LOG_PK, null),
                dynamoDBUtil.getKeySchemas(DynamoDBConstants.AUDIT_LOG_PK, null),
                dynamoDBUtil.getProvisionedThroughput(1L, 1L),
                CUSTOM_AUDIT_REPOSITORY_NAME,
                Collections.emptyList(),
                Collections.emptyList()
        );
        dynamoDBUtil.createTable(
                dynamoDBUtil.getAttributeDefinitions(DynamoDBConstants.LOCK_PK, null),
                dynamoDBUtil.getKeySchemas(DynamoDBConstants.LOCK_PK, null),
                dynamoDBUtil.getProvisionedThroughput(1L, 1L),
                CUSTOM_LOCK_REPOSITORY_NAME,
                Collections.emptyList(),
                Collections.emptyList()
        );

        DynamoDBTargetSystem dynamoDBTargetSystem = new DynamoDBTargetSystem("dynamodb", client);
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class, Collections.singletonList(DynamoDbClient.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)),
                        new CodeChangeTestDefinition(_004__insert_jorge_happy_transactional.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)))
                .WHEN(() -> {
                    FlamingockFactory.getCommunityBuilder()
                            .setAuditStore(DynamoDBAuditStore.from(dynamoDBTargetSystem)
                                    .withAutoCreate(false)
                                    .withAuditRepositoryName(CUSTOM_AUDIT_REPOSITORY_NAME)
                                    .withLockRepositoryName(CUSTOM_LOCK_REPOSITORY_NAME))
                            .addTargetSystem(dynamoDBTargetSystem)
                            .build()
                            .run();
                })
                .run();

        List<String> tableNames = client.listTables().tableNames();
        assertTrue(tableNames.contains(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertTrue(tableNames.contains(CUSTOM_LOCK_REPOSITORY_NAME));
    }
}
