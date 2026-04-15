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
import io.flamingock.internal.core.operation.validate.ValidateApplyOperation;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Tests for OperationResolver — routing logic for creating the appropriate operation.
 */
class OperationResolverTest {

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
    private AbstractLoadedChange loadedChange;

    private RunnerId runnerId;
    private Runnable noOpFinalizer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runnerId = RunnerId.fromString("test-runner@localhost#test-uuid");
        noOpFinalizer = () -> {};

        // Default pipeline setup so OperationResolver does not NPE on pipeline access
        when(pipeline.getSystemStage()).thenReturn(java.util.Optional.empty());
        when(pipeline.getStages()).thenReturn(Collections.singletonList(loadedStage));
        when(loadedStage.getChanges()).thenReturn(Collections.singletonList(loadedChange));

        // Default coreConfiguration stubs
        when(coreConfiguration.getMetadata()).thenReturn(Collections.emptyMap());
    }

    @Test
    @DisplayName("validationOnly=true with no operation → getOperation() routes to ValidateApplyOperation")
    void shouldRouteToValidateOperationWhenValidationOnlyIsTrue() throws Exception {
        // Given
        when(flamingockArgs.getOperation()).thenReturn(Optional.empty());
        when(coreConfiguration.isValidationOnly()).thenReturn(true);

        OperationResolver factory = new OperationResolver(
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
        assertInstanceOf(ValidateApplyOperation.class, innerOperation,
                "Expected the factory to route to ValidateApplyOperation when validationOnly=true");
    }

    @Test
    @DisplayName("validationOnly=false with EXECUTE_APPLY → getOperation() does NOT route to ValidateApplyOperation")
    void shouldNotRouteToValidateOperationWhenValidationOnlyIsFalse() throws Exception {
        // Given
        when(flamingockArgs.getOperation()).thenReturn(Optional.of(OperationType.EXECUTE_APPLY));
        when(coreConfiguration.isValidationOnly()).thenReturn(false);

        OperationResolver factory = new OperationResolver(
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
        // When validationOnly=false the standard AbstractPipelineTraverseOperation is used, not ValidateApplyOperation
        assertNotNull(innerOperation);
        // Verify it is NOT a ValidateApplyOperation
        boolean isValidateOp = innerOperation instanceof ValidateApplyOperation;
        org.junit.jupiter.api.Assertions.assertFalse(isValidateOp,
                "Expected the factory NOT to route to ValidateApplyOperation when validationOnly=false");
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
