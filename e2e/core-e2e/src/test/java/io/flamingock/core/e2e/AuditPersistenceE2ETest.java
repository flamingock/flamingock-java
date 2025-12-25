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
package io.flamingock.core.e2e;

import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.common.test.pipeline.PipelineTestHelper;
import io.flamingock.core.e2e.changes._002__CustomTargetSystemChange;
import io.flamingock.core.e2e.changes._001__SimpleNonTransactionalChange;
import io.flamingock.core.kit.audit.AuditEntryExpectation;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.core.kit.inmemory.InternalInMemoryTestKit;
import io.flamingock.internal.common.core.util.Deserializer;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.targetsystem.nontransactional.NonTransactionalTargetSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Comprehensive tests for audit entry persistence across core Flamingock functionality.
 *
 * <p>This test class focuses on verifying that ALL audit entry fields are correctly
 * persisted and retrieved from storage. It covers basic core scenarios using in-memory
 * storage before more complex database-specific tests in community modules.</p>
 *
 * <p>Key test areas:</p>
 * <ul>
 *   <li>Complete audit field verification (all AuditEntry fields)</li>
 *   <li>Default vs custom target system ID persistence</li>
 *   <li>Basic transaction type scenarios</li>
 *   <li>Audit entry completeness and correctness</li>
 * </ul>
 */
class AuditPersistenceE2ETest {

    private InternalInMemoryTestKit testKit;
    private AuditTestHelper auditHelper;

    @BeforeEach
    void setUp() {
        testKit = InternalInMemoryTestKit.create();
        auditHelper = testKit.getAuditHelper();
    }

