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
package io.flamingock.internal.core.pipeline.run;

import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineRunToResponseTest {

    @Test
    void emptyRunYieldsSuccessWithZeroCounts() {
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.<AbstractLoadedStage>emptyList());
        pipelineRun.start();
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        assertEquals(ExecutionStatus.SUCCESS, response.getStatus());
        assertEquals(0, response.getTotalStages());
        assertEquals(0, response.getCompletedStages());
        assertEquals(0, response.getFailedStages());
        assertEquals(0, response.getTotalChanges());
        assertNull(response.getError());
        assertNotNull(response.getStartTime());
        assertNotNull(response.getEndTime());
    }

    @Test
    void twoCompletedStagesYieldsSuccessAndCountersRollUp() {
        AbstractLoadedStage stageA = mockStage("alpha");
        AbstractLoadedStage stageB = mockStage("beta");
        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(stageA, stageB));

        pipelineRun.start();
        pipelineRun.markStageCompleted("alpha", completedStageResult("alpha", 2 /*applied*/, 1 /*skipped*/));
        pipelineRun.markStageCompleted("beta", completedStageResult("beta", 1, 0));
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        assertEquals(ExecutionStatus.SUCCESS, response.getStatus());
        assertEquals(2, response.getTotalStages());
        assertEquals(2, response.getCompletedStages());
        assertEquals(0, response.getFailedStages());
        assertEquals(4, response.getTotalChanges());     // 2+1 + 1+0
        assertEquals(3, response.getAppliedChanges());   // 2 + 1
        assertEquals(1, response.getSkippedChanges());   // 1 + 0
        assertEquals(0, response.getFailedChanges());
        assertNull(response.getError());
    }

    @Test
    void oneCompletedAndOneFailedYieldsFailedAndPipelineErrorMatchesStage() {
        AbstractLoadedStage stageA = mockStage("alpha");
        AbstractLoadedStage stageB = mockStage("beta");
        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(stageA, stageB));

        RuntimeException betaCause = new RuntimeException("boom");
        StageExecutionException betaException = StageExecutionException.fromResult(
                betaCause, failedStageResult("beta"), "change-b1");

        pipelineRun.start();
        pipelineRun.markStageCompleted("alpha", completedStageResult("alpha", 1, 0));
        pipelineRun.markStageFailed("beta", betaException);
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        assertEquals(ExecutionStatus.FAILED, response.getStatus());
        assertEquals(2, response.getTotalStages());
        assertEquals(1, response.getCompletedStages());
        assertEquals(1, response.getFailedStages());
        assertEquals(2, response.getTotalChanges());     // 1 applied + 1 failed
        assertEquals(1, response.getAppliedChanges());
        assertEquals(1, response.getFailedChanges());
        ErrorInfo error = response.getError();
        assertEquals(java.util.Collections.singletonList("change-b1"), error.getChangeIds());
        assertEquals("beta", error.getStageId());
        assertEquals("RuntimeException", error.getErrorType());
        assertEquals("boom", error.getMessage());
    }

    @Test
    void pipelineFailedWithNoStageFailuresYieldsFailedAndPipelineError() {
        AbstractLoadedStage stageA = mockStage("alpha");
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(stageA));

        pipelineRun.start();
        pipelineRun.markPipelineFailed(new RuntimeException("lock not acquired"));
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        assertEquals(ExecutionStatus.FAILED, response.getStatus());
        // Stage was NOT_STARTED — it appears in response.stages with state=NOT_STARTED, but
        // it doesn't count toward failedStages/completedStages.
        assertEquals(1, response.getTotalStages());
        assertEquals(0, response.getCompletedStages());
        assertEquals(0, response.getFailedStages());
        assertTrue(response.getStages().get(0).getState().isNotStarted());
        ErrorInfo error = response.getError();
        assertEquals("RuntimeException", error.getErrorType());
        assertEquals("lock not acquired", error.getMessage());
    }

    @Test
    void stageErrorTakesPrecedenceOverPipelineError() {
        AbstractLoadedStage stageA = mockStage("alpha");
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(stageA));

        RuntimeException stageCause = new RuntimeException("stage boom");
        StageExecutionException stageException = StageExecutionException.fromResult(
                stageCause, failedStageResult("alpha"), "change-1");

        pipelineRun.start();
        pipelineRun.markStageFailed("alpha", stageException);
        pipelineRun.markPipelineFailed(new IllegalStateException("late pipeline error"));
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        assertEquals(ExecutionStatus.FAILED, response.getStatus());
        ErrorInfo error = response.getError();
        // Stage-level error wins over pipeline-level
        assertEquals("RuntimeException", error.getErrorType());
        assertEquals("stage boom", error.getMessage());
        assertEquals("alpha", error.getStageId());
    }

    @Test
    void blockedForMIStageIsTrackedInResponseWithBlockedState() {
        AbstractLoadedChange c1 = mockChange("c1");
        AbstractLoadedStage alpha = mockStageWithChanges("alpha", c1);
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(alpha));

        pipelineRun.start();
        pipelineRun.markStageBlockedFromMI(
                "alpha",
                java.util.Arrays.asList(new RecoveryIssue("c1")));
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        // Blocked stage appears in response.stages with its BlockedForMI state (carrying the
        // RecoveryIssue list). It also contributes to failedStages because BlockedForMI.isFailed()
        // returns true (preserves today's FAILED pipeline status).
        assertEquals(1, response.getTotalStages());
        assertEquals(1, response.getStages().size());
        assertEquals(1, response.getFailedStages());
        assertTrue(response.getStages().get(0).getState().isBlockedForManualIntervention());
        assertEquals(1, response.getStages().get(0).getState().getRecoveryIssues().size());
        // Pipeline-level failure surfaces via pipelineError as today.
        assertEquals(ExecutionStatus.FAILED, response.getStatus());
        assertNotNull(response.getError());
    }

    @Test
    void notStartedStagesAreReportedInResponseWithNotStartedState() {
        AbstractLoadedStage stageA = mockStage("alpha");
        AbstractLoadedStage stageB = mockStage("beta");
        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(stageA, stageB));

        pipelineRun.start();
        pipelineRun.markStageCompleted("alpha", completedStageResult("alpha", 1, 0));
        // beta never advances past NOT_STARTED — still appears in the response.
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        assertEquals(2, response.getTotalStages());
        assertEquals(1, response.getCompletedStages());
        assertEquals(0, response.getFailedStages());
        assertEquals(2, response.getStages().size());
        assertTrue(response.getStages().get(0).getState().isCompleted());
        assertTrue(response.getStages().get(1).getState().isNotStarted());
    }

    private static AbstractLoadedStage mockStage(String name) {
        AbstractLoadedStage stage = mock(AbstractLoadedStage.class);
        when(stage.getName()).thenReturn(name);
        return stage;
    }

    private static AbstractLoadedStage mockStageWithChanges(String name, AbstractLoadedChange... changes) {
        AbstractLoadedStage stage = mock(AbstractLoadedStage.class);
        when(stage.getName()).thenReturn(name);
        when(stage.getChanges()).thenReturn(Arrays.asList(changes));
        return stage;
    }

    private static AbstractLoadedChange mockChange(String id) {
        AbstractLoadedChange change = mock(AbstractLoadedChange.class);
        when(change.getId()).thenReturn(id);
        return change;
    }

    private static StageResult completedStageResult(String name, int applied, int skipped) {
        StageResult.Builder builder = StageResult.builder()
                .stageId(name)
                .stageName(name)
                .state(StageState.COMPLETED);
        for (int i = 0; i < applied; i++) {
            builder.addChange(ChangeResult.builder().changeId(name + "-a" + i).status(ChangeStatus.APPLIED).build());
        }
        for (int i = 0; i < skipped; i++) {
            builder.addChange(ChangeResult.builder().changeId(name + "-s" + i).status(ChangeStatus.ALREADY_APPLIED).build());
        }
        return builder.build();
    }

    private static StageResult failedStageResult(String name) {
        return StageResult.builder()
                .stageId(name)
                .stageName(name)
                .state(StageState.failed(null))
                .addChange(ChangeResult.builder().changeId(name + "-f0").status(ChangeStatus.FAILED).build())
                .build();
    }
}
