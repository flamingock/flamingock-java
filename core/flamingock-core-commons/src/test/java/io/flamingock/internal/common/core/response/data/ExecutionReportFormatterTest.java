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
                .totalChanges(1)
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS)
                .startTime(Instant.parse("2026-05-15T08:00:00Z"))
                .endTime(Instant.parse("2026-05-15T08:00:00.075Z"))
                .totalDurationMs(75)
                .totalStages(1).completedStages(1).failedStages(0)
                .totalChanges(1).appliedChanges(1)
                .stages(Collections.singletonList(done))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("Flamingock execution report — SUCCESS"), out);
        assertTrue(out.contains("Stages:    1 total — 1 completed, 0 failed, 0 up to date, 0 not reached"), out);
        assertTrue(out.contains("Changes:   1 total — 1 newly applied, 0 already applied, 0 failed, 0 not reached"), out);
        assertTrue(out.contains("[COMPLETED]"), out);
        assertTrue(out.contains("only-stage"), out);
        assertTrue(out.contains("1 newly applied, 0 already applied, 0 failed, 0 not reached"), out);
        assertFalse(out.contains("Not reached"), "no Not-reached section when all stages reached: " + out);
    }

    @Test
    void reportFailureIncludesErrorBlockAndFailedChangeIds() {
        StageResult failed = StageResult.builder()
                .stageId("bad").stageName("bad-stage")
                .state(StageState.failed(new ErrorInfo("Boom", "kaboom\non two lines", Collections.singletonList("c1"), "bad-stage")))
                .durationMs(42)
                .changes(Collections.singletonList(failedChange("c1", "Boom", "kaboom")))
                .totalChanges(1)
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).failedStages(1)
                .totalChanges(1).failedChanges(1)
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
                .totalChanges(2)
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).failedStages(1)
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
        // Naked StageResult with state=NOT_STARTED (default) does not appear in the breakdown.
        // To exercise the breakdown's defensive path, give it a terminal state (any non-NOT_STARTED).
        StageResult naked = new StageResult();
        naked.setStageName(null);
        naked.setState(StageState.COMPLETED);
        naked.setChanges(null);

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS)
                .completedStages(1)
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
                .totalChanges(6)
                .build();
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).failedStages(1)
                .totalChanges(6).appliedChanges(0).skippedChanges(5).failedChanges(1)
                .totalDurationMs(221)
                .stages(Collections.singletonList(stage))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("0 newly applied, 5 already applied, 1 failed"),
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
    void reportAllUpToDateShowsNoChangesHeadlineAndUpToDateRowsInBreakdown() {
        // Mirrors the Run-2 scenario: every change already applied; planner short-circuited,
        // executor never invoked. Planner stamped UP_TO_DATE and populated ALREADY_APPLIED records.
        StageResult upToDate = StageResult.builder()
                .stageId("database-init").stageName("database-init")
                .state(StageState.NOT_STARTED)
                .totalChanges(6)
                .plannerVerdict(PlannerVerdict.UP_TO_DATE)
                .changes(Arrays.asList(
                        skippedChange("c1"), skippedChange("c2"), skippedChange("c3"),
                        skippedChange("c4"), skippedChange("c5"), skippedChange("c6")))
                .build();
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.NO_CHANGES)
                .totalStages(1).upToDateStages(1).notReachedStages(0).failedStages(0)
                .totalChanges(6).appliedChanges(0).skippedChanges(6).failedChanges(0)
                .stages(Collections.singletonList(upToDate))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("Flamingock execution report — NO CHANGES"),
                "headline must render NO_CHANGES with a space: " + out);
        assertTrue(out.contains("Stages:    1 total — 0 completed, 0 failed, 1 up to date, 0 not reached"), out);
        assertTrue(out.contains("Changes:   6 total — 0 newly applied, 6 already applied, 0 failed, 0 not reached"), out);
        assertTrue(out.contains("Per-stage breakdown:"),
                "per-stage section should include [UP TO DATE] rows: " + out);
        assertTrue(out.contains("[UP TO DATE]"), out);
        assertTrue(out.contains("database-init"), out);
        assertTrue(out.contains("(6 changes already applied)"), out);
        assertFalse(out.contains("Not reached"),
                "Not-reached section must be omitted when nothing reached and nothing not-evaluated: " + out);
    }

    @Test
    void reportAllUpToDateForCloudCaseRendersCountOnlyWhenChangesEmpty() {
        // Cloud's CONTINUE branch marks UP_TO_DATE but does NOT populate per-change records.
        // Per-stage row should render the count from totalChanges instead.
        StageResult upToDate = StageResult.builder()
                .stageId("database-init").stageName("database-init")
                .state(StageState.NOT_STARTED)
                .totalChanges(6)
                .plannerVerdict(PlannerVerdict.UP_TO_DATE)
                .build();
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.NO_CHANGES)
                .totalStages(1).upToDateStages(1)
                .totalChanges(6)
                .stages(Collections.singletonList(upToDate))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("[UP TO DATE]"), out);
        assertTrue(out.contains("(6 changes already applied)"),
                "cloud-style up-to-date row should use totalChanges when per-change list is empty: " + out);
    }

    @Test
    void reportPartialCoverageMixesReachedUpToDateAndNotReached() {
        // Block 1 ran with work; block 2 was up-to-date (planner verdict); block 3 failed mid-way;
        // block 4 never reached.
        StageResult block1 = StageResult.builder()
                .stageId("block-1").stageName("block-1")
                .state(StageState.COMPLETED).durationMs(50)
                .changes(Arrays.asList(appliedChange("c1a"), appliedChange("c1b")))
                .totalChanges(2)
                .build();
        StageResult block2 = StageResult.builder()
                .stageId("block-2").stageName("block-2")
                .state(StageState.NOT_STARTED)
                .totalChanges(3)
                .plannerVerdict(PlannerVerdict.UP_TO_DATE)
                .changes(Arrays.asList(skippedChange("c2a"), skippedChange("c2b"), skippedChange("c2c")))
                .build();
        StageResult block3 = StageResult.builder()
                .stageId("block-3").stageName("block-3")
                .state(StageState.failed(new ErrorInfo("Boom", "kaboom",
                        Collections.singletonList("c3-bad"), "block-3")))
                .durationMs(120)
                .changes(Arrays.asList(appliedChange("c3a"), failedChange("c3-bad", "Boom", "kaboom")))
                .totalChanges(2)
                .build();
        StageResult block4 = StageResult.builder()
                .stageId("block-4").stageName("block-4")
                .state(StageState.NOT_STARTED)
                .totalChanges(3)
                .build();

        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(4).completedStages(1).upToDateStages(1).notReachedStages(1).failedStages(1)
                .totalChanges(10).appliedChanges(3).skippedChanges(3).failedChanges(1).notReachedChanges(3)
                .totalDurationMs(170)
                .stages(Arrays.asList(block1, block2, block3, block4))
                .build();

        String out = ExecutionReportFormatter.report(result);
        assertTrue(out.contains("Stages:    4 total — 1 completed, 1 failed, 1 up to date, 1 not reached"), out);
        assertTrue(out.contains("Changes:   10 total — 3 newly applied, 3 already applied, 1 failed, 3 not reached"), out);
        assertTrue(out.contains("Per-stage breakdown:"), out);
        assertTrue(out.contains("[COMPLETED] block-1"), out);
        assertTrue(out.contains("[UP TO DATE]") && out.contains("block-2"), out);
        assertTrue(out.contains("[FAILED]") && out.contains("block-3"), out);
        assertFalse(out.contains("[NOT REACHED] block-4"),
                "unreached stages must NOT appear in per-stage breakdown: " + out);
        assertTrue(out.contains("Not reached (1):"), out);
        assertTrue(out.contains("- block-4 (3 changes)"),
                "Not-reached entry must include the structural change count: " + out);
    }

    @Test
    void summaryRendersNoChangesHeadlineWhenNothingReached() {
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.NO_CHANGES)
                .totalStages(1)
                .totalChanges(6)
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
