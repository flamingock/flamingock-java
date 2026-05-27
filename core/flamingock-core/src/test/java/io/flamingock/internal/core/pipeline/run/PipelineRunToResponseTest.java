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
import io.flamingock.internal.common.core.response.data.PlannerVerdict;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineRunToResponseTest {

    @Test
    void emptyRunYieldsNoChangesWithZeroCounts() {
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.<AbstractLoadedStage>emptyList());
        pipelineRun.start();
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        // Empty pipeline → nothing reached, nothing failed → NO_CHANGES (not SUCCESS).
        assertEquals(ExecutionStatus.NO_CHANGES, response.getStatus());
        assertEquals(0, response.getTotalStages());
        assertEquals(0, response.getCompletedStages());
        assertEquals(0, response.getFailedStages());
        assertEquals(0, response.getTotalChanges());
        assertNotNull(response.getStartTime());
        assertNotNull(response.getEndTime());
    }

    @Test
    void twoCompletedStagesYieldsSuccessAndCountersRollUp() {
        AbstractLoadedStage stageA = mockStageWithChangeCount("alpha", 3);
        AbstractLoadedStage stageB = mockStageWithChangeCount("beta", 1);
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
        assertEquals(4, response.getTotalChanges());     // 3 (loaded alpha) + 1 (loaded beta)
        assertEquals(3, response.getAppliedChanges());   // 2 + 1
        assertEquals(1, response.getSkippedChanges());   // 1 + 0
        assertEquals(0, response.getFailedChanges());
    }

    @Test
    void oneCompletedAndOneFailedYieldsFailedAndPipelineErrorMatchesStage() {
        AbstractLoadedStage stageA = mockStageWithChangeCount("alpha", 1);
        AbstractLoadedStage stageB = mockStageWithChangeCount("beta", 1);
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
        assertEquals(2, response.getTotalChanges());     // 1 (loaded alpha) + 1 (loaded beta)
        assertEquals(1, response.getAppliedChanges());
        assertEquals(1, response.getFailedChanges());

        // Per-stage error info lives on the StageResult's state, accessed via getErrorInfo().
        ErrorInfo error = response.getStages().get(1).getState().getErrorInfo().get();
        assertEquals(java.util.Collections.singletonList("change-b1"), error.getChangeIds());
        assertEquals("beta", error.getStageId());
        assertEquals("RuntimeException", error.getErrorType());
        assertEquals("boom", error.getMessage());
    }

    @Test
    void blockedForMIStageIsTrackedInResponseWithBlockedState() {
        AbstractLoadedChange c1 = mockChange("c1");
        AbstractLoadedStage alpha = mockStageWithChanges("alpha", c1);
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(alpha));

        pipelineRun.start();
        pipelineRun.markStageStarted("alpha");
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
        // BlockedForMI is a Failed subtype → counts toward failedStages → status FAILED.
        assertEquals(ExecutionStatus.FAILED, response.getStatus());
    }

    @Test
    void notStartedStagesAreReportedInResponseWithNotStartedState() {
        AbstractLoadedStage stageA = mockStageWithChangeCount("alpha", 1);
        AbstractLoadedStage stageB = mockStageWithChangeCount("beta", 2);
        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(stageA, stageB));

        pipelineRun.start();
        pipelineRun.markStageCompleted("alpha", completedStageResult("alpha", 1, 0));
        // beta never advances past NOT_STARTED — still appears in the response as unreached.
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        assertEquals(2, response.getTotalStages());
        assertEquals(1, response.getCompletedStages());
        assertEquals(0, response.getFailedStages());
        assertEquals(1, response.getNotReachedStages());
        assertEquals(2, response.getStages().size());
        assertTrue(response.getStages().get(0).getState().isCompleted());
        assertTrue(response.getStages().get(1).getState().isNotStarted());
    }

    @Test
    void rolledBackChangeIsCountedAsFailedInAggregate() {
        // Mirrors the real-world MongoDB duplicate-key case: a transactional change failed and was
        // auto-rolled-back. PipelineRun must count it in failedChanges (not silently drop it).
        AbstractLoadedStage stage = mockStageWithChangeCount("database-init", 3);
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(stage));

        StageResult stageResult = StageResult.builder()
                .stageId("database-init").stageName("database-init")
                .state(StageState.failed(null))
                .addChange(ChangeResult.builder().changeId("c1").status(ChangeStatus.ALREADY_APPLIED).build())
                .addChange(ChangeResult.builder().changeId("c2").status(ChangeStatus.ALREADY_APPLIED).build())
                .addChange(ChangeResult.builder().changeId("c3").status(ChangeStatus.ROLLED_BACK).build())
                .build();

        pipelineRun.start();
        pipelineRun.markStageFailed("database-init", StageExecutionException.fromResult(
                new RuntimeException("boom"), stageResult, "c3"));
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();
        assertEquals(3, response.getTotalChanges());
        assertEquals(0, response.getAppliedChanges());
        assertEquals(2, response.getSkippedChanges());
        assertEquals(1, response.getFailedChanges(),
                "ROLLED_BACK must be counted as failed in the user-facing aggregate");
    }

    @Test
    void allStagesUnreachedYieldsNoChangesAndCarriesStructuralTotalChanges() {
        // Run-2 case: every change in the pipeline is already applied; planner short-circuits,
        // runStage never invoked. Stages stay NOT_STARTED. The response must carry the structural
        // totalChanges (6) so the report doesn't read "0 of nothing", and status must be NO_CHANGES.
        AbstractLoadedStage stage = mockStageWithChangeCount("database-init", 6);
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(stage));

        pipelineRun.start();
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();

        assertEquals(ExecutionStatus.NO_CHANGES, response.getStatus());
        assertEquals(1, response.getTotalStages());
        assertEquals(1, response.getNotReachedStages());
        assertEquals(6, response.getTotalChanges());
        assertEquals(0, response.getAppliedChanges());
        assertEquals(0, response.getFailedChanges());
        assertTrue(response.getStages().get(0).getState().isNotStarted());
        // Structural change count stamped on the StageResult so the formatter's "Not reached" row
        // can render "(N changes)" even though the per-change list is empty.
        assertEquals(6, response.getStages().get(0).getTotalChanges());
    }

    @Test
    void upToDateVerdictWithAuditPopulatedChangesAggregatesAsExpected() {
        // Community case: planner marks UP_TO_DATE and populates ALREADY_APPLIED records from
        // audit. Aggregate must show: 1 stage up-to-date, 0 reached, 6 changes already at target.
        AbstractLoadedStage stage = mockStageWithChangeCount("database-init", 6);
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(stage));

        pipelineRun.start();
        pipelineRun.markStageAlreadyAppliedFromAudit("database-init",
                Arrays.asList("database-init-c0", "database-init-c1", "database-init-c2",
                              "database-init-c3", "database-init-c4", "database-init-c5"));
        pipelineRun.markStageVerdict("database-init", PlannerVerdict.UP_TO_DATE);
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();
        assertEquals(ExecutionStatus.NO_CHANGES, response.getStatus());
        assertEquals(1, response.getUpToDateStages());
        assertEquals(0, response.getNotReachedStages());
        assertEquals(6, response.getSkippedChanges(),
                "ALREADY_APPLIED records added by the planner must roll up into skippedChanges");
        assertEquals(0, response.getAppliedChanges());
        assertEquals(0, response.getFailedChanges());
        assertEquals(PlannerVerdict.UP_TO_DATE, response.getStages().get(0).getPlannerVerdict());
    }

    @Test
    void markStageAlreadyAppliedFromAuditDefensiveMergeRespectsOperationWrites() {
        // Operation already recorded alpha-c0 as APPLIED. Planner then attempts to add the same id
        // as ALREADY_APPLIED on re-evaluation. Defensive merge: operation's APPLIED must stand.
        // mockStageWithChangeCount generates IDs as "${stageName}-c${i}", so the 2 changes are
        // alpha-c0 and alpha-c1; each starts as NOT_REACHED at PipelineRun.of() construction.
        AbstractLoadedStage stage = mockStageWithChangeCount("alpha", 2);
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(stage));

        StageResult executorOutput = StageResult.builder()
                .stageId("alpha").stageName("alpha")
                .state(StageState.COMPLETED)
                .addChange(ChangeResult.builder().changeId("alpha-c0").status(ChangeStatus.APPLIED).build())
                .build();

        pipelineRun.start();
        pipelineRun.markStageCompleted("alpha", executorOutput);
        // Planner re-evaluates and would mark both as ALREADY_APPLIED. Defensive merge:
        //   alpha-c0 has APPLIED already (operation wrote it) → stays APPLIED.
        //   alpha-c1 is still NOT_REACHED → upgraded to ALREADY_APPLIED.
        pipelineRun.markStageAlreadyAppliedFromAudit("alpha", Arrays.asList("alpha-c0", "alpha-c1"));
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();
        assertEquals(1, response.getAppliedChanges(), "alpha-c0 stays APPLIED");
        assertEquals(1, response.getSkippedChanges(), "alpha-c1 becomes ALREADY_APPLIED");
        assertEquals(0, response.getNotReachedChanges(), "no NOT_REACHED records left");
        assertEquals(2, response.getStages().get(0).getChanges().size());
    }

    @Test
    void midStageFailurePreservesNotReachedForUnprocessedChanges() {
        // Mirrors the user's real-world SQL scenario: stage has 5 loaded changes; executor
        // processed 3 (2 APPLIED + 1 FAILED) before stopping on the failure. The two
        // unprocessed changes should remain NOT_REACHED so the report can show them explicitly.
        AbstractLoadedStage stage = mockStageWithChangeCount("database-init", 5);
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(stage));

        // Executor's view: 2 APPLIED + 1 FAILED; nothing for the trailing 2 changes.
        StageResult executorOutput = StageResult.builder()
                .stageId("database-init").stageName("database-init")
                .state(StageState.failed(null))
                .addChange(ChangeResult.builder().changeId("database-init-c0").status(ChangeStatus.APPLIED).build())
                .addChange(ChangeResult.builder().changeId("database-init-c1").status(ChangeStatus.APPLIED).build())
                .addChange(ChangeResult.builder().changeId("database-init-c2").status(ChangeStatus.FAILED).build())
                .build();
        StageExecutionException exception = StageExecutionException.fromResult(
                new RuntimeException("boom"), executorOutput, "database-init-c2");

        pipelineRun.start();
        pipelineRun.markStageFailed("database-init", exception);
        pipelineRun.stop();

        ExecuteResponseData response = pipelineRun.toResponse();
        assertEquals(5, response.getTotalChanges());
        assertEquals(2, response.getAppliedChanges());
        assertEquals(0, response.getSkippedChanges());
        assertEquals(1, response.getFailedChanges());
        assertEquals(2, response.getNotReachedChanges(),
                "trailing unprocessed changes must remain NOT_REACHED after merge");
        // Per-stage records contain entries for ALL 5 loaded changes.
        assertEquals(5, response.getStages().get(0).getChanges().size());
    }

    @Test
    void markStageVerdictIsMonotone() {
        AbstractLoadedStage stage = mockStageWithChangeCount("alpha", 1);
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(stage));

        pipelineRun.markStageVerdict("alpha", PlannerVerdict.UP_TO_DATE);
        // Attempt to downgrade — must be silently ignored.
        pipelineRun.markStageVerdict("alpha", PlannerVerdict.NEEDS_WORK);

        ExecuteResponseData response = pipelineRun.toResponse();
        assertEquals(PlannerVerdict.UP_TO_DATE, response.getStages().get(0).getPlannerVerdict());
    }

    private static AbstractLoadedStage mockStage(String name) {
        AbstractLoadedStage stage = mock(AbstractLoadedStage.class);
        when(stage.getName()).thenReturn(name);
        // toResponse() reads loaded-stage change count to populate the structural totalChanges field.
        // Default to empty list for tests that don't care; tests that assert totalChanges should
        // use mockStageWithChangeCount(name, n) instead.
        when(stage.getChanges()).thenReturn(java.util.Collections.emptyList());
        return stage;
    }

    private static AbstractLoadedStage mockStageWithChangeCount(String name, int changeCount) {
        AbstractLoadedStage stage = mock(AbstractLoadedStage.class);
        when(stage.getName()).thenReturn(name);
        java.util.List<AbstractLoadedChange> changes = new java.util.ArrayList<>(changeCount);
        for (int i = 0; i < changeCount; i++) {
            changes.add(mockChange(name + "-c" + i));
        }
        when(stage.getChanges()).thenReturn(changes);
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
