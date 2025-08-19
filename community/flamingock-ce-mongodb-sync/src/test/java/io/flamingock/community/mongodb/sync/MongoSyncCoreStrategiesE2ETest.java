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
import io.flamingock.common.test.pipeline.PipelineTestHelper;
import io.flamingock.community.mongodb.sync.changes._001_create_client_collection_happy;
import io.flamingock.community.mongodb.sync.changes._002_insert_federico_happy_non_transactional;
import io.flamingock.community.mongodb.sync.changes._002_insert_federico_happy_transactional;
import io.flamingock.community.mongodb.sync.changes._003_insert_jorge_failed_non_transactional_rollback;
import io.flamingock.community.mongodb.sync.kit.MongoSyncTestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.List;

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
@Disabled("Until we add the STARTED state")
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
        MongoSyncTestKit testKit = MongoSyncTestKit.create(mongoClient, database);
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList()),
                            new CodeChangeUnitTestDefinition(_002_insert_federico_happy_non_transactional.class, Collections.emptyList())
                    )
            );

            // When - Execute using MongoDB-backed test builder
            testKit.createBuilder()
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
        }

        // Then - Verify complete audit flow using MongoDB storage
        List<AuditEntry> auditEntries = auditHelper.getAuditEntriesSorted();
        assertEquals(4, auditEntries.size(), "Expected 2 EXECUTED audit entries in MongoDB");

        // Verify both changes executed successfully with MongoDB persistence
        auditHelper.verifySuccessfulChangeExecution("create-client-collection");
        auditHelper.verifySuccessfulChangeExecution("insert-federico-document");

        // Verify audit counts using MongoDB storage
        assertEquals(0, auditHelper.getStartedAuditCount()); // STARTED entries not written by default
        assertEquals(2, auditHelper.getExecutedAuditCount());
        assertEquals(0, auditHelper.getFailedAuditCount());
        assertEquals(0, auditHelper.getRolledBackAuditCount());
    }

    @Test
    @DisplayName("Should execute transactional change using SimpleTx/SharedTx strategy with complete audit flow in MongoDB")
    void testTransactionalChangeExecution() {
        // Given - Create MongoDB test kit with real database storage
        MongoSyncTestKit testKit = MongoSyncTestKit.create(mongoClient, database);
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList()),
                            new CodeChangeUnitTestDefinition(_002_insert_federico_happy_transactional.class, Collections.emptyList())
                    )
            );

            // When - Execute transactional change with MongoDB
            testKit.createBuilder()
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
        }

        // Then - Verify complete audit flow for transactional change in MongoDB
        List<AuditEntry> auditEntries = auditHelper.getAuditEntriesSorted();
        assertEquals(4, auditEntries.size(), "Expected 2 changes × 2 audit entries each = 4 total in MongoDB");

        auditHelper.verifySuccessfulChangeExecution("create-client-collection");
        auditHelper.verifySuccessfulChangeExecution("insert-federico-document");

        assertEquals(2, auditHelper.getStartedAuditCount());
        assertEquals(2, auditHelper.getExecutedAuditCount());
        assertEquals(0, auditHelper.getFailedAuditCount());
        assertEquals(0, auditHelper.getRolledBackAuditCount());
    }

    @Test
    @DisplayName("Should execute multiple changes with correct audit sequence in MongoDB")
    void testMultipleChangesExecution() {
        // Given - Create MongoDB test kit with real database storage
        MongoSyncTestKit testKit = MongoSyncTestKit.create(mongoClient, database);
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList()),
                            new CodeChangeUnitTestDefinition(_002_insert_federico_happy_non_transactional.class, Collections.emptyList())
                    )
            );

            // When - Execute multiple changes with MongoDB persistence
            testKit.createBuilder()
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
        }

        // Then - Verify multiple changes executed successfully with MongoDB storage
        List<AuditEntry> auditEntries = auditHelper.getAuditEntriesSorted();
        assertEquals(4, auditEntries.size(), "Expected 2 changes × 2 audit entries each = 4 total in MongoDB");

        // Verify each change has complete audit sequence in MongoDB
        auditHelper.verifySuccessfulChangeExecution("create-client-collection");
        auditHelper.verifySuccessfulChangeExecution("insert-federico-document");

        // Verify total counts with MongoDB persistence
        assertEquals(2, auditHelper.getStartedAuditCount());
        assertEquals(2, auditHelper.getExecutedAuditCount());
        assertEquals(0, auditHelper.getFailedAuditCount());
    }

    @Test
    @DisplayName("Should handle failing transactional change with proper audit and rollback in MongoDB")
    void testFailingTransactionalChangeWithRollback() {
        // Given - Create MongoDB test kit with real database storage
        MongoSyncTestKit testKit = MongoSyncTestKit.create(mongoClient, database);
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList()),
                            new CodeChangeUnitTestDefinition(_003_insert_jorge_failed_non_transactional_rollback.class, Collections.emptyList())
                    )
            );

            // When & Then - Execution should fail with MongoDB storage
            assertThrows(PipelineExecutionException.class, () -> {
                testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .build()
                        .run();
            });
        }

        // Then - Verify failure audit sequence using MongoDB storage
        List<AuditEntry> auditEntries = auditHelper.getAuditEntriesSorted();

        // Should have: collection creation (STARTED + EXECUTED) + failed insert (STARTED + EXECUTION_FAILED + ROLLED_BACK) = 5 total
        assertEquals(5, auditEntries.size());

        // First change (collection creation) should succeed
        assertEquals("create-client-collection", auditEntries.get(0).getTaskId());
        assertEquals(AuditEntry.Status.STARTED, auditEntries.get(0).getState());
        assertEquals("create-client-collection", auditEntries.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditEntries.get(1).getState());

        // Second change (jorge insert) should fail and rollback
        assertEquals("insert-jorge-document", auditEntries.get(2).getTaskId());
        assertEquals(AuditEntry.Status.STARTED, auditEntries.get(2).getState());
        assertEquals("insert-jorge-document", auditEntries.get(3).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTION_FAILED, auditEntries.get(3).getState());
        assertEquals("insert-jorge-document", auditEntries.get(4).getTaskId());
        assertEquals(AuditEntry.Status.ROLLED_BACK, auditEntries.get(4).getState());

        // Verify audit counts with MongoDB persistence
        assertEquals(2, auditHelper.getStartedAuditCount());
        assertEquals(1, auditHelper.getExecutedAuditCount());
        assertEquals(1, auditHelper.getFailedAuditCount());
        assertEquals(1, auditHelper.getRolledBackAuditCount());
    }

    @Test
    @DisplayName("Should handle already-executed changes correctly on second run with MongoDB persistence")
    void testAlreadyExecutedChangesSkipping() {
        // Given - Create MongoDB test kit with persistent storage
        MongoSyncTestKit testKit = MongoSyncTestKit.create(mongoClient, database);
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(_001_create_client_collection_happy.class, Collections.emptyList())
                    )
            );

            // First execution with MongoDB persistence
            testKit.createBuilder()
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();

            // Verify first execution with MongoDB storage
            assertEquals(2, auditHelper.getAuditEntriesSorted().size());

            // Second execution - create new kit using SAME MongoDB database to simulate persistence
            // The audit entries should persist across kit instances when using same database
            MongoSyncTestKit secondRunKit = MongoSyncTestKit.create(mongoClient, database);

            secondRunKit.createBuilder()
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
        }

        // Then - Should still have only original 2 audit entries in MongoDB (no additional executions)
        List<AuditEntry> auditEntries = auditHelper.getAuditEntriesSorted();
        assertEquals(2, auditEntries.size(), "Should not have additional audit entries for already-executed change in MongoDB");

        // Original change should still show as successfully executed in MongoDB
        auditHelper.verifySuccessfulChangeExecution("_001_create_client_collection_happy");
    }
}