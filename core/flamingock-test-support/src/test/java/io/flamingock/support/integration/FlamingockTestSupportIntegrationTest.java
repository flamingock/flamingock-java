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
package io.flamingock.support.integration;

import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.common.test.pipeline.PipelineTestHelper;
import io.flamingock.core.kit.inmemory.InMemoryTestKit;
import io.flamingock.internal.common.core.util.Deserializer;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.support.FlamingockTestSupport;
import io.flamingock.support.integration.changes.*;
import io.flamingock.support.integration.helpers.Counter;
import io.flamingock.targetsystem.nontransactional.NonTransactionalTargetSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;

import static io.flamingock.support.domain.AuditEntryDefinition.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlamingockTestSupportIntegrationTest {

    @Test
    @DisplayName("Should execute non-transactional change")
    void shouldExecuteNonTransactionalChange() {
        InMemoryTestKit testKit = InMemoryTestKit.create();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_001__SimpleNonTransactionalChange.class, Collections.emptyList())
                    )
            );
            NonTransactionalTargetSystem targetSystem = new NonTransactionalTargetSystem("kafka");

            FlamingockTestSupport
                    .given(testKit.createBuilder().addTargetSystem(targetSystem))
                    .whenRun()
                    .thenExpectAuditSequenceStrict(
                            STARTED(_001__SimpleNonTransactionalChange.class),
                            APPLIED(_001__SimpleNonTransactionalChange.class)
                    )
                    .verify();
        } finally {
            testKit.cleanUp();
        }
    }

    @Test
    @DisplayName("Should verify multiple changes execute in correct sequence with complete audit flow")
    void shouldVerifyMultipleChangesInSequence() {
        InMemoryTestKit testKit = InMemoryTestKit.create();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_003__MultiTest1NonTransactionalChange.class, Collections.emptyList()),
                            new CodeChangeTestDefinition(_004__MultiTest2TransactionalChange.class, Collections.emptyList())
                    )
            );

            FlamingockTestSupport
                    .given(testKit.createBuilder()
                            .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                            .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                            .addTargetSystem(new NonTransactionalTargetSystem("s3")))
                    .whenRun()
                    .thenExpectAuditSequenceStrict(
                            STARTED(_003__MultiTest1NonTransactionalChange.class),
                            APPLIED(_003__MultiTest1NonTransactionalChange.class),
                            STARTED(_004__MultiTest2TransactionalChange.class),
                            APPLIED(_004__MultiTest2TransactionalChange.class)
                    )
                    .verify();
        }
    }

    @Test
    @DisplayName("Should verify failing transactional change triggers rollback with correct audit trail")
    void shouldVerifyFailingTransactionalChangeTriggersRollback() {
        InMemoryTestKit testKit = InMemoryTestKit.create();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_006__FailingTransactionalChange.class, Collections.emptyList(), Collections.emptyList())
                    )
            );

            FlamingockTestSupport
                    .given(testKit.createBuilder()
                            .addTargetSystem(new NonTransactionalTargetSystem("salesforce"))
                            .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                            .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                            .addTargetSystem(new NonTransactionalTargetSystem("s3")))
                    .whenRun()
                    .thenExpectException(PipelineExecutionException.class, null)
                    .andExpectAuditSequenceStrict(
                            STARTED(_006__FailingTransactionalChange.class),
                            FAILED(_006__FailingTransactionalChange.class),
                            ROLLED_BACK(_006__FailingTransactionalChange.class)
                    )
                    .verify();
        }
    }

    @Test
    @DisplayName("Should verify already-applied changes are skipped on subsequent runs")
    void shouldVerifyAlreadyAppliedChangesAreSkipped() {
        InMemoryTestKit testKit = InMemoryTestKit.create();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_005__SecondRunNonTransactionalChange.class, Collections.emptyList())
                    )
            );

            FlamingockTestSupport
                    .given(testKit.createBuilder()
                            .addTargetSystem(new NonTransactionalTargetSystem("stripe-api"))
                            .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                            .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                            .addTargetSystem(new NonTransactionalTargetSystem("s3")))
                    .andExistingAudit(
                            APPLIED(_005__SecondRunNonTransactionalChange.class)
                    )
                    .whenRun()
                    .thenExpectAuditSequenceStrict(
                            STARTED(_005__SecondRunNonTransactionalChange.class),
                            APPLIED(_005__SecondRunNonTransactionalChange.class)
                    )
                    .verify();
        }
    }

    @Test
    @DisplayName("Should verify dependency injection works correctly in rollback for non-transactional changes")
    void shouldVerifyDependencyInjectionInRollbackForNonTransactionalChanges() {
        InMemoryTestKit testKit = InMemoryTestKit.create();
        Counter counter = new Counter();

        NonTransactionalTargetSystem targetSystem = new NonTransactionalTargetSystem("kafka")
                .addDependency(counter);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_007__SimpleNonTransactionalChangeWithError.class,
                                    Collections.singletonList(Counter.class),
                                    Collections.singletonList(Counter.class))
                    )
            );

            FlamingockTestSupport
                    .given(testKit.createBuilder().addTargetSystem(targetSystem))
                    .whenRun()
                    .thenExpectException(PipelineExecutionException.class, ex -> {
                        assertTrue(ex.getMessage().contains("Intentional failure"));
                        assertTrue(counter.isExecuted(), "Counter should be executed");
                        assertTrue(counter.isRollbacked(), "Counter should be rolled back");
                    })
                    .andExpectAuditSequenceStrict(
                            STARTED(_007__SimpleNonTransactionalChangeWithError.class),
                            FAILED(_007__SimpleNonTransactionalChangeWithError.class),
                            ROLLED_BACK(_007__SimpleNonTransactionalChangeWithError.class)
                    )
                    .verify();
        }
    }

    @Test
    @DisplayName("Should verify transactional change executes successfully with correct audit entries")
    void shouldVerifyTransactionalChangeExecutesSuccessfully() {
        InMemoryTestKit testKit = InMemoryTestKit.create();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_002__SimpleTransactionalChange.class, Collections.emptyList())
                    )
            );

            FlamingockTestSupport
                    .given(testKit.createBuilder()
                            .addTargetSystem(new NonTransactionalTargetSystem("okta"))
                            .addTargetSystem(new NonTransactionalTargetSystem("elasticsearch"))
                            .addTargetSystem(new NonTransactionalTargetSystem("s3")))
                    .whenRun()
                    .thenExpectAuditSequenceStrict(
                            STARTED(_002__SimpleTransactionalChange.class),
                            APPLIED(_002__SimpleTransactionalChange.class)
                    )
                    .verify();
        }
    }
}

