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
package io.flamingock.internal.common.core.response.data;

import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionReportFormatterTest {

    @Test
    void summaryOnNullReturnsSafeFallback() {
        String out = ExecutionReportFormatter.summary(null);
        assertTrue(out.contains("no execution data"));
    }

    @Test
    void summaryOnSuccess() {
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS)
                .totalStages(2)
                .completedStages(2)
                .failedStages(0)
                .totalChanges(3)
                .appliedChanges(3)
                .skippedChanges(0)
                .failedChanges(0)
                .totalDurationMs(120)
                .build();

        String out = ExecutionReportFormatter.summary(result);
        assertTrue(out.startsWith("Flamingock execution completed:"), out);
        assertTrue(out.contains("applied=3"), out);
        assertTrue(out.contains("duration=120ms"), out);
        assertFalse(out.contains("manual intervention"), out);
    }

    @Test
    void summaryOnStageFailureListsFailedStageNames() {
        StageResult completed = StageResult.builder()
                .stageId("ok").stageName("ok-stage").state(StageState.COMPLETED).durationMs(50)
                .build();
        StageResult failed = StageResult.builder()
                .stageId("bad").stageName("bad-stage")
                .state(StageState.failed(new ErrorInfo("Boom", "kaboom", Collections.singletonList("c1"), "bad-stage")))
                .durationMs(70)
                .changes(Collections.singletonList(failedChange("c1", "Boom", "kaboom")))
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(2).completedStages(1).failedStages(1)
                .totalChanges(2).appliedChanges(1).failedChanges(1)
                .totalDurationMs(120)
                .stages(Arrays.asList(completed, failed))
                .build();

        String out = ExecutionReportFormatter.summary(result);
        assertTrue(out.startsWith("Flamingock execution failed: 1 of 2 stage(s) failed [bad-stage]"), out);
        assertTrue(out.contains("failed=1"), out);
    }

    @Test
    void summaryAppendsManualInterventionChangeIds() {
        StageResult blocked = StageResult.builder()
                .stageId("mi").stageName("mi-stage")
                .state(StageState.blockedManualIntervention("mi-stage",
                        Arrays.asList(new RecoveryIssue("change-a"), new RecoveryIssue("change-b"))))
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).failedStages(1)
                .totalChanges(2).failedChanges(0)
                .totalDurationMs(10)
                .stages(Collections.singletonList(blocked))
                .build();

        String out = ExecutionReportFormatter.summary(result);
        assertTrue(out.contains("manual intervention required for change(s): change-a, change-b"), out);
    }

    @Test
    void reportSuccessHeadlineAndPerStageBlock() {
        StageResult done = StageResult.builder()
                .stageId("only").stageName("only-stage")
                .state(StageState.COMPLETED).durationMs(75)
                .changes(Collections.singletonList(appliedChange("c1")))
                .wasExecuted(true).totalChanges(1)
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS)
                .startTime(Instant.parse("2026-05-15T08:00:00Z"))
                .endTime(Instant.parse("2026-05-15T08:00:00.075Z"))
                .totalDurationMs(75)
                .totalStages(1).reachedStages(1).completedStages(1).failedStages(0)
                .totalChanges(1).reachedChanges(1).appliedChanges(1)
                .stages(Collections.singletonList(done))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("Flamingock execution report — SUCCESS"), out);
        assertTrue(out.contains("Stages:    1 total — 1 reached, 1 completed, 0 failed"), out);
        assertTrue(out.contains("Changes:   1 total — 1 reached, 1 applied, 0 skipped, 0 failed"), out);
        assertTrue(out.contains("[COMPLETED]"), out);
        assertTrue(out.contains("only-stage"), out);
        assertTrue(out.contains("1 applied, 0 skipped, 0 failed"), out);
        assertFalse(out.contains("Not reached"), "no Not-reached section when all stages reached: " + out);
    }

    @Test
    void reportFailureIncludesErrorBlockAndFailedChangeIds() {
        StageResult failed = StageResult.builder()
                .stageId("bad").stageName("bad-stage")
                .state(StageState.failed(new ErrorInfo("Boom", "kaboom\non two lines", Collections.singletonList("c1"), "bad-stage")))
                .durationMs(42)
                .changes(Collections.singletonList(failedChange("c1", "Boom", "kaboom")))
                .wasExecuted(true).totalChanges(1)
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).reachedStages(1).failedStages(1)
                .totalChanges(1).reachedChanges(1).failedChanges(1)
                .totalDurationMs(42)
                .stages(Collections.singletonList(failed))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("Flamingock execution report — FAILED"), out);
        assertTrue(out.contains("[FAILED]"), out);
        assertTrue(out.contains("error:   Boom"), out);
        assertTrue(out.contains("kaboom"), out);
        assertTrue(out.contains("on two lines"), out);
        assertTrue(out.contains("failed change(s): c1"), out);
    }

    @Test
    void reportBlockedForMIShowsChangeIdsAndDistinctLabel() {
        StageResult blocked = StageResult.builder()
                .stageId("mi").stageName("mi-stage")
                .state(StageState.blockedManualIntervention("mi-stage",
                        Arrays.asList(new RecoveryIssue("change-a"), new RecoveryIssue("change-b"))))
                .wasExecuted(true).totalChanges(2)
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).reachedStages(1).failedStages(1)
                .totalChanges(2)
                .stages(Collections.singletonList(blocked))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("[BLOCKED — manual intervention required]"), out);
        assertTrue(out.contains("change(s) requiring intervention: change-a, change-b"), out);
    }

    @Test
    void reportOnNullReturnsSafeBanner() {
        String out = ExecutionReportFormatter.report(null);
        assertNotNull(out);
        assertTrue(out.contains("NO DATA"), out);
    }

    @Test
    void reportToleratesNullStagesAndPartialData() {
        ExecuteResponseData result = new ExecuteResponseData();
        result.setStages(null);
        // Should not throw.
        String out = ExecutionReportFormatter.report(result);
        assertNotNull(out);
    }

    @Test
    void reportToleratesStageWithNullStateAndNullChanges() {
        StageResult naked = new StageResult();
        naked.setStageName(null);
        naked.setState(null);
        naked.setChanges(null);
        // Mark reached so the breakdown renders it — that's the path being exercised here
        // (defensive rendering of weird stage data). Unreached stages skip the breakdown entirely.
        naked.setWasExecuted(true);

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS)
                .reachedStages(1)
                .stages(Collections.singletonList(naked))
                .build();

        // Should not throw despite null state/name/changes.
        String out = ExecutionReportFormatter.report(result);
        assertNotNull(out);
        assertTrue(out.contains("(unnamed)"), out);
    }

    @Test
    void reportCountsRolledBackChangeAsFailedAndListsItsId() {
        // Scenario mirrors the real-world MongoDB duplicate-key case: 5 already-applied changes
        // plus 1 transactional change that failed and was auto-rolled-back (status ROLLED_BACK).
        // The user-facing report must show 1 failed and list the change ID.
        StageResult stage = StageResult.builder()
                .stageId("database-init").stageName("database-init")
                .state(StageState.failed(new ErrorInfo("MongoWriteException",
                        "E11000 duplicate key", Collections.singletonList("InsertSeedData"),
                        "database-init")))
                .durationMs(126)
                .changes(Arrays.asList(
                        skippedChange("CreateCustomersTable"),
                        skippedChange("CreateOrdersTable"),
                        skippedChange("InsertSeedData"),
                        skippedChange("CreateEmployeesTable"),
                        skippedChange("CreateEmployeesCollection"),
                        rolledBackChange("InsertEmployeeSeedData", "MongoWriteException", "E11000 duplicate key")))
                .wasExecuted(true).totalChanges(6)
                .build();
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).reachedStages(1).failedStages(1)
                .totalChanges(6).reachedChanges(6).appliedChanges(0).skippedChanges(5).failedChanges(1)
                .totalDurationMs(221)
                .stages(Collections.singletonList(stage))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("0 applied, 5 skipped, 1 failed"),
                "per-stage block must count ROLLED_BACK as failed: " + out);
        assertTrue(out.contains("failed change(s): InsertEmployeeSeedData"),
                "failed change(s) line must include the rolled-back change ID: " + out);
    }

    @Test
    void summaryCountsRolledBackChangeAsFailed() {
        StageResult stage = StageResult.builder()
                .stageId("database-init").stageName("database-init")
                .state(StageState.failed(new ErrorInfo("MongoWriteException", "boom",
                        Collections.singletonList("InsertEmployeeSeedData"), "database-init")))
                .changes(Collections.singletonList(rolledBackChange("InsertEmployeeSeedData", "MongoWriteException", "boom")))
                .build();
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).failedStages(1)
                .totalChanges(1).failedChanges(1)
                .stages(Collections.singletonList(stage))
                .build();

        String out = ExecutionReportFormatter.summary(result);
        assertTrue(out.contains("failed=1"), "summary must reflect ROLLED_BACK in failed=N: " + out);
    }

    @Test
    void reportAllUpToDateShowsNoChangesHeadlineAndOmitsPerStageBreakdown() {
        // Mirrors the Run-2 scenario: every change already applied; planner short-circuited,
        // executor never invoked. Stage stays wasExecuted=false.
        StageResult upToDate = StageResult.builder()
                .stageId("database-init").stageName("database-init")
                .state(StageState.NOT_STARTED)
                .wasExecuted(false).totalChanges(6)
                .build();
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.NO_CHANGES)
                .totalStages(1).reachedStages(0).completedStages(0).failedStages(0)
                .totalChanges(6).reachedChanges(0)
                .stages(Collections.singletonList(upToDate))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("Flamingock execution report — NO CHANGES"),
                "headline must render NO_CHANGES with a space: " + out);
        assertTrue(out.contains("Stages:    1 total — 0 reached"), out);
        assertTrue(out.contains("Changes:   6 total — 0 reached"), out);
        assertFalse(out.contains("Per-stage breakdown:"),
                "per-stage section must be omitted when no stages reached: " + out);
        assertFalse(out.contains("Not reached"),
                "Not-reached section must be omitted when nothing reached at all (NO_CHANGES headline carries it): " + out);
    }

    @Test
    void reportPartialCoverageListsNotReachedStagesSeparately() {
        // Block 1 succeeds with work; block 2 fails; block 3 never reached.
        StageResult block1 = StageResult.builder()
                .stageId("block-1-stage").stageName("block-1-stage")
                .state(StageState.COMPLETED).durationMs(50)
                .changes(Arrays.asList(appliedChange("c1a"), appliedChange("c1b")))
                .wasExecuted(true).totalChanges(2)
                .build();
        StageResult block2 = StageResult.builder()
                .stageId("block-2-stage").stageName("block-2-stage")
                .state(StageState.failed(new ErrorInfo("Boom", "kaboom",
                        Collections.singletonList("c2-bad"), "block-2-stage")))
                .durationMs(120)
                .changes(Arrays.asList(appliedChange("c2a"), failedChange("c2-bad", "Boom", "kaboom")))
                .wasExecuted(true).totalChanges(2)
                .build();
        StageResult block3 = StageResult.builder()
                .stageId("block-3-stage").stageName("block-3-stage")
                .state(StageState.NOT_STARTED)
                .wasExecuted(false).totalChanges(3)
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(3).reachedStages(2).completedStages(1).failedStages(1)
                .totalChanges(7).reachedChanges(4).appliedChanges(3).skippedChanges(0).failedChanges(1)
                .totalDurationMs(170)
                .stages(Arrays.asList(block1, block2, block3))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("Stages:    3 total — 2 reached, 1 completed, 1 failed"), out);
        assertTrue(out.contains("Changes:   7 total — 4 reached, 3 applied, 0 skipped, 1 failed"), out);
        assertTrue(out.contains("Per-stage breakdown:"), out);
        assertTrue(out.contains("[COMPLETED] block-1-stage"), out);
        assertTrue(out.contains("[FAILED]") && out.contains("block-2-stage"), out);
        assertFalse(out.contains("[PENDING]   block-3-stage"),
                "unreached stages must NOT appear in per-stage breakdown: " + out);
        assertTrue(out.contains("Not reached (1):"), out);
        assertTrue(out.contains("- block-3-stage (3 changes)"),
                "Not-reached entry must include the structural change count: " + out);
    }

    @Test
    void summaryRendersNoChangesHeadlineWhenNothingReached() {
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.NO_CHANGES)
                .totalStages(1).reachedStages(0)
                .totalChanges(6).reachedChanges(0)
                .totalDurationMs(32)
                .stages(Collections.emptyList())
                .build();

        String out = ExecutionReportFormatter.summary(result);
        assertTrue(out.startsWith("Flamingock execution: no changes"), out);
        assertTrue(out.contains("1 stage(s) already up to date"), out);
        assertTrue(out.contains("duration=32ms"), out);
        assertFalse(out.contains("failed="), "no failure counts should appear in NO_CHANGES summary: " + out);
    }

    private static ChangeResult appliedChange(String id) {
        return ChangeResult.builder().changeId(id).status(ChangeStatus.APPLIED).build();
    }

    private static ChangeResult skippedChange(String id) {
        return ChangeResult.builder().changeId(id).status(ChangeStatus.ALREADY_APPLIED).build();
    }

    private static ChangeResult failedChange(String id, String errorType, String errorMessage) {
        return ChangeResult.builder()
                .changeId(id)
                .status(ChangeStatus.FAILED)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .build();
    }

    private static ChangeResult rolledBackChange(String id, String errorType, String errorMessage) {
        return ChangeResult.builder()
                .changeId(id)
                .status(ChangeStatus.ROLLED_BACK)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .build();
    }
}
