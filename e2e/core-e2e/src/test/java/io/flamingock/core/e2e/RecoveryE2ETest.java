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
import io.flamingock.core.e2e.changes.SimpleNonTransactionalChange;
import io.flamingock.core.e2e.changes.CustomTargetSystemChange;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.core.targets.DefaultTargetSystem;
import io.flamingock.core.kit.inmemory.InMemoryTestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.engine.audit.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.core.engine.audit.recovery.RecoveryIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static io.flamingock.core.kit.audit.AuditExpectation.STARTED;
import static io.flamingock.core.kit.audit.AuditExpectation.EXECUTION_FAILED;
import static io.flamingock.core.kit.audit.AuditExpectation.EXECUTED;
import static io.flamingock.core.kit.audit.AuditExpectation.ROLLED_BACK;
import static io.flamingock.core.kit.audit.AuditExpectation.ROLLBACK_FAILED;
import static org.junit.jupiter.api.Assertions.*;

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
        // Given - Pre-insert audit entry with STARTED state and NON_TX type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.STARTED, AuditTxType.NON_TX);
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
                        .setRelaxTargetSystemValidation(true)
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
        
        // Then - Verify audit log remains unchanged (only the pre-inserted STARTED entry)
        auditHelper.verifyAuditSequenceStrict(
            STARTED(changeId)
        );
    }
    
    @Test
    @DisplayName("Should require manual intervention for EXECUTION_FAILED NON_TX change")
    void testExecutionFailedNonTxRequiresManualIntervention() {
        // Given - Pre-insert audit entry with EXECUTION_FAILED state and NON_TX type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.EXECUTION_FAILED, AuditTxType.NON_TX);
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
                        .setRelaxTargetSystemValidation(true)
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
        
        // Then - Verify audit log remains unchanged (only the pre-inserted EXECUTION_FAILED entry)
        auditHelper.verifyAuditSequenceStrict(
            EXECUTION_FAILED(changeId)
        );
    }
    
    @Test
    @DisplayName("Should execute successfully for EXECUTION_FAILED TX_SEPARATE_NO_MARKER change")
    void testExecutionFailedTxSeparateNoMarkerSuccessfulExecution() {
        // Given - Pre-insert audit entry with EXECUTION_FAILED state and TX_SEPARATE_NO_MARKER type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.EXECUTION_FAILED, AuditTxType.TX_SEPARATE_NO_MARKER);
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
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log shows successful execution after recovery
        auditHelper.verifyAuditSequenceStrict(
            EXECUTION_FAILED(changeId),
            STARTED(changeId),
            EXECUTED(changeId)
        );
    }
    
    @Test
    @DisplayName("Should execute successfully for EXECUTION_FAILED TX_SHARED change")
    void testExecutionFailedTxSharedSuccessfulExecution() {
        // Given - Pre-insert audit entry with EXECUTION_FAILED state and TX_SHARED type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.EXECUTION_FAILED, AuditTxType.TX_SHARED);
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
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log shows successful execution after recovery
        auditHelper.verifyAuditSequenceStrict(
            EXECUTION_FAILED(changeId),
            STARTED(changeId),
            EXECUTED(changeId)
        );
    }
    
    @Test
    @DisplayName("Should execute successfully for ROLLED_BACK NON_TX change")
    void testRolledBackNonTxSuccessfulExecution() {
        // Given - Pre-insert audit entry with ROLLED_BACK state and NON_TX type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.ROLLED_BACK, AuditTxType.NON_TX);
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
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log shows successful execution after rollback
        auditHelper.verifyAuditSequenceStrict(
            ROLLED_BACK(changeId),
            STARTED(changeId),
            EXECUTED(changeId)
        );
    }
    
    @Test
    @DisplayName("Should execute successfully for ROLLED_BACK TX_SEPARATE_NO_MARKER change")
    void testRolledBackTxSeparateNoMarkerSuccessfulExecution() {
        // Given - Pre-insert audit entry with ROLLED_BACK state and TX_SEPARATE_NO_MARKER type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.ROLLED_BACK, AuditTxType.TX_SEPARATE_NO_MARKER);
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
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log shows successful execution after rollback
        auditHelper.verifyAuditSequenceStrict(
            ROLLED_BACK(changeId),
            STARTED(changeId),
            EXECUTED(changeId)
        );
    }
    
    @Test
    @DisplayName("Should execute successfully for ROLLED_BACK TX_SHARED change")
    void testRolledBackTxSharedSuccessfulExecution() {
        // Given - Pre-insert audit entry with ROLLED_BACK state and TX_SHARED type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.ROLLED_BACK, AuditTxType.TX_SHARED);
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
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log shows successful execution after rollback
        auditHelper.verifyAuditSequenceStrict(
            ROLLED_BACK(changeId),
            STARTED(changeId),
            EXECUTED(changeId)
        );
    }
    
    @Test
    @DisplayName("Should require manual intervention for ROLLBACK_FAILED NON_TX change")
    void testRollbackFailedNonTxRequiresManualIntervention() {
        // Given - Pre-insert audit entry with ROLLBACK_FAILED state and NON_TX type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.ROLLBACK_FAILED, AuditTxType.NON_TX);
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
                        .setRelaxTargetSystemValidation(true)
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
    @DisplayName("Should do nothing for EXECUTED NON_TX change")
    void testExecutedNonTxDoNothing() {
        // Given - Pre-insert audit entry with EXECUTED state and NON_TX type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.EXECUTED, AuditTxType.NON_TX);
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
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log remains unchanged (only the pre-inserted EXECUTED entry)
        auditHelper.verifyAuditSequenceStrict(
            EXECUTED(changeId)
        );
    }
    
    @Test
    @DisplayName("Should do nothing for EXECUTED TX_SEPARATE_NO_MARKER change")
    void testExecutedTxSeparateNoMarkerDoNothing() {
        // Given - Pre-insert audit entry with EXECUTED state and TX_SEPARATE_NO_MARKER type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.EXECUTED, AuditTxType.TX_SEPARATE_NO_MARKER);
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
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log remains unchanged (only the pre-inserted EXECUTED entry)
        auditHelper.verifyAuditSequenceStrict(
            EXECUTED(changeId)
        );
    }
    
    @Test
    @DisplayName("Should do nothing for EXECUTED TX_SHARED change")
    void testExecutedTxSharedDoNothing() {
        // Given - Pre-insert audit entry with EXECUTED state and TX_SHARED type  
        String changeId = "test1-non-tx-change";
        AuditEntry preExistingEntry = AuditEntryTestFactory.createTestAuditEntry(changeId, AuditEntry.Status.EXECUTED, AuditTxType.TX_SHARED);
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
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log remains unchanged (only the pre-inserted EXECUTED entry)
        auditHelper.verifyAuditSequenceStrict(
            EXECUTED(changeId)
        );
    }

    @Test
    @DisplayName("Should persist default targetSystemId in audit entries")
    void testDefaultTargetSystemIdPersistence() {
        // Given - No pre-existing entries, letting Flamingock run and audit the process
        String changeId = "test1-non-tx-change";
        
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                )
            );
            
            // When - Run Flamingock (should execute successfully and audit with default target system)
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log shows successful execution
        auditHelper.verifyAuditSequenceStrict(
            STARTED(changeId),
            EXECUTED(changeId)
        );
        
        // Additional verification: check the stored audit entries have the correct default targetSystemId
        List<AuditEntry> auditEntries = testKit.getAuditStorage().getAuditEntries();
        assertEquals(2, auditEntries.size());
        
        // Verify both STARTED and EXECUTED entries have the default target system ID
        for (AuditEntry entry : auditEntries) {
            assertEquals("default-audit-store-target-system", entry.getTargetSystemId(), 
                        "Stored audit entry should have the default targetSystemId");
        }
    }
    
    @Test
    @DisplayName("Should persist custom targetSystemId in audit entries")
    void testCustomTargetSystemIdPersistence() {
        // Given - Custom target system added to builder
        String customTargetSystemId = "custom-target-system";
        String changeId = "test-custom-target-change";
        
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(CustomTargetSystemChange.class, Collections.emptyList())
                )
            );
            
            // When - Run Flamingock with custom target system added to builder
            assertDoesNotThrow(() -> {
                testKit.createBuilder()
                    .setRelaxTargetSystemValidation(true)
                    .addTargetSystem(new DefaultTargetSystem(customTargetSystemId))
                    .build()
                    .run();
            });
        }
        
        // Then - Verify audit log shows successful execution
        auditHelper.verifyAuditSequenceStrict(
            STARTED(changeId),
            EXECUTED(changeId)
        );
        
        // Additional verification: check the stored audit entries have the correct custom targetSystemId
        List<AuditEntry> auditEntries = testKit.getAuditStorage().getAuditEntries();
        assertEquals(2, auditEntries.size());
        
        // Verify both STARTED and EXECUTED entries have the custom target system ID
        for (AuditEntry entry : auditEntries) {
            assertEquals(customTargetSystemId, entry.getTargetSystemId(), 
                        "Stored audit entry should have the custom targetSystemId");
        }
    }
    
}