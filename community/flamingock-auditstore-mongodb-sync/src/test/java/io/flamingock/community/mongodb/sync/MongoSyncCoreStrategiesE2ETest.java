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
package io.flamingock.community.mongodb.sync;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.common.test.pipeline.CodeChangeUnitTestDefinition;
import io.flamingock.community.mongodb.sync.changes._001_create_client_collection_happy;
import io.flamingock.community.mongodb.sync.changes._002_insert_federico_happy_non_transactional;
import io.flamingock.community.mongodb.sync.changes._002_insert_federico_happy_transactional;
import io.flamingock.community.mongodb.sync.changes._003_insert_jorge_failed_non_transactional_rollback;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.mongodb.kit.MongoSyncTestKit;
import io.flamingock.community.mongodb.sync.driver.MongoSyncAuditStore;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.EXECUTED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.EXECUTION_FAILED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.ROLLED_BACK;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MongoDB-specific version of CoreStrategiesE2ETest demonstrating unified TestKit API
 * with real MongoDB storage using Testcontainers.
 *
 * <p>This test suite replicates all scenarios from CoreStrategiesE2ETest but uses
 * MongoSyncTestKit with actual MongoDB persistence, proving the audit-store agnostic
 * design works identically across storage implementations.</p>
 */
@Testcontainers
class MongoSyncCoreStrategiesE2ETest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4.6"))
            .withReuse(true);

    private MongoClient mongoClient;
    private MongoDatabase database;

    @BeforeAll
    static void beforeAll() {
        mongoDBContainer.start();
    }

    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase("test");
    }

    @AfterEach
    void tearDown() {
        database.drop(); // Clean between tests
        mongoClient.close();
    }

    @Test
    @DisplayName("Should execute non-transactional change using NonTx strategy with complete audit flow in MongoDB")
    void testNonTransactionalChangeExecution() {
        // Given - Create MongoDB test kit with real database storage
        TestKit testKit = MongoSyncTestKit.create(new MongoSyncAuditStore(), mongoClient, database);

        // Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList()),
                        new CodeChangeUnitTestDefinition(_002_insert_federico_happy_non_transactional.class, Collections.emptyList())
                )
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(new MongoSyncAuditStore())
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(mongoClient)
                        .addDependency(database)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection"),
                        STARTED("insert-federico-document"),
                        EXECUTED("insert-federico-document")
                )
                .run();
    }

    @Test
    @DisplayName("Should execute transactional change using SimpleTx/SharedTx strategy with complete audit flow in MongoDB")
    void testTransactionalChangeExecution() {
        // Given - Create MongoDB test kit with real database storage
        TestKit testKit = MongoSyncTestKit.create(new MongoSyncAuditStore(), mongoClient, database);
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        // Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList()),
                        new CodeChangeUnitTestDefinition(_002_insert_federico_happy_transactional.class, Collections.emptyList())
                )
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(new MongoSyncAuditStore())
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(mongoClient)
                        .addDependency(database)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection"),
                        STARTED("insert-federico-document"),
                        EXECUTED("insert-federico-document")
                )
                .run();
    }

    @Test
    @DisplayName("Should execute multiple changes with correct audit sequence in MongoDB")
    void testMultipleChangesExecution() {
        // Given - Create MongoDB test kit with real database storage
        TestKit testKit = MongoSyncTestKit.create(new MongoSyncAuditStore(), mongoClient, database);
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        // Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList()),
                        new CodeChangeUnitTestDefinition(_002_insert_federico_happy_non_transactional.class, Collections.emptyList())
                )
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(new MongoSyncAuditStore())
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(mongoClient)
                        .addDependency(database)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection"),
                        STARTED("insert-federico-document"),
                        EXECUTED("insert-federico-document")
                )
                .run();
    }

    @Test
    @DisplayName("Should handle failing transactional change with proper audit and rollback in MongoDB")
    void testFailingTransactionalChangeWithRollback() {
        // Given - Create MongoDB test kit with real database storage
        TestKit testKit = MongoSyncTestKit.create(new MongoSyncAuditStore(), mongoClient, database);
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        // Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList()),
                        new CodeChangeUnitTestDefinition(_003_insert_jorge_failed_non_transactional_rollback.class, Collections.emptyList())
                )
                .WHEN(() -> assertThrows(PipelineExecutionException.class, () -> {
                    testKit.createBuilder()
                        .setAuditStore(new MongoSyncAuditStore())
                            .setRelaxTargetSystemValidation(true)
                            .addDependency(mongoClient)
                            .addDependency(database)
                            .build()
                            .run();
                }))
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection"),
                        STARTED("insert-jorge-document"),
                        EXECUTION_FAILED("insert-jorge-document"),
                        ROLLED_BACK("insert-jorge-document")
                )
                .run();
    }

    @Test
    @DisplayName("Should handle already-executed changes correctly on second run with MongoDB persistence")
    void testAlreadyExecutedChangesSkipping() {
        // Given - Create MongoDB test kit with persistent storage
        TestKit testKit = MongoSyncTestKit.create(new MongoSyncAuditStore(), mongoClient, database);
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        // Given-When-Then - First execution
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList())
                )
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(new MongoSyncAuditStore())
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(mongoClient)
                        .addDependency(database)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection")
                )
                .run();

        // Verify first execution with MongoDB storage
        assertEquals(2, auditHelper.getAuditEntriesSorted().size());

        // Second execution - create new kit using SAME MongoDB database to simulate persistence
        TestKit secondRunKit = MongoSyncTestKit.create(new MongoSyncAuditStore(), mongoClient, database);

        AuditTestSupport.withTestKit(secondRunKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList())
                )
                .WHEN(() -> secondRunKit.createBuilder()
                        .setAuditStore(new MongoSyncAuditStore())
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(mongoClient)
                        .addDependency(database)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("create-client-collection"),
                        EXECUTED("create-client-collection")
                )
                .run();
    }
}