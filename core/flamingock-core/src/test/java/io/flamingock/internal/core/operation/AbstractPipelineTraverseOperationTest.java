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

import io.flamingock.api.StageType;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.operation.execute.ExecuteApplyOperation;
import io.flamingock.internal.core.operation.execute.ExecuteArgs;
import io.flamingock.internal.core.operation.execute.ExecuteResult;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.run.PipelineRun;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.util.TriConsumer;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AbstractPipelineTraverseOperationTest {

    @Test
    @DisplayName("MI changes per stage should mark the stage BLOCKED_FOR_MI and throw StagedExecuteOperationException")
    void miChangesPerStageShouldBlockStageAndThrowStagedException() {
        ExecutableChange miChange = mockChange("change-1", ChangeAction.MANUAL_INTERVENTION);
        ExecutableStage stage = new ExecutableStage("stage-1", Collections.singletonList(miChange));
        ExecutionPlan plan = ExecutionPlan.newExecution("exec-1", null, Collections.singletonList(stage));
        ExecutionPlan continuePlan = ExecutionPlan.CONTINUE();

        ExecutionPlanner planner = mock(ExecutionPlanner.class);
        when(planner.getNextExecution(any(PipelineRun.class))).thenReturn(plan, continuePlan);

        LoadedPipeline pipeline = mockPipeline("change-1");
        StageExecutor stageExecutor = mock(StageExecutor.class);
        ExecuteApplyOperation operation = buildOperation(planner, stageExecutor);

        StagedExecuteOperationException ex = assertThrows(
                StagedExecuteOperationException.class,
                () -> operation.execute(new ExecuteArgs(pipeline)));

        StageResult stageResult = ex.getResult().getStages().get(0);
        assertEquals("stage-1", stageResult.getStageName());
        assertTrue(stageResult.getState().isBlockedForManualIntervention());
        verify(stageExecutor, never()).executeStage(any(), any(), any());
    }

    @Test
    @DisplayName("ABORT plan without pre-existing stage failures should exit the loop cleanly without throwing")
    void abortPlanWithoutStageFailuresExitsCleanly() {
        // Under the new ABORT/CONTINUE semantics, ABORT is a control-flow signal — the operation
        // breaks the loop and returns the result. In production, ABORT is only returned by the
        // planner when stage failures are already recorded in PipelineRun; the realistic path is
        // covered by miChangesPerStageShouldBlockStageAndThrowStagedException. This test
        // documents the synthetic "ABORT with no failures" case: no exception, no work done.
        ExecutionPlan abortPlan = ExecutionPlan.ABORT();

        ExecutionPlanner planner = mock(ExecutionPlanner.class);
        when(planner.getNextExecution(any(PipelineRun.class))).thenReturn(abortPlan);

        StageExecutor stageExecutor = mock(StageExecutor.class);
        LoadedPipeline pipeline = mockPipeline();
        ExecuteApplyOperation operation = buildOperation(planner, stageExecutor);

        operation.execute(new ExecuteArgs(pipeline));   // returns normally — no exception
        verify(stageExecutor, never()).executeStage(any(), any(), any());
    }

    @Test
    @DisplayName("Should execute multiple blocks in dependency order (happy path: SYSTEM -> LEGACY -> DEFAULT, all succeed)")
    void shouldExecuteMultipleBlocksInDependencyOrderHappyPath() {
        // Three executable stages, one per block. The mocked planner returns a newExecution plan
        // for each in dependency order, then CONTINUE() when there's no more work. The operation
        // drives the loop; we verify the executor was invoked once per stage in the right order
        // and the response reflects a clean success.
        ExecutableStage systemExec = mockExecutableStage("flamingock-system-stage");
        ExecutableStage legacyExec = mockExecutableStage("flamingock-legacy-stage");
        ExecutableStage defaultExec = mockExecutableStage("changes");

        StageExecutor stageExecutor = mock(StageExecutor.class);
        when(stageExecutor.executeStage(any(), any(), any())).thenAnswer(invocation -> {
            ExecutableStage es = invocation.getArgument(0);
            return successOutput(es.getName());
        });

        ExecutionPlan planSystem = mockPlanInvokingConsumerWith(systemExec);
        ExecutionPlan planLegacy = mockPlanInvokingConsumerWith(legacyExec);
        ExecutionPlan planDefault = mockPlanInvokingConsumerWith(defaultExec);
        ExecutionPlan donePlan = mockContinuePlan();

        ExecutionPlanner planner = mock(ExecutionPlanner.class);
        when(planner.getNextExecution(any(PipelineRun.class)))
                .thenReturn(planSystem, planLegacy, planDefault, donePlan);

        LoadedPipeline pipeline = mockMultiBlockPipeline();
        ExecuteApplyOperation operation = buildOperation(planner, stageExecutor);

        ExecuteResult result = operation.execute(new ExecuteArgs(pipeline));

        // Response status is SUCCESS and all three stages ran.
        ExecuteResponseData response = result.getData();
        assertEquals(ExecutionStatus.SUCCESS, response.getStatus());
        assertEquals(3, response.getCompletedStages());
        assertEquals(0, response.getFailedStages());

        // Verify execution order: SYSTEM first, LEGACY second, DEFAULT third.
        ArgumentCaptor<ExecutableStage> captor = ArgumentCaptor.forClass(ExecutableStage.class);
        verify(stageExecutor, times(3)).executeStage(captor.capture(), any(), any());
        List<ExecutableStage> executed = captor.getAllValues();
        assertEquals("flamingock-system-stage", executed.get(0).getName());
        assertEquals("flamingock-legacy-stage", executed.get(1).getName());
        assertEquals("changes", executed.get(2).getName());
    }

    @Test
    @DisplayName("Should throw StagedExecuteOperationException with downstream block stages NOT_STARTED when an earlier block fails")
    void shouldThrowStagedExceptionWithDownstreamStagesNotStartedWhenEarlierBlockFails() {
        // SYSTEM stage fails. The planner then returns ABORT() (because SYSTEM block becomes
        // terminal+hasFailures). Downstream blocks (LEGACY, DEFAULT) must stay NOT_STARTED.
        ExecutableStage systemExec = mockExecutableStage("flamingock-system-stage");

        StageExecutor stageExecutor = mock(StageExecutor.class);
        StageExecutionException systemFailure = StageExecutionException.fromResult(
                new RuntimeException("system boom"),
                failedStageResult("flamingock-system-stage"),
                "sys-c1");
        when(stageExecutor.executeStage(eq(systemExec), any(), any())).thenThrow(systemFailure);

        ExecutionPlan planSystem = mockPlanInvokingConsumerWith(systemExec);
        ExecutionPlan abortPlan = ExecutionPlan.ABORT();

        ExecutionPlanner planner = mock(ExecutionPlanner.class);
        when(planner.getNextExecution(any(PipelineRun.class)))
                .thenReturn(planSystem, abortPlan);

        LoadedPipeline pipeline = mockMultiBlockPipeline();
        ExecuteApplyOperation operation = buildOperation(planner, stageExecutor);

        StagedExecuteOperationException ex = assertThrows(
                StagedExecuteOperationException.class,
                () -> operation.execute(new ExecuteArgs(pipeline)));

        ExecuteResponseData response = ex.getResult();
        assertEquals(ExecutionStatus.FAILED, response.getStatus());
        assertEquals(3, response.getTotalStages());
        assertEquals(0, response.getCompletedStages());
        assertEquals(1, response.getFailedStages());

        // Per-stage: SYSTEM failed, LEGACY + DEFAULT untouched (NOT_STARTED).
        StageResult systemResult = findStage(response, "flamingock-system-stage");
        assertTrue(systemResult.getState().isFailed(),
                "SYSTEM stage should be Failed after its only executable stage failed");

        StageResult legacyResult = findStage(response, "flamingock-legacy-stage");
        assertTrue(legacyResult.getState().isNotStarted(),
                "LEGACY stage must remain NOT_STARTED — its block is downstream of a failed block");

        StageResult defaultResult = findStage(response, "changes");
        assertTrue(defaultResult.getState().isNotStarted(),
                "DEFAULT stage must remain NOT_STARTED — its block is downstream of a failed block");

        // Executor was called exactly once — only for SYSTEM.
        verify(stageExecutor, times(1)).executeStage(any(), any(), any());
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static StageResult findStage(ExecuteResponseData response, String name) {
        return response.getStages().stream()
                .filter(s -> name.equals(s.getStageName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stage not found in response: " + name));
    }

    private static ExecutableStage mockExecutableStage(String name) {
        ExecutableStage stage = mock(ExecutableStage.class);
        when(stage.getName()).thenReturn(name);
        doNothing().when(stage).validate();
        return stage;
    }

    private static StageExecutor.Output successOutput(String stageName) {
        StageResult result = StageResult.builder()
                .stageId(stageName)
                .stageName(stageName)
                .state(StageState.COMPLETED)
                .build();
        StageExecutor.Output output = mock(StageExecutor.Output.class);
        when(output.getResult()).thenReturn(result);
        return output;
    }

    private static StageResult failedStageResult(String stageName) {
        return StageResult.builder()
                .stageId(stageName)
                .stageName(stageName)
                .state(StageState.failed(null))
                .build();
    }

    private static ExecutionPlan mockPlanInvokingConsumerWith(ExecutableStage executableStage) {
        ExecutionPlan plan = mock(ExecutionPlan.class);
        when(plan.isAborted()).thenReturn(false);
        when(plan.isExecutionRequired()).thenReturn(true);
        doNothing().when(plan).close();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TriConsumer<String, io.flamingock.internal.core.external.store.lock.Lock, ExecutableStage> consumer =
                    invocation.getArgument(0);
            consumer.accept("exec-1", null, executableStage);
            return null;
        }).when(plan).applyOnEach(any());
        return plan;
    }

    private static ExecutionPlan mockContinuePlan() {
        ExecutionPlan plan = mock(ExecutionPlan.class);
        when(plan.isAborted()).thenReturn(false);
        when(plan.isExecutionRequired()).thenReturn(false);
        doNothing().when(plan).close();
        return plan;
    }

    private static LoadedPipeline mockMultiBlockPipeline() {
        AbstractLoadedStage systemLoaded = mockNamedTypedLoadedStage("flamingock-system-stage", StageType.SYSTEM);
        AbstractLoadedStage legacyLoaded = mockNamedTypedLoadedStage("flamingock-legacy-stage", StageType.LEGACY);
        AbstractLoadedStage defaultLoaded = mockNamedTypedLoadedStage("changes", StageType.DEFAULT);

        LoadedPipeline pipeline = mock(LoadedPipeline.class);
        when(pipeline.getSystemStage()).thenReturn(java.util.Optional.of(systemLoaded));
        when(pipeline.getStages()).thenReturn(Arrays.asList(legacyLoaded, defaultLoaded));
        doNothing().when(pipeline).validate();
        return pipeline;
    }

    private static AbstractLoadedStage mockNamedTypedLoadedStage(String name, StageType type) {
        AbstractLoadedStage stage = mock(AbstractLoadedStage.class);
        when(stage.getName()).thenReturn(name);
        when(stage.getType()).thenReturn(type);
        when(stage.getChanges()).thenReturn(Collections.emptyList());
        return stage;
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
