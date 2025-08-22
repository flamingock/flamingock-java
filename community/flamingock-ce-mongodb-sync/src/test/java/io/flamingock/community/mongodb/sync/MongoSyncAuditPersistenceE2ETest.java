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
import io.flamingock.community.mongodb.sync.changes.audit.NonTxTargetSystemChange;
import io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange;
import io.flamingock.community.mongodb.sync.changes.audit.TxSeparateAndSameMongoClientChange;
import io.flamingock.community.mongodb.sync.changes.audit.TxSeparateChange;
import io.flamingock.community.mongodb.sync.changes.audit.TxSharedDefaultChange;
import io.flamingock.community.mongodb.sync.driver.MongoSyncDriver;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditEntryAssertions;
import io.flamingock.core.kit.audit.AuditEntryExpectation;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.targets.DefaultTargetSystem;
import io.flamingock.mongodb.kit.MongoSyncTestKit;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.EXECUTED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.auditEntry;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class MongoSyncAuditPersistenceE2ETest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4.6"));

    private TestKit testKit;
    private AuditTestHelper auditHelper;
    private MongoClient sharedMongoClient;
    private MongoClient separateMongoClient;
    private MongoDatabase database;

    @BeforeEach
    void setUp() {
        // Create shared MongoClient for audit storage and TX_SHARED scenarios
        sharedMongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = sharedMongoClient.getDatabase("test");

        // Create separate MongoClient for TX_SEPARATE_NO_MARKER scenarios
        separateMongoClient = MongoClients.create(mongoDBContainer.getConnectionString());

        // Initialize test kit with MongoDB persistence
        testKit = MongoSyncTestKit.create(new MongoSyncDriver(), database);
        auditHelper = testKit.getAuditHelper();
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.drop(); // Clean between tests
        }
        if (sharedMongoClient != null) {
            sharedMongoClient.close();
        }
        if (separateMongoClient != null) {
            separateMongoClient.close();
        }
    }

    @Test
    @DisplayName("Should persist all audit entry fields correctly in MongoDB")
    void testCompleteAuditEntryPersistenceInMongoDB() {
        // Given
        String changeId = "non-tx-transactional-false";
        LocalDateTime testStart = LocalDateTime.now();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock with MongoDB persistence
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(sharedMongoClient)
                        .addDependency(database)
                        .build()
                        .run();
            });
        }

        LocalDateTime testEnd = LocalDateTime.now();

        // Then - Verify complete audit persistence in MongoDB using unified approach
        AuditEntryExpectation startedExpected = STARTED(changeId)
                .withType(AuditEntry.ExecutionType.EXECUTION)
                .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange.class)
                .withTxType(AuditTxType.NON_TX)
                .withTargetSystemId("default-audit-store-target-system")
                .withSystemChange(false)
                .withTimestampBetween(testStart, testEnd);

        AuditEntryExpectation executedExpected = EXECUTED(changeId)
                .withType(AuditEntry.ExecutionType.EXECUTION)
                .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange.class)
                .withTxType(AuditTxType.NON_TX)
                .withTargetSystemId("default-audit-store-target-system")
                .withSystemChange(false)
                .withTimestampBetween(testStart, testEnd);

        auditHelper.verifyAuditSequenceStrict(startedExpected, executedExpected);
    }

    @Test
    @DisplayName("Should persist NON_TX txType for transactional=false scenarios")
    void testNonTxScenarios() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.emptyList()),
                            new CodeChangeUnitTestDefinition(NonTxTargetSystemChange.class, Collections.emptyList())
                    )
            );

            // When - Run with NON_TX scenarios
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(new DefaultTargetSystem("non-tx-system")) // Non-transactional target system
                        .addDependency(sharedMongoClient)
                        .addDependency(database)
                        .build()
                        .run();
            });
        }

        // Then - Verify NON_TX txType and complete field persistence using unified approach
        auditHelper.verifyAuditSequenceStrict(
                // First change (NonTxTransactionalFalseChange) - STARTED & EXECUTED
                STARTED("non-tx-transactional-false")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("default-audit-store-target-system"),
                EXECUTED("non-tx-transactional-false")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("default-audit-store-target-system"),

                // Second change (NonTxTargetSystemChange) - STARTED & EXECUTED
                STARTED("non-tx-target-system")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTargetSystemChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("non-tx-system"),
                EXECUTED("non-tx-target-system")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTargetSystemChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("non-tx-system")
        );
    }

    @Test
    @DisplayName("Should persist TX_SHARED txType when targetSystem not defined in changeUnit")
    void testTxSharedScenarios() {
        MongoSyncTargetSystem sharedTargetSystem = new MongoSyncTargetSystem("tx-shared-system")
                .withMongoClient(sharedMongoClient) // Same MongoClient as audit storage
                .withDatabase(database);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(TxSharedDefaultChange.class, Collections.emptyList())
                    )
            );

            // When - Run with TX_SHARED scenarios
            assertDoesNotThrow(() -> {


                testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(sharedTargetSystem)
                        .addDependency(sharedMongoClient)
                        .addDependency(database)
                        .build()
                        .run();
            });
        }

        // Then - Verify TX_SHARED txType and complete field persistence using unified approach
        auditHelper.verifyAuditSequenceStrict(
                STARTED("tx-shared-default")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSharedDefaultChange.class)
                        .withTxType(AuditTxType.TX_SHARED)
                        .withTargetSystemId("default-audit-store-target-system"),
                EXECUTED("tx-shared-default")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSharedDefaultChange.class)
                        .withTxType(AuditTxType.TX_SHARED)
                        .withTargetSystemId("default-audit-store-target-system")
        );
    }

    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER when targetSystem defined and different from auditStore")
    void testTxNoMarkerWhenSameMongoClientButDifferentTargetSystem() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(TxSeparateAndSameMongoClientChange.class, Collections.emptyList())
                    )
            );

            // When - Run with TX_SHARED scenarios
            assertDoesNotThrow(() -> {
                MongoSyncTargetSystem sharedTargetSystem = new MongoSyncTargetSystem("mongo-system")
                        .withMongoClient(sharedMongoClient) // Same MongoClient as audit storage
                        .withDatabase(database);

                testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(sharedTargetSystem)
                        .addDependency(sharedMongoClient)
                        .addDependency(database)
                        .build()
                        .run();
            });
        }

        // Then - Verify TX_SEPARATE_NO_MARKER txType and complete field persistence using unified approach
        auditHelper.verifyAuditSequenceStrict(
                STARTED("tx-separate-no-marker")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSeparateAndSameMongoClientChange.class)
                        .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                        .withTargetSystemId("mongo-system"),
                EXECUTED("tx-separate-no-marker")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSeparateAndSameMongoClientChange.class)
                        .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                        .withTargetSystemId("mongo-system")
        );
    }

    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER txType for different MongoClient scenario")
    void testTxSeparateNoMarkerScenario() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(TxSeparateChange.class, Collections.emptyList())
                    )
            );

            // When - Run with TX_SEPARATE_NO_MARKER scenario
            assertDoesNotThrow(() -> {
                MongoDatabase separateDatabase = separateMongoClient.getDatabase("test");
                MongoSyncTargetSystem separateTargetSystem = new MongoSyncTargetSystem("tx-separate-system")
                        .withMongoClient(separateMongoClient) // Different MongoClient from audit storage
                        .withDatabase(separateDatabase);

                testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(separateTargetSystem)
                        .addDependency(sharedMongoClient)
                        .addDependency(database)
                        .build()
                        .run();
            });
        }

        // Then - Verify TX_SEPARATE_NO_MARKER txType and complete field persistence using unified approach
        auditHelper.verifyAuditSequenceStrict(
                STARTED("tx-separate-no-marker")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSeparateChange.class)
                        .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                        .withTargetSystemId("tx-separate-system"),
                EXECUTED("tx-separate-no-marker")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSeparateChange.class)
                        .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                        .withTargetSystemId("tx-separate-system")
        );
    }

    @Test
    @DisplayName("Should persist correct targetSystemId for different target system configurations")
    void testTargetSystemIdVariations() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(TxSharedDefaultChange.class, Collections.emptyList()),
                            new CodeChangeUnitTestDefinition(NonTxTargetSystemChange.class, Collections.emptyList()),
                            new CodeChangeUnitTestDefinition(TxSeparateChange.class, Collections.emptyList())
                    )
            );

            // When - Run with multiple target system configurations
            assertDoesNotThrow(() -> {
                MongoDatabase separateDatabase = separateMongoClient.getDatabase("test");
                testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(new DefaultTargetSystem("non-tx-system"))
                        .addTargetSystem(new MongoSyncTargetSystem("tx-separate-system")
                                .withMongoClient(separateMongoClient)
                                .withDatabase(separateDatabase))
                        .addDependency(sharedMongoClient)
                        .addDependency(database)
                        .build()
                        .run();
            });
        }

        // Then - Verify each change has correct targetSystemId using unified approach
        auditHelper.verifyAuditSequenceStrict(

                // TxSharedDefaultChange - STARTED & EXECUTED with default target system
                STARTED("tx-shared-default").withTargetSystemId("default-audit-store-target-system"),
                EXECUTED("tx-shared-default").withTargetSystemId("default-audit-store-target-system"),

                // NonTxTargetSystemChange - STARTED & EXECUTED
                STARTED("non-tx-target-system").withTargetSystemId("non-tx-system"),
                EXECUTED("non-tx-target-system").withTargetSystemId("non-tx-system"),

                // TxSeparateChange - STARTED & EXECUTED with separate target system
                STARTED("tx-separate-no-marker").withTargetSystemId("tx-separate-system"),
                EXECUTED("tx-separate-no-marker").withTargetSystemId("tx-separate-system")
        );
    }

    @Test
    @DisplayName("Should persist multiple changes with different txType configurations correctly")
    void testMultipleChangesWithDifferentConfigurations() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.emptyList()),
                            new CodeChangeUnitTestDefinition(TxSharedDefaultChange.class, Collections.emptyList()),
                            new CodeChangeUnitTestDefinition(TxSeparateChange.class, Collections.emptyList())
                    )
            );

            // When - Run comprehensive txType test
            assertDoesNotThrow(() -> {
                MongoDatabase separateDatabase = separateMongoClient.getDatabase("test");
                testKit.createBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(new MongoSyncTargetSystem("tx-separate-system")
                                .withMongoClient(separateMongoClient)
                                .withDatabase(separateDatabase))
                        .addDependency(sharedMongoClient)
                        .addDependency(database)
                        .build()
                        .run();
            });
        }

        // Then - Verify each change has correct txType and all fields are persisted correctly
        List<AuditEntry> auditEntries = auditHelper.getAuditEntriesSorted();
        assertEquals(6, auditEntries.size()); // 3 changes × 2 states

        // Use comprehensive assertions for systematic verification
        AuditEntryAssertions.assertAuditSequence(auditEntries,
                // NonTxTransactionalFalseChange - STARTED & EXECUTED
                STARTED("non-tx-transactional-false").withTxType(AuditTxType.NON_TX),
                EXECUTED("non-tx-transactional-false").withTxType(AuditTxType.NON_TX),

                // TxSharedDefaultChange - STARTED & EXECUTED
                STARTED("tx-shared-default").withTxType(AuditTxType.TX_SHARED),
                EXECUTED("tx-shared-default").withTxType(AuditTxType.TX_SHARED),

                // TxSeparateChange - STARTED & EXECUTED
                STARTED("tx-separate-no-marker").withTxType(AuditTxType.TX_SEPARATE_NO_MARKER),
                EXECUTED("tx-separate-no-marker").withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
        );
    }
}