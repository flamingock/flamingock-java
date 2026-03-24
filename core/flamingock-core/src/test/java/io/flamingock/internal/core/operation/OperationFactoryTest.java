/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.builder.args.FlamingockArguments;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.internal.core.operation.execute.ValidateOperation;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.common.core.operation.OperationType;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Tests for OperationFactory — routing logic for creating the appropriate operation.
 */
class OperationFactoryTest {

    @Mock
    private FlamingockArguments flamingockArgs;

    @Mock
    private LoadedPipeline pipeline;

    @Mock
    private AuditPersistence persistence;

    @Mock
    private ExecutionPlanner executionPlanner;

    @Mock
    private TargetSystemManager targetSystemManager;

    @Mock
    private CoreConfigurable coreConfiguration;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ContextResolver dependencyContext;

    @Mock
    private AbstractLoadedStage loadedStage;

    @Mock
    private AbstractLoadedTask loadedTask;

    private RunnerId runnerId;
    private Runnable noOpFinalizer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runnerId = RunnerId.fromString("test-runner@localhost#test-uuid");
        noOpFinalizer = () -> {};

        // Default pipeline setup so OperationFactory does not NPE on pipeline access
        when(pipeline.getSystemStage()).thenReturn(java.util.Optional.empty());
        when(pipeline.getStages()).thenReturn(Collections.singletonList(loadedStage));
        when(loadedStage.getTasks()).thenReturn(Collections.singletonList(loadedTask));

        // Default coreConfiguration stubs
        when(coreConfiguration.getMetadata()).thenReturn(Collections.emptyMap());
    }

    @Test
    @DisplayName("validationOnly=true with EXECUTE_APPLY → getOperation() routes to ValidateOperation")
    void shouldRouteToValidateOperationWhenValidationOnlyIsTrue() throws Exception {
        // Given
        when(flamingockArgs.getOperation()).thenReturn(OperationType.EXECUTE_APPLY);
        when(coreConfiguration.isValidationOnly()).thenReturn(true);

        OperationFactory factory = new OperationFactory(
                runnerId,
                flamingockArgs,
                pipeline,
                persistence,
                executionPlanner,
                targetSystemManager,
                coreConfiguration,
                eventPublisher,
                dependencyContext,
                new HashSet<>(),
                true,
                noOpFinalizer
        );

        // When
        RunnableOperation<?, ?> runnableOperation = factory.getOperation();

        // Then
        assertNotNull(runnableOperation);
        Operation<?, ?> innerOperation = extractInnerOperation(runnableOperation);
        assertInstanceOf(ValidateOperation.class, innerOperation,
                "Expected the factory to route to ValidateOperation when validationOnly=true");
    }

    @Test
    @DisplayName("validationOnly=false with EXECUTE_APPLY → getOperation() does NOT route to ValidateOperation")
    void shouldNotRouteToValidateOperationWhenValidationOnlyIsFalse() throws Exception {
        // Given
        when(flamingockArgs.getOperation()).thenReturn(OperationType.EXECUTE_APPLY);
        when(coreConfiguration.isValidationOnly()).thenReturn(false);

        OperationFactory factory = new OperationFactory(
                runnerId,
                flamingockArgs,
                pipeline,
                persistence,
                executionPlanner,
                targetSystemManager,
                coreConfiguration,
                eventPublisher,
                dependencyContext,
                new HashSet<>(),
                true,
                noOpFinalizer
        );

        // When
        RunnableOperation<?, ?> runnableOperation = factory.getOperation();

        // Then
        assertNotNull(runnableOperation);
        Operation<?, ?> innerOperation = extractInnerOperation(runnableOperation);
        // When validationOnly=false the standard ExecuteOperation is used, not ValidateOperation
        assertNotNull(innerOperation);
        // Verify it is NOT a ValidateOperation
        boolean isValidateOp = innerOperation instanceof ValidateOperation;
        org.junit.jupiter.api.Assertions.assertFalse(isValidateOp,
                "Expected the factory NOT to route to ValidateOperation when validationOnly=false");
    }

    // ─────────────────────────── Helpers ───────────────────────────

    /**
     * Uses reflection to extract the private {@code operation} field from a {@link RunnableOperation}.
     */
    private static Operation<?, ?> extractInnerOperation(RunnableOperation<?, ?> runnableOperation)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = RunnableOperation.class.getDeclaredField("operation");
        field.setAccessible(true);
        return (Operation<?, ?>) field.get(runnableOperation);
    }
}
