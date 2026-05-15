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

import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
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
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.util.TriConsumer;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ExecuteApplyOperation - executes the pipeline and returns structured result data.
 */
class ExecuteApplyOperationTest {

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
    private AbstractLoadedChange loadedChange;

    private ExecuteApplyOperation operation;
    private RunnerId runnerId;
    private OrphanExecutionContext orphanContext;
    private Runnable noOpFinalizer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runnerId = RunnerId.fromString("test-runner@localhost#test-uuid");
        orphanContext = new OrphanExecutionContext("localhost", new HashMap<>());
        noOpFinalizer = () -> {};

        operation = new ExecuteApplyOperation(
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
    @DisplayName("Should return success result when all changes apply")
    void shouldReturnSuccessResultWhenAllChangesApply() throws Exception {
        // Given
        StageResult stageResult = createSuccessStageResult("stage-1", 2, 1);
        ExecutionPlan executionPlan = mockNoExecutionRequiredPlan();

        when(pipeline.getSystemStage()).thenReturn(java.util.Optional.empty());
        when(pipeline.getStages()).thenReturn(Collections.singletonList(loadedStage));
        when(loadedStage.getChanges()).thenReturn(Collections.singletonList(loadedChange));
        when(executionPlanner.getNextExecution(any())).thenReturn(executionPlan);

        ExecuteArgs args = new ExecuteArgs(pipeline);

        // When
        ExecuteResult result = operation.execute(args);

        // Then
        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(ExecutionStatus.SUCCESS, result.getData().getStatus());
        verify(eventPublisher, atLeastOnce()).publish(any());
    }

    @Test
    @DisplayName("Should return result with correct counts")
    void shouldReturnResultWithCorrectCounts() throws Exception {
        // Given
        ExecutionPlan executionPlan = mockNoExecutionRequiredPlan();

        when(pipeline.getSystemStage()).thenReturn(java.util.Optional.empty());
        when(pipeline.getStages()).thenReturn(Collections.singletonList(loadedStage));
        when(loadedStage.getChanges()).thenReturn(Arrays.asList(loadedChange, loadedChange, loadedChange));
        when(executionPlanner.getNextExecution(any())).thenReturn(executionPlan);

        ExecuteArgs args = new ExecuteArgs(pipeline);

        // When
        ExecuteResult result = operation.execute(args);

        // Then
        assertNotNull(result);
        assertNotNull(result.getData());
        // With no execution required, we expect no applied changes but success status
        assertEquals(ExecutionStatus.SUCCESS, result.getData().getStatus());
    }

    @Test
    @DisplayName("Should throw StagedExecuteOperationException on stage failure and continue past it")
    void shouldThrowOperationExceptionOnStageFailure() throws Exception {
        // Given
        RuntimeException originalException = new RuntimeException("Change failed");
        StageResult failedStageResult = createFailedStageResult("stage-1", "change-001");
        StageExecutionException stageException = StageExecutionException.fromResult(
                originalException,
                failedStageResult,
                "change-001"
        );

        ExecutableStage executableStage = mock(ExecutableStage.class);
        when(executableStage.getName()).thenReturn("stage-1");
        doNothing().when(executableStage).validate();

        when(stageExecutor.executeStage(any(), any(), any())).thenThrow(stageException);

        ExecutionPlan failingPlan = mockPlanInvokingConsumerWith(executableStage);
        ExecutionPlan continuePlan = mockNoExecutionRequiredPlan();

        when(pipeline.getSystemStage()).thenReturn(java.util.Optional.empty());
        when(pipeline.getStages()).thenReturn(Collections.singletonList(loadedStage));
        when(loadedStage.getName()).thenReturn("stage-1");
        when(loadedStage.getChanges()).thenReturn(Collections.singletonList(loadedChange));
        when(executionPlanner.getNextExecution(any())).thenReturn(failingPlan, continuePlan);

        ExecuteArgs args = new ExecuteArgs(pipeline);

        // When / Then
        StagedExecuteOperationException thrown = assertThrows(StagedExecuteOperationException.class, () -> operation.execute(args));
        assertNotNull(thrown.getResult());
        assertEquals(ExecutionStatus.FAILED, thrown.getResult().getStatus());
        assertEquals(1, thrown.getResult().getFailedStages());
    }

    private StageResult createSuccessStageResult(String stageId, int applied, int skipped) {
        StageResult.Builder builder = StageResult.builder()
                .stageId(stageId)
                .stageName(stageId)
                .state(StageState.COMPLETED)
                .durationMs(100);

        for (int i = 0; i < applied; i++) {
            builder.addChange(ChangeResult.builder()
                    .changeId("change-" + i)
                    .status(ChangeStatus.APPLIED)
                    .durationMs(50)
                    .build());
        }
        for (int i = 0; i < skipped; i++) {
            builder.addChange(ChangeResult.builder()
                    .changeId("change-skipped-" + i)
                    .status(ChangeStatus.ALREADY_APPLIED)
                    .durationMs(10)
                    .build());
        }

        return builder.build();
    }

    private StageResult createFailedStageResult(String stageId, String failedChangeId) {
        return StageResult.builder()
                .stageId(stageId)
                .stageName(stageId)
                .state(StageState.failed(null))
                .durationMs(50)
                .addChange(ChangeResult.builder()
                        .changeId(failedChangeId)
                        .status(ChangeStatus.FAILED)
                        .errorMessage("Test failure")
                        .errorType("RuntimeException")
                        .durationMs(30)
                        .build())
                .build();
    }

    private ExecutionPlan mockNoExecutionRequiredPlan() {
        ExecutionPlan plan = mock(ExecutionPlan.class);
        when(plan.isExecutionRequired()).thenReturn(false);
        doNothing().when(plan).validate();
        doNothing().when(plan).close();
        return plan;
    }

    private ExecutionPlan mockPlanInvokingConsumerWith(ExecutableStage executableStage) {
        ExecutionPlan plan = mock(ExecutionPlan.class);
        when(plan.isExecutionRequired()).thenReturn(true);
        doNothing().when(plan).validate();
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
}
