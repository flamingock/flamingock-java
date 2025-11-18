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
import io.flamingock.core.e2e.changes._008__TargetSystemManagerInjectionChange;
import io.flamingock.core.e2e.helpers.Counter;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.inmemory.InMemoryTestKit;
import io.flamingock.internal.common.core.util.Deserializer;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.targetsystem.nontransactional.NonTransactionalTargetSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static org.junit.jupiter.api.Assertions.assertTrue;


class BuilderE2ETest {

    @Test
    @DisplayName("Should inject TargetSystemManager as dependency in change")
    void shouldInjectTargetSystemManagerInChange() {
        // Given - Create isolated test kit with domain-separated helpers
        InMemoryTestKit testKit = InMemoryTestKit.create();
        AuditTestHelper auditHelper = testKit.getAuditHelper();

        Counter counter = new Counter();

        NonTransactionalTargetSystem targetSystem = new NonTransactionalTargetSystem("kafka")
                .addDependency(counter);

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(
                                    _008__TargetSystemManagerInjectionChange.class,
                                    Arrays.asList(TargetSystemManager.class, Counter.class)
                            )
                    )
            );

            // When - Execute using test builder
            testKit.createBuilder()
                    .addTargetSystem(targetSystem)
                    .build()
                    .run();
        }

        // Then - Verify that TargetSystemManager was successfully injected
        assertTrue(counter.isExecuted(), "Counter.executed should be true, indicating TargetSystemManager was injected");

        // Verify complete audit flow
        auditHelper.verifyAuditSequenceStrict(
                STARTED("test8-target-system-manager-injection"),
                APPLIED("test8-target-system-manager-injection")
        );
    }
}
