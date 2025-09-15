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

import io.flamingock.common.test.pipeline.CodeChangeUnitTestDefinition;
import io.flamingock.common.test.pipeline.PipelineTestHelper;
import io.flamingock.core.e2e.changes.AlwaysRetryNonTransactionalChange;
import io.flamingock.core.e2e.changes.ManualInterventionNonTransactionalChange;
import io.flamingock.core.e2e.changes.SimpleNonTransactionalChange;
import io.flamingock.core.kit.audit.AuditEntryExpectation;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.inmemory.InMemoryTestKit;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.targets.DefaultTargetSystem;
import io.flamingock.internal.common.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.FAILED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.ROLLBACK_FAILED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.ROLLED_BACK;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for Flamingock recovery scenarios.
 *
 * <p>This test suite verifies that Flamingock correctly handles various audit states
 * and responds appropriately to recovery situations. Each test follows the pattern:</p>
 * <ol>
 *   <li>Pre-insert audit entry with specific state and txType</li>
 *   <li>Run Flamingock and check for expected exception</li>
 *   <li>Validate audit log state after recovery attempt</li>
 * </ol>
 */
class RecoveryE2ETest {

    private InMemoryTestKit testKit;
    private AuditTestHelper auditHelper;

    @BeforeEach
    void setUp() {
        testKit = InMemoryTestKit.create();
        auditHelper = testKit.getAuditHelper();
    }

    @Test
    @DisplayName("Should require manual intervention for STARTED NON_TX change")
    void testStartedNonTxRequiresManualIntervention() {
        String changeId = "test1-non-tx-change";
        testForManualInterventionException(changeId,
                AuditEntry.Status.STARTED,
                AuditTxType.NON_TX,
                SimpleNonTransactionalChange.class,
                STARTED(changeId));
    }

    @Test
    @DisplayName("Should require manual intervention for EXECUTION_FAILED NON_TX change")
    void testExecutionFailedNonTxRequiresManualIntervention() {
        String changeId = "test1-non-tx-change";
        testForManualInterventionException(changeId,
                AuditEntry.Status.FAILED,
                AuditTxType.NON_TX,
                SimpleNonTransactionalChange.class,
                FAILED(changeId));
    }

    @Test
    @DisplayName("Should execute successfully for EXECUTION_FAILED TX_SEPARATE_NO_MARKER change")
    void testExecutionFailedTxSeparateNoMarkerSuccessfulExecution() {
        String changeId = "test1-non-tx-change";
        testForRetry(changeId,
                AuditEntry.Status.FAILED,
                AuditTxType.TX_SEPARATE_NO_MARKER,
                SimpleNonTransactionalChange.class,
                FAILED(changeId), STARTED(changeId), APPLIED(changeId));
    }

    @Test
    @DisplayName("Should execute successfully for EXECUTION_FAILED TX_SHARED change")
    void testExecutionFailedTxSharedSuccessfulExecution() {
        // Given - Pre-insert audit entry with EXECUTION_FAILED state and TX_SHARED type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.FAILED, AuditTxType.TX_SHARED, SimpleNonTransactionalChange.class);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock (should execute successfully without throwing exception)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new DefaultTargetSystem("keycloak"))
                        .addTargetSystem(new DefaultTargetSystem("kafka"))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit log shows successful execution after recovery
        auditHelper.verifyAuditSequenceStrict(
                FAILED(changeId),
                STARTED(changeId),
                APPLIED(changeId)
        );
    }

    @Test
    @DisplayName("Should execute successfully for ROLLED_BACK NON_TX change")
    void testRolledBackNonTxSuccessfulExecution() {
        // Given - Pre-insert audit entry with ROLLED_BACK state and NON_TX type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.ROLLED_BACK, AuditTxType.NON_TX, SimpleNonTransactionalChange.class);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock (should execute successfully without throwing exception)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new DefaultTargetSystem("keycloak"))
                        .addTargetSystem(new DefaultTargetSystem("kafka"))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit log shows successful execution after rollback
        auditHelper.verifyAuditSequenceStrict(
                ROLLED_BACK(changeId),
                STARTED(changeId),
                APPLIED(changeId)
        );
    }

