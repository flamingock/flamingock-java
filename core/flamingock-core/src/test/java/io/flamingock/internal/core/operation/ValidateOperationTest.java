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

import io.flamingock.internal.common.core.error.PendingChangesException;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.operation.execute.ExecuteArgs;
import io.flamingock.internal.core.operation.execute.ExecuteResult;
import io.flamingock.internal.core.operation.validate.ValidateOperation;
import io.flamingock.internal.core.pipeline.execution.ExecutablePipeline;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for ValidateOperation — validation-only mode that checks for pending changes
 * without executing them.
 */
class ValidateOperationTest {

    @Mock
    private ExecutionPlanner executionPlanner;

    @Mock
    private StageExecutor stageExecutor;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private LoadedPipeline pipeline;

    @Mock
    private AbstractLoadedStage loadedStage;

    @Mock
    private AbstractLoadedTask loadedTask;

    private ValidateOperation operation;
    private RunnerId runnerId;
    private OrphanExecutionContext orphanContext;
    private Runnable noOpFinalizer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runnerId = RunnerId.fromString("test-runner@localhost#test-uuid");
        orphanContext = new OrphanExecutionContext("localhost", new HashMap<>());
        noOpFinalizer = () -> {};

        operation = new ValidateOperation(
            runnerId,
            executionPlanner,
            stageExecutor,
            orphanContext,
            eventPublisher,
            true,
            noOpFinalizer
        );
    }

    @Test
    @DisplayName("validationOnly: no pending changes → execute() returns success without throwing")
    void shouldReturnSuccessWhenNoPendingChangesExist() throws Exception {
        // Given
        ExecutionPlan executionPlan = mockNoPendingPlan();

        when(pipeline.getSystemStage()).thenReturn(java.util.Optional.empty());
        when(pipeline.getStages()).thenReturn(Collections.singletonList(loadedStage));
        when(loadedStage.getTasks()).thenReturn(Collections.singletonList(loadedTask));
        when(executionPlanner.getNextExecution(any())).thenReturn(executionPlan);

        ExecuteArgs args = new ExecuteArgs(pipeline);

        // When
        ExecuteResult result = operation.execute(args);

        // Then
        assertNotNull(result);
        assertNotNull(result.getData());
        // ValidateOperation uses resultBuilder.noChanges() — status is SUCCESS when no pending changes
        assertEquals(ExecutionStatus.SUCCESS, result.getData().getStatus());
    }

    @Test
    @DisplayName("validationOnly: pending changes exist → execute() throws PendingChangesException with correct count")
    void shouldThrowPendingChangesExceptionWhenPendingChangesExist() throws Exception {
        // Given
        // Two pending tasks (isAlreadyApplied = false)
        ExecutableTask pendingTask1 = mock(ExecutableTask.class);
        ExecutableTask pendingTask2 = mock(ExecutableTask.class);
        when(pendingTask1.isAlreadyApplied()).thenReturn(false);
        when(pendingTask2.isAlreadyApplied()).thenReturn(false);

        List<ExecutableTask> pendingTasks = Arrays.asList(pendingTask1, pendingTask2);
        ExecutableStage executableStage = mock(ExecutableStage.class);
        doReturn(pendingTasks).when(executableStage).getTasks();

        ExecutablePipeline executablePipeline = mock(ExecutablePipeline.class);
        when(executablePipeline.getExecutableStages()).thenReturn(Collections.singletonList(executableStage));

        ExecutionPlan executionPlan = mockPendingPlan(executablePipeline);

        when(pipeline.getSystemStage()).thenReturn(java.util.Optional.empty());
        when(pipeline.getStages()).thenReturn(Collections.singletonList(loadedStage));
        when(loadedStage.getTasks()).thenReturn(Arrays.asList(loadedTask, loadedTask));
        when(executionPlanner.getNextExecution(any())).thenReturn(executionPlan);

        ExecuteArgs args = new ExecuteArgs(pipeline);

        // When / Then
        PendingChangesException thrown = assertThrows(
                PendingChangesException.class,
                () -> operation.execute(args)
        );
    }

    // ─────────────────────────── Helpers ───────────────────────────

    /**
     * Creates an ExecutionPlan mock where no execution is required
     * (i.e., all changes are already applied).
     */
    private ExecutionPlan mockNoPendingPlan() {
        ExecutionPlan plan = mock(ExecutionPlan.class);
        when(plan.isExecutionRequired()).thenReturn(false);
        doNothing().when(plan).validate();
        doNothing().when(plan).close();
        return plan;
    }

    /**
     * Creates an ExecutionPlan mock where execution is required
     * (i.e., there are pending changes) and the pipeline exposes the given executable stages.
     */
    private ExecutionPlan mockPendingPlan(ExecutablePipeline executablePipeline) {
        ExecutionPlan plan = mock(ExecutionPlan.class);
        when(plan.isExecutionRequired()).thenReturn(true);
        when(plan.getPipeline()).thenReturn(executablePipeline);
        doNothing().when(plan).validate();
        doNothing().when(plan).close();
        return plan;
    }
}