    @Test
    @DisplayName("Should persist all audit entry fields correctly for basic execution")
    void testCompleteAuditFieldPersistence() {
        // Given
        String changeId = "test1-non-tx-change";
        LocalDateTime testStart = LocalDateTime.now();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_001__SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new NonTransactionalTargetSystem("kafka"))
                        .build()
                        .run();
            });
        }

        LocalDateTime testEnd = LocalDateTime.now();

        // Then - Verify complete audit sequence with all fields using unified approach
        AuditEntryExpectation startedExpected = STARTED(changeId)
                .withAuthor("aperezdieppa")
                .withType(AuditEntry.ExecutionType.EXECUTION)
                .withClass(io.flamingock.core.e2e.changes._001__SimpleNonTransactionalChange.class)
                .withTxType(AuditTxType.NON_TX)
                .withTargetSystemId("kafka")
                .withSystemChange(false)
                .withTimestampBetween(testStart, testEnd);

        AuditEntryExpectation appliedExpected = APPLIED(changeId)
                .withAuthor("aperezdieppa")
                .withType(AuditEntry.ExecutionType.EXECUTION)
                .withClass(io.flamingock.core.e2e.changes._001__SimpleNonTransactionalChange.class)
                .withTxType(AuditTxType.NON_TX)
                .withTargetSystemId("kafka")
                .withSystemChange(false)
                .withTimestampBetween(testStart, testEnd);

        auditHelper.verifyAuditSequenceStrict(startedExpected, appliedExpected);
    }

    @Test
    @DisplayName("Should persist default targetSystemId in audit entries")
    void testDefaultTargetSystemIdPersistence() {
        // Given
        String changeId = "test1-non-tx-change";

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_001__SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock (should execute successfully and audit with default target system)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new NonTransactionalTargetSystem("kafka"))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit entries have correct default targetSystemId using unified approach
        auditHelper.verifyAuditSequenceStrict(
                STARTED(changeId).withTxType(AuditTxType.NON_TX).withTargetSystemId("kafka"),
                APPLIED(changeId).withTxType(AuditTxType.NON_TX).withTargetSystemId("kafka")
        );
    }

    @Test
    @DisplayName("Should persist custom targetSystemId in audit entries")
    void testCustomTargetSystemIdPersistence() {
        // Given
        String customTargetSystemId = "custom-target-system";
        String changeId = "test-custom-target-change";

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_002__CustomTargetSystemChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock with custom target system added to builder
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new NonTransactionalTargetSystem("kafka"))
                        .addTargetSystem(new NonTransactionalTargetSystem(customTargetSystemId))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit entries have correct custom targetSystemId using unified approach
        auditHelper.verifyAuditSequenceStrict(
                STARTED(changeId).withTxType(AuditTxType.NON_TX).withTargetSystemId(customTargetSystemId),
                APPLIED(changeId).withTxType(AuditTxType.NON_TX).withTargetSystemId(customTargetSystemId)
        );
    }

    @Test
    @DisplayName("Should persist correct basic audit fields for different change types")
    void testBasicAuditFieldVariations() {
        // Given - Multiple changes with different characteristics
        String changeId1 = "test1-non-tx-change";
        String changeId2 = "test-custom-target-change";

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_001__SimpleNonTransactionalChange.class, Collections.emptyList()),
                            new CodeChangeTestDefinition(_002__CustomTargetSystemChange.class, Collections.emptyList())
                    )
            );

            // When - Run multiple changes
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new NonTransactionalTargetSystem("kafka"))
                        .addTargetSystem(new NonTransactionalTargetSystem("custom-target-system"))
                        .build()
                        .run();
            });
        }


        // Then - Verify each change has correct audit characteristics using unified approach

        List<AuditEntry> auditEntriesSorted = auditHelper.getAuditEntriesSorted();
        auditEntriesSorted.forEach(c-> System.out.println("id: " + c.getTaskId() + ", state: " + c.getState() + ", time: " +c.getCreatedAt()));

        auditHelper.verifyAuditSequenceStrict(
                // First change (SimpleNonTransactionalChange) - STARTED & APPLIED
                STARTED(changeId1)
                        .withAuthor("aperezdieppa")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.core.e2e.changes._001__SimpleNonTransactionalChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("kafka"),
                APPLIED(changeId1)
                        .withAuthor("aperezdieppa")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.core.e2e.changes._001__SimpleNonTransactionalChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("kafka"),

                // Second change (CustomTargetSystemChange) - STARTED & APPLIED
                STARTED(changeId2)
                        .withAuthor("aperezdieppa")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.core.e2e.changes._002__CustomTargetSystemChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("custom-target-system"),
                APPLIED(changeId2)
                        .withAuthor("aperezdieppa")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(io.flamingock.core.e2e.changes._002__CustomTargetSystemChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("custom-target-system")
        );

    }

    @Test
    @DisplayName("DEMO: New fluent API for simple audit verification")
    void testNewFluentAPIDemo() {
        String changeId = "test1-non-tx-change";
        
        // Using the new AuditTestSupport API - this replaces all the MockedStatic boilerplate!
        AuditTestSupport.withTestKit(testKit)
            
            .GIVEN_Changes(
                new CodeChangeTestDefinition(_001__SimpleNonTransactionalChange.class, Collections.emptyList())
            )
            .WHEN(() -> {
                // The actual test execution code - no more MockedStatic management needed!
                assertDoesNotThrow(() -> {
                    testKit.createBuilder()
                            .addTargetSystem(new NonTransactionalTargetSystem("kafka"))
                            .build()
                            .run();
                });
            })
            .THEN_VerifyAuditSequenceStrict(
                STARTED(changeId),
                APPLIED(changeId)
            )
            .run();
    }
    
    @Test
    @DisplayName("COMPARISON: Before vs After - Multiple changes with new API") 
    void testMultipleChangesWithNewAPI() {
        String changeId1 = "test1-non-tx-change";
        String changeId2 = "test-custom-target-change";
        
        // NEW WAY: Clean, readable, no boilerplate
        AuditTestSupport.withTestKit(testKit)
            
            .GIVEN_Changes(
                new CodeChangeTestDefinition(_001__SimpleNonTransactionalChange.class, Collections.emptyList()),
                new CodeChangeTestDefinition(_002__CustomTargetSystemChange.class, Collections.emptyList())
            )
            .WHEN(() -> {
                assertDoesNotThrow(() -> {
                    testKit.createBuilder()
                            .addTargetSystem(new NonTransactionalTargetSystem("kafka"))
                            .addTargetSystem(new NonTransactionalTargetSystem("custom-target-system"))
                            .build()
                            .run();
                });
            })
            .THEN_VerifyAuditSequenceStrict(
                // First change sequence
                STARTED(changeId1)
                        .withAuthor("aperezdieppa")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(_001__SimpleNonTransactionalChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("kafka"),
                APPLIED(changeId1)
                        .withAuthor("aperezdieppa")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(_001__SimpleNonTransactionalChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("kafka"),
                
                // Second change sequence  
                STARTED(changeId2)
                        .withAuthor("aperezdieppa")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(_002__CustomTargetSystemChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("custom-target-system"),
                APPLIED(changeId2)
                        .withAuthor("aperezdieppa")
                        .withType(AuditEntry.ExecutionType.EXECUTION)
                        .withClass(_002__CustomTargetSystemChange.class)
                        .withTxType(AuditTxType.NON_TX)
                        .withTargetSystemId("custom-target-system")
            )
            .run();
    }
}