    @Test
    @DisplayName("Should execute successfully for ROLLED_BACK TX_SEPARATE_NO_MARKER change")
    void testRolledBackTxSeparateNoMarkerSuccessfulExecution() {
        // Given - Pre-insert audit entry with ROLLED_BACK state and TX_SEPARATE_NO_MARKER type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.ROLLED_BACK, AuditTxType.TX_SEPARATE_NO_MARKER, SimpleNonTransactionalChange.class);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock (should execute successfully without throwing exception)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new DefaultTargetSystem("kafka"))
                        .addTargetSystem(new DefaultTargetSystem("sendgrid"))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit log shows successful execution after rollback
        auditHelper.verifyAuditSequenceStrict(
                ROLLED_BACK(changeId),
                STARTED(changeId),
                APPLIED(changeId)
        );
    }

    @Test
    @DisplayName("Should execute successfully for ROLLED_BACK TX_SHARED change")
    void testRolledBackTxSharedSuccessfulExecution() {
        // Given - Pre-insert audit entry with ROLLED_BACK state and TX_SHARED type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.ROLLED_BACK, AuditTxType.TX_SHARED, SimpleNonTransactionalChange.class);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock (should execute successfully without throwing exception)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new DefaultTargetSystem("keycloak"))
                        .addTargetSystem(new DefaultTargetSystem("kafka"))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit log shows successful execution after rollback
        auditHelper.verifyAuditSequenceStrict(
                ROLLED_BACK(changeId),
                STARTED(changeId),
                APPLIED(changeId)
        );
    }

    @Test
    @DisplayName("Should require manual intervention for ROLLBACK_FAILED NON_TX change")
    void testRollbackFailedNonTxRequiresManualIntervention() {
        // Given - Pre-insert audit entry with ROLLBACK_FAILED state and NON_TX type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.ROLLBACK_FAILED, AuditTxType.NON_TX, SimpleNonTransactionalChange.class);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            ManualInterventionRequiredException exception = assertThrows(
                    ManualInterventionRequiredException.class,
                    () -> {
                        testKit.createBuilder()
                                .addTargetSystem(new DefaultTargetSystem("kafka"))
                        .addTargetSystem(new DefaultTargetSystem("sendgrid"))
                                .build()
                                .run();
                    }
            );

            // Then - Verify exception details
            assertNotNull(exception.getConflictingChanges());
            assertEquals(1, exception.getConflictingChanges().size());

            RecoveryIssue recoveryIssue = exception.getConflictingChanges().get(0);
            assertEquals(changeId, recoveryIssue.getChangeId());

