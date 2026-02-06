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
package io.flamingock.internal.core.builder.runner;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.internal.core.operation.OperationType;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunnerFactoryTest {

    @Mock
    private AuditPersistence persistence;

    @Mock
    private LoadedPipeline pipeline;

    @Mock
    private ExecutionPlanner executionPlanner;

    @Mock
    private TargetSystemManager targetSystemManager;

    @Mock
    private CoreConfigurable coreConfiguration;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ContextResolver contextResolver;

    @Mock
    private Runnable finalizer;

    private RunnerId runnerId;
    private Set<Class<?>> nonGuardedTypes;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runnerId = RunnerId.generate("test-service");
        nonGuardedTypes = new HashSet<>();
        when(coreConfiguration.getMetadata()).thenReturn(Collections.emptyMap());
    }

    @Test
    @DisplayName("Should return Runner for EXECUTE operation type")
    void shouldReturnRunnerForExecuteOperationType() {
        // Given
        when(pipeline.getStages()).thenReturn(Collections.emptyList());

        // When
        Runner runner = RunnerFactory.getRunner(
                runnerId,
                OperationType.EXECUTE,
                pipeline,
                persistence,
                executionPlanner,
                targetSystemManager,
                coreConfiguration,
                eventPublisher,
                contextResolver,
                nonGuardedTypes,
                true,
                finalizer
        );

        // Then
        assertNotNull(runner);
        assertInstanceOf(DefaultRunner.class, runner);
    }

    @Test
    @DisplayName("Should return Runner for LIST operation type")
    void shouldReturnRunnerForListOperationType() {
        // When
        Runner runner = RunnerFactory.getRunner(
                runnerId,
                OperationType.LIST,
                pipeline,
                persistence,
                executionPlanner,
                targetSystemManager,
                coreConfiguration,
                eventPublisher,
                contextResolver,
                nonGuardedTypes,
                true,
                finalizer
        );

        // Then
        assertNotNull(runner);
        assertInstanceOf(DefaultRunner.class, runner);
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException for UNDO operation")
    void shouldThrowUnsupportedOperationExceptionForUndoOperation() {
        // When & Then
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> RunnerFactory.getRunner(
                        runnerId,
                        OperationType.UNDO,
                        pipeline,
                        persistence,
                        executionPlanner,
                        targetSystemManager,
                        coreConfiguration,
                        eventPublisher,
                        contextResolver,
                        nonGuardedTypes,
                        true,
                        finalizer
                )
        );

        assertEquals("Operation UNDO not supported", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException for VALIDATE operation")
    void shouldThrowUnsupportedOperationExceptionForValidateOperation() {
        // When & Then
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> RunnerFactory.getRunner(
                        runnerId,
                        OperationType.VALIDATE,
                        pipeline,
                        persistence,
                        executionPlanner,
                        targetSystemManager,
                        coreConfiguration,
                        eventPublisher,
                        contextResolver,
                        nonGuardedTypes,
                        true,
                        finalizer
                )
        );

        assertEquals("Operation VALIDATE not supported", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException for DRY_RUN operation")
    void shouldThrowUnsupportedOperationExceptionForDryRunOperation() {
        // When & Then
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> RunnerFactory.getRunner(
                        runnerId,
                        OperationType.DRY_RUN,
                        pipeline,
                        persistence,
                        executionPlanner,
                        targetSystemManager,
                        coreConfiguration,
                        eventPublisher,
                        contextResolver,
                        nonGuardedTypes,
                        true,
                        finalizer
                )
        );

        assertEquals("Operation DRY_RUN not supported", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException for FIX operation")
    void shouldThrowUnsupportedOperationExceptionForFixOperation() {
        // When & Then
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> RunnerFactory.getRunner(
                        runnerId,
                        OperationType.FIX,
                        pipeline,
                        persistence,
                        executionPlanner,
                        targetSystemManager,
                        coreConfiguration,
                        eventPublisher,
                        contextResolver,
                        nonGuardedTypes,
                        true,
                        finalizer
                )
        );

        assertEquals("Operation FIX not supported", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException for ISSUE operation")
    void shouldThrowUnsupportedOperationExceptionForIssueOperation() {
        // When & Then
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> RunnerFactory.getRunner(
                        runnerId,
                        OperationType.ISSUE,
                        pipeline,
                        persistence,
                        executionPlanner,
                        targetSystemManager,
                        coreConfiguration,
                        eventPublisher,
                        contextResolver,
                        nonGuardedTypes,
                        true,
                        finalizer
                )
        );

        assertEquals("Operation ISSUE not supported", exception.getMessage());
    }

    @Test
    @DisplayName("Should call finalizer when LIST runner completes")
    void shouldCallFinalizerWhenListRunnerCompletes() {
        // Given
        when(persistence.getAuditHistory()).thenReturn(Collections.emptyList());

        Runner runner = RunnerFactory.getRunner(
                runnerId,
                OperationType.LIST,
                pipeline,
                persistence,
                executionPlanner,
                targetSystemManager,
                coreConfiguration,
                eventPublisher,
                contextResolver,
                nonGuardedTypes,
                true,
                finalizer
        );

        // When
        runner.run();

        // Then
        verify(finalizer, times(1)).run();
    }
}
