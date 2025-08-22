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
package io.flamingock.community.dynamodb;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import io.flamingock.community.dynamodb.changes.audit.NonTxTargetSystemChange;
import io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange;
import io.flamingock.community.dynamodb.changes.audit.TxSeparateAndSameMongoClientChange;
import io.flamingock.community.dynamodb.changes.audit.TxSharedDefaultChange;
import io.flamingock.core.kit.audit.AuditEntryAssertions;
import io.flamingock.core.kit.audit.AuditEntryExpectation;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.community.Constants;
import io.flamingock.internal.core.targets.DefaultTargetSystem;
import io.flamingock.internal.util.Trio;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.auditEntry;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class DynamoDBAuditPersistenceE2ETest {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBAuditPersistenceE2ETest.class);

    @Container
    static final GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDbClient sharedDynamoDbClient;
    private DynamoDbClient separateDynamoDbClient;
    private DynamoDBTestHelper dynamoDBTestHelper;

    @BeforeEach
    void setUp() {
        logger.info("Setting up DynamoDB clients for container...");
        
        // Create shared DynamoDbClient for audit storage and TX_SHARED scenarios
        sharedDynamoDbClient = DynamoDBTestContainer.createClient(dynamoDBContainer);
        
        // Create separate DynamoDbClient for TX_SEPARATE_NO_MARKER scenarios
        // Note: In TestContainers, both clients connect to the same container instance
        separateDynamoDbClient = DynamoDBTestContainer.createClient(dynamoDBContainer);
        
        // Initialize test helper with DynamoDB persistence
        dynamoDBTestHelper = DynamoDBTestContainer.createTestHelper(sharedDynamoDbClient);
        
        // Clean up any existing tables to ensure test isolation
        cleanupTables();
    }
    
    private void cleanupTables() {
        try {
            // List and delete all tables to ensure clean state for each test
            List<String> tableNames = sharedDynamoDbClient.listTables().tableNames();
            for (String tableName : tableNames) {
                sharedDynamoDbClient.deleteTable(builder -> builder.tableName(tableName));
                // Wait for table deletion to complete
                sharedDynamoDbClient.waiter().waitUntilTableNotExists(builder -> builder.tableName(tableName));
            }
        } catch (Exception e) {
            logger.warn("Error cleaning up tables: {}", e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        if (sharedDynamoDbClient != null) {
            sharedDynamoDbClient.close();
        }
        if (separateDynamoDbClient != null) {
            separateDynamoDbClient.close();
        }
    }

    @Test
    @DisplayName("Should persist all audit entry fields correctly in DynamoDB")
    void testCompleteAuditEntryPersistenceInDynamoDB() {
        // Given
        String changeId = "non-tx-transactional-false";
        LocalDateTime testStart = LocalDateTime.now();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new Trio<>(NonTxTransactionalFalseChange.class, Collections.singletonList(DynamoDbClient.class), null)
                    )
            );

            // When - Run Flamingock with DynamoDB persistence
            assertDoesNotThrow(() -> {
                FlamingockFactory.getCommunityBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addDependency(sharedDynamoDbClient)
                        .build()
                        .run();
            });
        }

        LocalDateTime testEnd = LocalDateTime.now();

        // Then - Verify complete audit persistence in DynamoDB
        List<AuditEntry> auditEntries = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(2, auditEntries.size());

        // Verify STARTED entry with all fields
        AuditEntryExpectation startedExpected = auditEntry()
                .withTaskId(changeId)
                .withState(AuditEntry.Status.STARTED)
                .withType(AuditEntry.ExecutionType.EXECUTION)
                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange")
                .withMethodName("execution")
                .withTxType(AuditTxType.NON_TX)
                .withTargetSystemId("default-audit-store-target-system")
                .withSystemChange(false)
                .withTimestampBetween(testStart, testEnd);

        AuditEntryAssertions.assertAuditEntry(auditEntries.get(0), startedExpected);

        // Verify EXECUTED entry with all fields
        AuditEntryExpectation executedExpected = auditEntry()
                .withTaskId(changeId)
                .withState(AuditEntry.Status.EXECUTED)
                .withType(AuditEntry.ExecutionType.EXECUTION)
                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange")
                .withMethodName("execution")
                .withTxType(AuditTxType.NON_TX)
                .withTargetSystemId("default-audit-store-target-system")
                .withSystemChange(false)
                .withTimestampBetween(testStart, testEnd);

        AuditEntryAssertions.assertAuditEntry(auditEntries.get(1), executedExpected);

        // Verify DynamoDB-specific persistence completeness
        for (AuditEntry entry : auditEntries) {
            AuditEntryAssertions.assertAuditEntryCompleteness(entry);
        }

        // Verify same execution relationship
        AuditEntryAssertions.assertSameExecution(auditEntries);
    }

    @Test
    @DisplayName("Should persist NON_TX txType for transactional=false scenarios")
    void testNonTxScenarios() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new Trio<>(NonTxTransactionalFalseChange.class, Collections.singletonList(DynamoDbClient.class), null),
                            new Trio<>(NonTxTargetSystemChange.class, Collections.singletonList(DynamoDbClient.class), null)
                    )
            );

            // When - Run with NON_TX scenarios
            assertDoesNotThrow(() -> {
                FlamingockFactory.getCommunityBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(new DefaultTargetSystem("non-tx-system")) // Non-transactional target system
                        .addDependency(sharedDynamoDbClient)
                        .build()
                        .run();
            });
        }

        // Then - Verify NON_TX txType and complete field persistence
        List<AuditEntry> auditEntries = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(4, auditEntries.size()); // 2 changes × 2 states

        // Verify first change (transactional=false)
        List<AuditEntry> change1Entries = auditEntries.stream()
                .filter(entry -> "non-tx-transactional-false".equals(entry.getTaskId()))
                .collect(Collectors.toList());
        assertEquals(2, change1Entries.size());

        for (AuditEntry entry : change1Entries) {
            AuditEntryAssertions.assertTransactionFields(entry,
                    AuditTxType.NON_TX,
                    "default-audit-store-target-system");
            AuditEntryAssertions.assertExecutionFields(entry,
                    "io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange",
                    "execution",
                    AuditEntry.ExecutionType.EXECUTION);
        }

        // Verify second change (DefaultTargetSystem)
        List<AuditEntry> change2Entries = auditEntries.stream()
                .filter(entry -> "non-tx-target-system".equals(entry.getTaskId()))
                .collect(Collectors.toList());
        assertEquals(2, change2Entries.size());

        for (AuditEntry entry : change2Entries) {
            AuditEntryAssertions.assertTransactionFields(entry,
                    AuditTxType.NON_TX,
                    "non-tx-system");
            AuditEntryAssertions.assertExecutionFields(entry,
                    "io.flamingock.community.dynamodb.changes.audit.NonTxTargetSystemChange",
                    "execution",
                    AuditEntry.ExecutionType.EXECUTION);
        }
    }

    @Test
    @DisplayName("Should persist TX_SHARED txType for default and explicit same DynamoDbClient scenarios")
    void testTxSharedScenarios() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new Trio<>(TxSharedDefaultChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class), null))
            );

            // When - Run with TX_SHARED scenarios
            assertDoesNotThrow(() -> {
                DynamoDBTargetSystem sharedTargetSystem = new DynamoDBTargetSystem("tx-shared-system")
                        .withDynamoDBClient(sharedDynamoDbClient); // Same DynamoDbClient as audit storage

                FlamingockFactory.getCommunityBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(sharedTargetSystem)
                        .addDependency(sharedDynamoDbClient)
                        .build()
                        .run();
            });
        }

        // Then - Verify TX_SHARED txType and complete field persistence
        List<AuditEntry> auditEntries = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(2, auditEntries.size()); // 2 changes × 2 states

        // Verify first change (default TX_SHARED)
        List<AuditEntry> change1Entries = auditEntries.stream()
                .filter(entry -> "tx-shared-default".equals(entry.getTaskId()))
                .collect(Collectors.toList());
        assertEquals(2, change1Entries.size());

        for (AuditEntry entry : change1Entries) {
            AuditEntryAssertions.assertTransactionFields(entry,
                    AuditTxType.TX_SHARED,
                    "default-audit-store-target-system");
            AuditEntryAssertions.assertExecutionFields(entry,
                    "io.flamingock.community.dynamodb.changes.audit.TxSharedDefaultChange",
                    "execution",
                    AuditEntry.ExecutionType.EXECUTION);
        }

    }

    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER when targetSystem defined and different from auditStore")
    void testTxNoMarkerWhenSameMongoClientButDifferentTargetSystem() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new Trio<>(TxSeparateAndSameMongoClientChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class), null)
                    )
            );

            // When - Run with multiple target system configurations
            assertDoesNotThrow(() -> {
                FlamingockFactory.getCommunityBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(new DynamoDBTargetSystem("mongo-system")
                                .withDynamoDBClient(separateDynamoDbClient))
                        .addDependency(sharedDynamoDbClient)
                        .build()
                        .run();
            });
        }

        // Then - Verify each change has correct targetSystemId
        List<AuditEntry> auditEntries = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(2, auditEntries.size()); // 3 changes × 2 states

        // Verify separate target system
        List<AuditEntry> separateEntries = auditEntries.stream()
                .filter(entry -> "tx-separate-no-marker".equals(entry.getTaskId()))
                .collect(Collectors.toList());
        for (AuditEntry entry : separateEntries) {
            assertEquals("mongo-system", entry.getTargetSystemId());
        }
    }


    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER txType for different DynamoDbClient scenario")
    void testTxSeparateNoMarkerScenario() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new Trio<>(TxSeparateAndSameMongoClientChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class), null)
                    )
            );

            // When - Run with TX_SEPARATE_NO_MARKER scenario
            assertDoesNotThrow(() -> {
                DynamoDBTargetSystem separateTargetSystem = new DynamoDBTargetSystem("mongo-system")
                        .withDynamoDBClient(separateDynamoDbClient); // Different DynamoDbClient from audit storage

                FlamingockFactory.getCommunityBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(separateTargetSystem)
                        .addDependency(sharedDynamoDbClient)
                        .build()
                        .run();
            });
        }

        // Then - Verify TX_SEPARATE_NO_MARKER txType and complete field persistence
        List<AuditEntry> auditEntries = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(2, auditEntries.size());

        for (AuditEntry entry : auditEntries) {
            AuditEntryAssertions.assertTransactionFields(entry,
                    AuditTxType.TX_SEPARATE_NO_MARKER,
                    "mongo-system");
            AuditEntryAssertions.assertExecutionFields(entry,
                    "io.flamingock.community.dynamodb.changes.audit.TxSeparateAndSameMongoClientChange",
                    "execution",
                    AuditEntry.ExecutionType.EXECUTION);
            AuditEntryAssertions.assertBasicFields(entry,
                    "tx-separate-no-marker",
                    "test-author",
                    entry.getState());
        }
    }

    @Test
    @DisplayName("Should persist correct targetSystemId for different target system configurations")
    void testTargetSystemIdVariations() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new Trio<>(TxSharedDefaultChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class), null),
                            new Trio<>(NonTxTargetSystemChange.class, Collections.singletonList(DynamoDbClient.class), null),
                            new Trio<>(TxSeparateAndSameMongoClientChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class), null)
                    )
            );

            // When - Run with multiple target system configurations
            assertDoesNotThrow(() -> {
                FlamingockFactory.getCommunityBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(new DefaultTargetSystem("non-tx-system"))
                        .addTargetSystem(new DynamoDBTargetSystem("mongo-system")
                                .withDynamoDBClient(separateDynamoDbClient))
                        .addDependency(sharedDynamoDbClient)
                        .build()
                        .run();
            });
        }

        // Then - Verify each change has correct targetSystemId
        List<AuditEntry> auditEntries = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(6, auditEntries.size()); // 3 changes × 2 states

        // Verify default target system
        List<AuditEntry> defaultEntries = auditEntries.stream()
                .filter(entry -> "tx-shared-default".equals(entry.getTaskId()))
                .collect(Collectors.toList());
        for (AuditEntry entry : defaultEntries) {
            assertEquals("default-audit-store-target-system", entry.getTargetSystemId());
        }

        // Verify custom target system
        List<AuditEntry> customEntries = auditEntries.stream()
                .filter(entry -> "non-tx-target-system".equals(entry.getTaskId()))
                .collect(Collectors.toList());
        for (AuditEntry entry : customEntries) {
            assertEquals("non-tx-system", entry.getTargetSystemId());
        }

        // Verify separate target system
        List<AuditEntry> separateEntries = auditEntries.stream()
                .filter(entry -> "tx-separate-no-marker".equals(entry.getTaskId()))
                .collect(Collectors.toList());
        for (AuditEntry entry : separateEntries) {
            assertEquals("mongo-system", entry.getTargetSystemId());
        }
    }


    @Test
    @DisplayName("Should persist multiple changes with different txType configurations correctly")
    void testMultipleChangesWithDifferentConfigurations() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new Trio<>(NonTxTransactionalFalseChange.class, Collections.singletonList(DynamoDbClient.class), null),
                            new Trio<>(TxSharedDefaultChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class), null),
                            new Trio<>(TxSeparateAndSameMongoClientChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class), null)
                    )
            );

            // When - Run comprehensive txType test
            assertDoesNotThrow(() -> {
                FlamingockFactory.getCommunityBuilder()
                        .setRelaxTargetSystemValidation(true)
                        .addTargetSystem(new DynamoDBTargetSystem("mongo-system")
                                .withDynamoDBClient(separateDynamoDbClient))
                        .addDependency(sharedDynamoDbClient)
                        .build()
                        .run();
            });
        }

        // Then - Verify each change has correct txType and all fields are persisted correctly
        List<AuditEntry> auditEntries = dynamoDBTestHelper.getAuditEntriesSorted(Constants.DEFAULT_AUDIT_STORE_NAME);
        assertEquals(6, auditEntries.size()); // 3 changes × 2 states

        // Use comprehensive assertions for systematic verification
        AuditEntryAssertions.assertAuditSequence(auditEntries,
                // NonTxTransactionalFalseChange - STARTED & EXECUTED
                auditEntry().withTaskId("non-tx-transactional-false").withState(AuditEntry.Status.STARTED).withTxType(AuditTxType.NON_TX),
                auditEntry().withTaskId("non-tx-transactional-false").withState(AuditEntry.Status.EXECUTED).withTxType(AuditTxType.NON_TX),

                // TxSharedDefaultChange - STARTED & EXECUTED
                auditEntry().withTaskId("tx-shared-default").withState(AuditEntry.Status.STARTED).withTxType(AuditTxType.TX_SHARED),
                auditEntry().withTaskId("tx-shared-default").withState(AuditEntry.Status.EXECUTED).withTxType(AuditTxType.TX_SHARED),

                // TxSeparateChange - STARTED & EXECUTED
                auditEntry().withTaskId("tx-separate-no-marker").withState(AuditEntry.Status.STARTED).withTxType(AuditTxType.TX_SEPARATE_NO_MARKER),
                auditEntry().withTaskId("tx-separate-no-marker").withState(AuditEntry.Status.EXECUTED).withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
        );

        // Verify each entry has complete audit field persistence
        for (AuditEntry entry : auditEntries) {
            AuditEntryAssertions.assertAuditEntryCompleteness(entry);
        }
    }
}