            // Verify exception message contains expected recovery guidance
            String message = exception.getMessage();
            assertTrue(message.contains("MANUAL INTERVENTION REQUIRED"),
                    "Exception should indicate manual intervention is required");
            assertTrue(message.contains(changeId),
                    "Exception message should mention the specific change ID");
            assertTrue(message.contains("flamingock mark"),
                    "Exception should provide CLI command guidance");
        }

        // Then - Verify audit log remains unchanged (only the pre-inserted ROLLBACK_FAILED entry)
        auditHelper.verifyAuditSequenceStrict(
                ROLLBACK_FAILED(changeId)
        );
    }

    @Test
    @DisplayName("Should do nothing for APPLIED NON_TX change")
    void testExecutedNonTxDoNothing() {
        // Given - Pre-insert audit entry with APPLIED state and NON_TX type
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.APPLIED, AuditTxType.NON_TX, SimpleNonTransactionalChange.class);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock (should execute successfully without throwing exception)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new DefaultTargetSystem("keycloak"))
                        .addTargetSystem(new DefaultTargetSystem("kafka"))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit log remains unchanged (only the pre-inserted APPLIED entry)
        auditHelper.verifyAuditSequenceStrict(
                APPLIED(changeId)
        );
    }

    @Test
    @DisplayName("Should do nothing for APPLIED TX_SEPARATE_NO_MARKER change")
    void testExecutedTxSeparateNoMarkerDoNothing() {
        // Given - Pre-insert audit entry with APPLIED state and TX_SEPARATE_NO_MARKER type
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.APPLIED, AuditTxType.TX_SEPARATE_NO_MARKER, SimpleNonTransactionalChange.class);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock (should execute successfully without throwing exception)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new DefaultTargetSystem("kafka"))
                        .addTargetSystem(new DefaultTargetSystem("sendgrid"))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit log remains unchanged (only the pre-inserted APPLIED entry)
        auditHelper.verifyAuditSequenceStrict(
                APPLIED(changeId)
        );
    }

    @Test
    @DisplayName("Should do nothing for APPLIED TX_SHARED change")
    void testExecutedTxSharedDoNothing() {
        // Given - Pre-insert audit entry with APPLIED state and TX_SHARED type
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.APPLIED, AuditTxType.TX_SHARED, SimpleNonTransactionalChange.class);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Run Flamingock (should execute successfully without throwing exception)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new DefaultTargetSystem("kafka"))
                        .addTargetSystem(new DefaultTargetSystem("sendgrid"))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit log remains unchanged (only the pre-inserted APPLIED entry)
        auditHelper.verifyAuditSequenceStrict(
                APPLIED(changeId)
        );
    }

    // =================================
    // ALWAYS_RETRY Recovery Strategy Tests
    // =================================

    @Test
    @DisplayName("Should execute successfully for STARTED NON_TX change with ALWAYS_RETRY strategy")
    void testStartedNonTxWithAlwaysRetrySuccessfulExecution() {
        String changeId = "always-retry-non-tx-change";
        testForRetry(changeId,
                AuditEntry.Status.STARTED,
                AuditTxType.NON_TX,
                AlwaysRetryNonTransactionalChange.class,
                STARTED(changeId), STARTED(changeId), APPLIED(changeId));
    }

    @Test
    @DisplayName("Should execute successfully for EXECUTION_FAILED NON_TX change with ALWAYS_RETRY strategy")
    void testExecutionFailedNonTxWithAlwaysRetrySuccessfulExecution() {
        String changeId = "always-retry-non-tx-change";
        testForRetry(changeId,
                AuditEntry.Status.FAILED,
                AuditTxType.NON_TX,
                AlwaysRetryNonTransactionalChange.class,
                FAILED(changeId), STARTED(changeId), APPLIED(changeId));
    }

    @Test
    @DisplayName("Should execute successfully for ROLLBACK_FAILED NON_TX change with ALWAYS_RETRY strategy")
    void testRollbackFailedNonTxWithAlwaysRetrySuccessfulExecution() {
        String changeId = "always-retry-non-tx-change";
        testForRetry(changeId,
                AuditEntry.Status.ROLLBACK_FAILED,
                AuditTxType.NON_TX,
                AlwaysRetryNonTransactionalChange.class,
                ROLLBACK_FAILED(changeId), STARTED(changeId), APPLIED(changeId));
    }

    // =================================
    // Explicit MANUAL_INTERVENTION Recovery Strategy Tests
    // =================================

    @Test
    @DisplayName("Should require manual intervention for STARTED NON_TX change with explicit MANUAL_INTERVENTION strategy")
    void testStartedNonTxWithExplicitManualInterventionRequiresManualIntervention() {
        String changeId = "manual-intervention-non-tx-change";
        testForManualInterventionException(changeId,
                AuditEntry.Status.STARTED,
                AuditTxType.NON_TX,
                ManualInterventionNonTransactionalChange.class,
                STARTED(changeId));
    }

    @Test
    @DisplayName("Should require manual intervention for EXECUTION_FAILED NON_TX change with explicit MANUAL_INTERVENTION strategy")
    void testExecutionFailedNonTxWithExplicitManualInterventionRequiresManualIntervention() {
        String changeId = "manual-intervention-non-tx-change";
        testForManualInterventionException(changeId,
                AuditEntry.Status.FAILED,
                AuditTxType.NON_TX,
                ManualInterventionNonTransactionalChange.class,
                FAILED(changeId));
    }

    @Test
    @DisplayName("Should require manual intervention for ROLLBACK_FAILED NON_TX change with explicit MANUAL_INTERVENTION strategy")
    void testRollbackFailedNonTxWithExplicitManualInterventionRequiresManualIntervention() {
        String changeId = "manual-intervention-non-tx-change";
        testForManualInterventionException(changeId,
                AuditEntry.Status.ROLLBACK_FAILED,
                AuditTxType.NON_TX,
                ManualInterventionNonTransactionalChange.class,
                ROLLBACK_FAILED(changeId));
    }


    /**
     * Helper method to run a test expecting successful execution.
     */
    private void testForRetry(String changeId,
                              AuditEntry.Status previousState,
                              AuditTxType txType,
                              Class<?> changeUnitClass,
                              AuditEntryExpectation... expectedAuditSequence) {
        // Given - Pre-insert audit entry
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, previousState, txType, changeUnitClass);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(changeUnitClass, Collections.emptyList())
                    )
            );

            // When - Run Flamingock (should execute successfully without throwing exception)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                        .addTargetSystem(new DefaultTargetSystem("keycloak"))
                        .addTargetSystem(new DefaultTargetSystem("sendgrid"))
                        .addTargetSystem(new DefaultTargetSystem("kafka"))
                        .build()
                        .run();
            });
        }

        // Then - Verify audit log shows expected sequence
        auditHelper.verifyAuditSequenceStrict(expectedAuditSequence);
    }

    /**
     * Helper method to run a test expecting ManualInterventionRequiredException.
     */
    private void testForManualInterventionException(String changeId,
                                                    AuditEntry.Status previousState,
                                                    AuditTxType txType,
                                                    Class<?> changeUnitClass,
                                                    AuditEntryExpectation... expectedAuditSequence) {
        // Given - Pre-insert audit entry
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, previousState, txType, changeUnitClass);
        testKit.getAuditStorage().addAuditEntry(preExistingEntry);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeUnitTestDefinition(changeUnitClass, Collections.emptyList())
                    )
            );

            ManualInterventionRequiredException exception = assertThrows(
                    ManualInterventionRequiredException.class,
                    () -> {
                        testKit.createBuilder()
                                .addTargetSystem(new DefaultTargetSystem("keycloak"))
                        .addTargetSystem(new DefaultTargetSystem("sendgrid"))
                                .build()
                                .run();
                    }
            );

            // Then - Verify exception details
            assertNotNull(exception.getConflictingChanges());
            assertEquals(1, exception.getConflictingChanges().size());

            RecoveryIssue recoveryIssue = exception.getConflictingChanges().get(0);
            assertEquals(changeId, recoveryIssue.getChangeId());

            // Verify exception message contains expected recovery guidance
            String message = exception.getMessage();
            assertTrue(message.contains("MANUAL INTERVENTION REQUIRED"),
                    "Exception should indicate manual intervention is required");
            assertTrue(message.contains(changeId),
                    "Exception message should mention the specific change ID");
            assertTrue(message.contains("flamingock mark"),
                    "Exception should provide CLI command guidance");
        }

        // Then - Verify audit log shows expected sequence (usually just the pre-existing entry)
        auditHelper.verifyAuditSequenceStrict(expectedAuditSequence);
    }


}
