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
import io.flamingock.core.e2e.changes.SimpleTransactionalChange;
import io.flamingock.core.e2e.changes.FailingTransactionalChange;
import io.flamingock.core.e2e.changes.MultiTest1NonTransactionalChange;
import io.flamingock.core.e2e.changes.MultiTest2TransactionalChange;
import io.flamingock.core.e2e.changes.SecondRunNonTransactionalChange;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.core.kit.inmemory.InMemoryTestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.audit.AuditExpectation;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static io.flamingock.core.kit.audit.AuditExpectation.EXECUTED;
import static io.flamingock.core.kit.audit.AuditExpectation.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class CoreStrategiesE2ETest {
    
    @Test
    @DisplayName("Should execute non-transactional change using NonTx strategy with complete audit flow")
    void testNonTransactionalChangeExecution() {
        // Given - Create isolated test kit with domain-separated helpers
        InMemoryTestKit testKit = InMemoryTestKit.create();
        AuditTestHelper auditHelper = testKit.getAuditHelper();
        
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(SimpleNonTransactionalChange.class, Collections.emptyList())
                )
            );

            // When - Execute using test builder with domain separation
            testKit.createBuilder()
                .setRelaxTargetSystemValidation(true)
                .build()
                .run();
        }

        // Then - Verify complete audit flow using audit-specific helper
        auditHelper.verifyAuditSequenceStrict(
                STARTED("test1-non-tx-change"),
                EXECUTED("test1-non-tx-change")
        );

        List<AuditEntry> auditEntriesSorted = auditHelper.getAuditEntriesSorted();
        AuditEntry auditEntry = auditEntriesSorted.get(0);
        assertEquals(AuditTxType.NON_TX, auditEntry.getTxType());
    }
    
    @Test
    @DisplayName("Should execute transactional change using SimpleTx/SharedTx strategy with complete audit flow")
    void testTransactionalChangeExecution() {
        // Given - Create isolated test kit
        InMemoryTestKit testKit = InMemoryTestKit.create();
        AuditTestHelper auditHelper = testKit.getAuditHelper();
        
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(SimpleTransactionalChange.class, Collections.emptyList())
                )
            );
            
            // When
            testKit.createBuilder()
                .setRelaxTargetSystemValidation(true)
                .build()
                .run();
        }
        
        // Then - Verify complete audit flow for transactional change

        auditHelper.verifyAuditSequenceStrict(
                STARTED("test2-tx-change"),
                EXECUTED("test2-tx-change")
        );
    }
    
    @Test
    @DisplayName("Should execute multiple changes with correct audit sequence")
    void testMultipleChangesExecution() {
        // Given - Create isolated test kit
        InMemoryTestKit testKit = InMemoryTestKit.create();
        AuditTestHelper auditHelper = testKit.getAuditHelper();
        
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(MultiTest1NonTransactionalChange.class, Collections.emptyList()),
                    new CodeChangeUnitTestDefinition(MultiTest2TransactionalChange.class, Collections.emptyList())
                )
            );
            
            // When
            testKit.createBuilder()
                .setRelaxTargetSystemValidation(true)
                .build()
                .run();
        }
        
        auditHelper.verifyAuditSequenceStrict(
                AuditExpectation.STARTED("test3-multi-non-tx-change"),
                AuditExpectation.EXECUTED("test3-multi-non-tx-change"),
                AuditExpectation.STARTED("test3-multi-tx-change"),
                AuditExpectation.EXECUTED("test3-multi-tx-change")
        );
    }
    
    @Test
    @DisplayName("Should handle failing transactional change with proper audit and rollback")
    void testFailingTransactionalChangeWithRollback() {
        // Given - Create isolated test kit
        InMemoryTestKit testKit = InMemoryTestKit.create();
        AuditTestHelper auditHelper = testKit.getAuditHelper();
        
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(FailingTransactionalChange.class, Collections.emptyList())
                )
            );
            
            // When & Then - Execution should fail
            assertThrows(PipelineExecutionException.class, () -> {
                testKit.createBuilder()
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
            });
        }
        
        // Then - Verify failure audit sequence using new concise API
        auditHelper.verifyAuditSequenceStrict(
                AuditExpectation.STARTED("test4-failing-tx-change"),
                AuditExpectation.EXECUTION_FAILED("test4-failing-tx-change"),
                AuditExpectation.ROLLED_BACK("test4-failing-tx-change")
        );
    }
    
    @Test
    @DisplayName("Should handle already-executed changes correctly on second run")
    void testAlreadyExecutedChangesSkipping() {
        // Given - Create test kit with persistent storage
        InMemoryTestKit testKit = InMemoryTestKit.create();
        AuditTestHelper auditHelper = testKit.getAuditHelper();
        
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                PipelineTestHelper.getPreviewPipeline(
                    new CodeChangeUnitTestDefinition(SecondRunNonTransactionalChange.class, Collections.emptyList())
                )
            );
            
            // First execution
            testKit.createBuilder()
                .setRelaxTargetSystemValidation(true)
                .build()
                .run();
                
            // Verify first execution
            auditHelper.verifyAuditSequenceStrict(
                    STARTED("test5-second-run-change"),
                    EXECUTED("test5-second-run-change")
            );

            
            // Second execution - create new kit using SAME storage to simulate persistence
            // but avoid potential builder state issues
            InMemoryTestKit secondRunKit = InMemoryTestKit.create(
                testKit.getAuditStorage(), 
                testKit.getLockStorage()
            );
            
            secondRunKit.createBuilder()
                .setRelaxTargetSystemValidation(true)
                .build()
                .run();
        }
        
        // Then - Should still have only original 2 audit entries (no additional executions)
        auditHelper.verifyAuditSequenceStrict(
                STARTED("test5-second-run-change"),
                EXECUTED("test5-second-run-change")
        );
    }
}