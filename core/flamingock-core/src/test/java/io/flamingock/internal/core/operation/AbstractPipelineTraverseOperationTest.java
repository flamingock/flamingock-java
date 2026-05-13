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

import io.flamingock.internal.common.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.operation.execute.ExecuteApplyOperation;
import io.flamingock.internal.core.operation.execute.ExecuteArgs;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.run.PipelineRun;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AbstractPipelineTraverseOperationTest {

    @Test
    @DisplayName("Should throw ManualInterventionRequiredException when planner returns ABORT with MI changes")
    void shouldThrowManualInterventionWhenPlannerReturnsAbort() {
        ExecutableChange miChange = mockChange("change-1", ChangeAction.MANUAL_INTERVENTION);
        ExecutableStage stage = new ExecutableStage("stage-1", Collections.singletonList(miChange));
        ExecutionPlan abortPlan = ExecutionPlan.ABORT(Collections.singletonList(stage));

        ExecutionPlanner planner = mock(ExecutionPlanner.class);
        when(planner.getNextExecution(any(PipelineRun.class))).thenReturn(abortPlan);

        LoadedPipeline pipeline = mockPipeline("change-1");
        ExecuteApplyOperation operation = buildOperation(planner);

        ManualInterventionRequiredException ex = assertThrows(
                ManualInterventionRequiredException.class,
                () -> operation.execute(new ExecuteArgs(pipeline)));
        assertTrue(ex.getConflictingSummary().contains("change-1"));
    }

    @Test
    @DisplayName("Should throw FlamingockException and not execute changes when plan is ABORT without MI changes")
    void shouldNotExecuteChangesWhenPlanIsAbort() {
        ExecutableChange change = mockChange("change-1", ChangeAction.APPLY);
        ExecutableStage stage = new ExecutableStage("stage-1", Collections.singletonList(change));
        ExecutionPlan abortPlan = ExecutionPlan.ABORT(Collections.singletonList(stage));

        ExecutionPlanner planner = mock(ExecutionPlanner.class);
        when(planner.getNextExecution(any(PipelineRun.class))).thenReturn(abortPlan);

        StageExecutor stageExecutor = mock(StageExecutor.class);
        LoadedPipeline pipeline = mockPipeline();
        ExecuteApplyOperation operation = buildOperation(planner, stageExecutor);

        assertThrows(io.flamingock.internal.common.core.error.FlamingockException.class,
                () -> operation.execute(new ExecuteArgs(pipeline)));
        verify(stageExecutor, never()).executeStage(any(), any(), any());
    }

    private static ExecuteApplyOperation buildOperation(ExecutionPlanner planner) {
        return buildOperation(planner, mock(StageExecutor.class));
    }

    private static ExecuteApplyOperation buildOperation(ExecutionPlanner planner, StageExecutor stageExecutor) {
        return new ExecuteApplyOperation(
                RunnerId.fromString("test"),
                planner,
                stageExecutor,
                new OrphanExecutionContext("localhost", null),
                mock(EventPublisher.class),
                true,
                () -> {}
        );
    }

    private static LoadedPipeline mockPipeline(String... loadedChangeIds) {
        AbstractLoadedStage loadedStage = mock(AbstractLoadedStage.class);
        when(loadedStage.getName()).thenReturn("stage-1");
        java.util.List<AbstractLoadedChange> loadedChanges = new java.util.ArrayList<>();
        for (String id : loadedChangeIds) {
            AbstractLoadedChange ch = mock(AbstractLoadedChange.class);
            when(ch.getId()).thenReturn(id);
            loadedChanges.add(ch);
        }
        when(loadedStage.getChanges()).thenReturn(loadedChanges);

        LoadedPipeline pipeline = mock(LoadedPipeline.class);
        when(pipeline.getStages()).thenReturn(Collections.singletonList(loadedStage));
        when(pipeline.getSystemStage()).thenReturn(java.util.Optional.empty());
        doNothing().when(pipeline).validate();
        return pipeline;
    }

    private static ExecutableChange mockChange(String id, ChangeAction action) {
        ExecutableChange change = mock(ExecutableChange.class);
        when(change.getId()).thenReturn(id);
        when(change.getAction()).thenReturn(action);
        when(change.isAlreadyApplied()).thenReturn(action == ChangeAction.SKIP);
        return change;
    }
}
