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
import io.flamingock.core.e2e.changes.*;
import io.flamingock.core.e2e.helpers.Counter;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.inmemory.InMemoryTestKit;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.targetsystem.nontransactional.NonTransactionalTargetSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.FAILED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.ROLLED_BACK;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static org.junit.jupiter.api.Assertions.*;


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
                            new CodeChangeTestDefinition(_001__SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When - Execute using test builder with domain separation
            testKit.createBuilder()
                    .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                    .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                    .addTargetSystem(new NonTransactionalTargetSystem("kafka"))
                    .build()
                    .run();
        }

        // Then - Verify complete audit flow using audit-specific helper
        auditHelper.verifyAuditSequenceStrict(
                STARTED("test1-non-tx-change"),
                APPLIED("test1-non-tx-change")
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
                            new CodeChangeTestDefinition(_002__SimpleTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When
            testKit.createBuilder()
                    .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                    .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                    .addTargetSystem(new NonTransactionalTargetSystem("s3"))
                    .build()
                    .run();
        }

        // Then - Verify complete audit flow for transactional change

        auditHelper.verifyAuditSequenceStrict(
                STARTED("test2-tx-change"),
                APPLIED("test2-tx-change")
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
                            new CodeChangeTestDefinition(_003__MultiTest1NonTransactionalChange.class, Collections.emptyList()),
                            new CodeChangeTestDefinition(_004__MultiTest2TransactionalChange.class, Collections.emptyList())
                    )
            );

            // When
            testKit.createBuilder()
                    .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                    .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                    .addTargetSystem(new NonTransactionalTargetSystem("s3"))
                    .build()
                    .run();
        }

        auditHelper.verifyAuditSequenceStrict(
                STARTED("test3-multi-non-tx-change"),
                APPLIED("test3-multi-non-tx-change"),
                STARTED("test3-multi-tx-change"),
                APPLIED("test3-multi-tx-change")
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
                            new CodeChangeTestDefinition(_006__FailingTransactionalChange.class, Collections.emptyList())
                    )
            );

            // When & Then - Execution should fail
            assertThrows(PipelineExecutionException.class, () -> {
                testKit.createBuilder()
                        .addTargetSystem(new NonTransactionalTargetSystem("salesforce"))
                        .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                        .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                        .addTargetSystem(new NonTransactionalTargetSystem("s3"))
                        .build()
                        .run();
            });
        }

        // Then - Verify failure audit sequence using new concise API
        auditHelper.verifyAuditSequenceStrict(
                STARTED("test4-failing-tx-change"),
                FAILED("test4-failing-tx-change"),
                ROLLED_BACK("test4-failing-tx-change")
        );
    }

    @Test
    @DisplayName("Should handle already-applied changes correctly on second run")
    void testAlreadyAppliedChangesSkipping() {
        // Given - Create test kit with persistent storage
        InMemoryTestKit testKit = InMemoryTestKit.create();
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_005__SecondRunNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            // First execution
            testKit.createBuilder()
                    .addTargetSystem(new NonTransactionalTargetSystem("stripe-api"))
                    .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                    .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                    .addTargetSystem(new NonTransactionalTargetSystem("s3"))
                    .build()
                    .run();

            // Verify first execution
            auditHelper.verifyAuditSequenceStrict(
                    STARTED("test5-second-run-change"),
                    APPLIED("test5-second-run-change")
            );


            // Second execution - create new kit using SAME storage to simulate persistence
            // but avoid potential builder state issues
            InMemoryTestKit secondRunKit = InMemoryTestKit.create(
                    testKit.getAuditStorage(),
                    testKit.getLockStorage()
            );

            secondRunKit.createBuilder()
                    .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                    .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                    .addTargetSystem(new NonTransactionalTargetSystem("s3"))
                    .build()
                    .run();
        }

        // Then - Should still have only original 2 audit entries (no additional executions)
        auditHelper.verifyAuditSequenceStrict(
                STARTED("test5-second-run-change"),
                APPLIED("test5-second-run-change")
        );
    }

    @Test
    @DisplayName("Should inject dependencies in rollback for NON-TX change")
    void testDependencyInjectionInRollbackForNonTxChange() {
        InMemoryTestKit testKit = InMemoryTestKit.create();
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        Counter counter = new Counter();

        NonTransactionalTargetSystem targetSystem = new NonTransactionalTargetSystem( "kafka")
                .addDependency(counter);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_007__SimpleNonTransactionalChangeWithError.class, Collections.emptyList())
                    )
            );

            try {
                testKit.createBuilder()
                        .addTargetSystem(targetSystem)
                        .build()
                        .run();
            } catch (PipelineExecutionException e) {
                // Exception is expected, do not fail the test
            }
        }

        assertTrue(counter.isExecuted(), "Counter.executed should be true after execution");
        assertTrue(counter.isRollbacked(), "Counter.rollbacked should be true after rollback");

        auditHelper.verifyAuditSequenceStrict(
                STARTED("test1-non-tx-change"),
                FAILED("test1-non-tx-change"),
                ROLLED_BACK("test1-non-tx-change")
        );
    }
}
