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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Canonical renderer for {@link ExecuteResponseData}. Two outputs:
 *
 * <ul>
 *   <li>{@link #summary(ExecuteResponseData)} — single-line, log-aggregator-friendly.
 *       Used by {@code ExecuteOperationException#getMessage()}.</li>
 *   <li>{@link #report(ExecuteResponseData)} — multi-line, fixed-width block intended for SLF4J
 *       streams and {@code ExecuteOperationException#toString()}.</li>
 * </ul>
 *
 * <p>Both methods are fully defensive: null result, empty stages, missing per-stage state, and
 * partial data are tolerated; the formatter never throws. The default event listener and the
 * exception {@code toString()} both depend on this contract.
 */
public final class ExecutionReportFormatter {

    private static final String LINE = "========================================================================";
    private static final String NEWLINE = System.lineSeparator();

    private ExecutionReportFormatter() {
    }

    public static String summary(ExecuteResponseData result) {
        if (result == null) {
            return "Flamingock execution: no execution data available";
        }

        List<StageResult> stages = nonNullStages(result);
        List<String> failedStageNames = failedStageNames(stages);
        List<String> miChangeIds = manualInterventionChangeIds(stages);

        String headline;
        if (isFailedStatus(result.getStatus()) || !failedStageNames.isEmpty()) {
            headline = String.format(
                    "Flamingock execution failed: %d of %d stage(s) failed [%s]",
                    result.getFailedStages(),
                    result.getTotalStages(),
                    String.join(", ", failedStageNames));
        } else if (result.getStatus() == ExecutionStatus.NO_CHANGES) {
            return String.format(
                    "Flamingock execution: no changes — %d stage(s) already up to date; duration=%dms",
                    result.getTotalStages(),
                    result.getTotalDurationMs());
        } else {
            headline = String.format(
                    "Flamingock execution completed: %d stage(s)",
                    result.getTotalStages());
        }

        String counts = String.format(
                "; changes newly_applied=%d, already_applied=%d, failed=%d, not_reached=%d; duration=%dms",
                result.getAppliedChanges(),
                result.getAlreadyAppliedChanges(),
                result.getFailedChanges(),
                result.getNotReachedChanges(),
                result.getTotalDurationMs());

        StringBuilder sb = new StringBuilder(headline).append(counts);
        if (!miChangeIds.isEmpty()) {
            sb.append("; manual intervention required for change(s): ")
              .append(String.join(", ", miChangeIds));
        }
        return sb.toString();
    }

    public static String report(ExecuteResponseData result) {
        if (result == null) {
            return banner("Flamingock execution report — NO DATA");
        }

        StringBuilder sb = new StringBuilder();
        String statusLabel = statusLabel(result);
        sb.append(LINE).append(NEWLINE)
          .append(" Flamingock execution report — ").append(statusLabel).append(NEWLINE)
          .append(LINE).append(NEWLINE);

        appendTimeBlock(sb, result);
        sb.append(NEWLINE);

        sb.append(" Stages:    ")
          .append(result.getTotalStages()).append(" total — ")
          .append(result.getCompletedStages()).append(" completed, ")
          .append(result.getFailedStages()).append(" failed, ")
          .append(result.getUpToDateStages()).append(" up to date, ")
          .append(result.getNotReachedStages()).append(" not reached")
          .append(NEWLINE);
        sb.append(" Changes:   ")
          .append(result.getTotalChanges()).append(" total — ")
          .append(result.getAppliedChanges()).append(" newly applied, ")
          .append(result.getAlreadyAppliedChanges()).append(" already applied, ")
          .append(result.getFailedChanges()).append(" failed, ")
          .append(result.getNotReachedChanges()).append(" not reached")
          .append(NEWLINE);

        // Per-stage breakdown: include any stage the operation touched (state != NOT_STARTED) OR
        // the planner verdicted UP_TO_DATE. Stages with verdict NEEDS_WORK / NOT_EVALUATED and
        // still-NOT_STARTED state fall into the "Not reached" section.
        List<StageResult> stages = nonNullStages(result);
        List<StageResult> inBreakdown = stages.stream()
                .filter(s -> !s.getState().isNotStarted()
                        || s.getPlannerVerdict() == PlannerVerdict.UP_TO_DATE)
                .collect(Collectors.toList());
        if (!inBreakdown.isEmpty()) {
            sb.append(NEWLINE).append(" Per-stage breakdown:").append(NEWLINE).append(NEWLINE);
            for (StageResult stage : inBreakdown) {
                appendStageBlock(sb, stage);
                sb.append(NEWLINE);
            }
        }

        // "Not reached" lists stages neither operation-touched nor verdicted UP_TO_DATE. Omitted
        // when nothing was reached AND nothing was up to date (catastrophic-failure shape; the
        // headline conveys it).
        List<StageResult> notReached = stages.stream()
                .filter(s -> s.getState().isNotStarted()
                        && s.getPlannerVerdict() != PlannerVerdict.UP_TO_DATE)
                .collect(Collectors.toList());
        if (!notReached.isEmpty()
                && (result.getCompletedStages() > 0
                    || result.getFailedStages() > 0
                    || result.getUpToDateStages() > 0)) {
            sb.append(NEWLINE).append(" Not reached (").append(notReached.size()).append("):").append(NEWLINE);
            for (StageResult stage : notReached) {
                String name = stage.getStageName() != null ? stage.getStageName() : "(unnamed)";
                sb.append("   - ").append(name)
                  .append(" (").append(stage.getTotalChanges()).append(" changes)").append(NEWLINE);
            }
            sb.append(NEWLINE);
        }

        sb.append(LINE);
        return sb.toString();
    }

    private static void appendTimeBlock(StringBuilder sb, ExecuteResponseData result) {
        Instant start = result.getStartTime();
        Instant end = result.getEndTime();
        sb.append(" Started:   ").append(start != null ? start.toString() : "n/a").append(NEWLINE);
        sb.append(" Finished:  ").append(end != null ? end.toString() : "n/a").append(NEWLINE);
        sb.append(" Duration:  ").append(result.getTotalDurationMs()).append(" ms").append(NEWLINE);
    }

    private static void appendStageBlock(StringBuilder sb, StageResult stage) {
        String label = stageLabel(stage);
        String name = stage.getStageName() != null ? stage.getStageName() : "(unnamed)";

        // [UP TO DATE] rows render without a duration (the executor never ran). Both community
        // and cloud feed per-change ALREADY_APPLIED records into PipelineRun via the planner-side
        // writer (community from the local audit snapshot; cloud from the server's pipelineResult
        // payload). When records are present we emit a per-change line; if they're empty we
        // fall back to a count-only line.
        boolean isUpToDateOnly = stage.getState().isNotStarted()
                && stage.getPlannerVerdict() == PlannerVerdict.UP_TO_DATE;

        if (isUpToDateOnly) {
            int alreadyAppliedCount = stage.getChanges() != null
                    ? (int) stage.getChanges().stream()
                        .filter(c -> c != null && c.isAlreadyApplied())
                        .count()
                    : 0;
            int reportableCount = alreadyAppliedCount > 0 ? alreadyAppliedCount : stage.getTotalChanges();
            sb.append("   ").append(label).append(' ').append(name)
              .append("  (").append(reportableCount).append(" changes already applied)")
              .append(NEWLINE);
            return;
        }

        sb.append("   ").append(label).append(' ').append(name)
          .append(" (").append(stage.getDurationMs()).append(" ms)").append(NEWLINE);

        List<ChangeResult> changes = stage.getChanges() != null ? stage.getChanges() : Collections.emptyList();
        int applied = (int) changes.stream().filter(c -> c != null && c.isApplied()).count();
        int skipped = (int) changes.stream().filter(c -> c != null && c.isAlreadyApplied()).count();
        // ROLLED_BACK is widened into the failed bucket — the change did not succeed even though
        // the system was left clean. See PipelineRun.toResponse() for the matching aggregate logic.
        int failed = (int) changes.stream().filter(c -> c != null && (c.isFailed() || c.isRolledBack())).count();
        int notReached = (int) changes.stream().filter(c -> c != null && c.isNotReached()).count();
        sb.append("               changes: ")
          .append(applied).append(" newly applied, ")
          .append(skipped).append(" already applied, ")
          .append(failed).append(" failed, ")
          .append(notReached).append(" not reached")
          .append(NEWLINE);

        StageState state = stage.getState();
        if (state == null) {
            return;
        }
        if (state.isBlockedForManualIntervention()) {
            List<String> ids = changeIdsFromRecoveryIssues(state.getRecoveryIssues());
            if (!ids.isEmpty()) {
                sb.append("               change(s) requiring intervention: ")
                  .append(String.join(", ", ids)).append(NEWLINE);
            }
        } else if (state.isFailed()) {
            state.getErrorInfo().ifPresent(err -> appendErrorBlock(sb, err));
            List<String> failedChangeIds = failedChangeIds(stage);
            if (!failedChangeIds.isEmpty()) {
                sb.append("               failed change(s): ")
                  .append(String.join(", ", failedChangeIds)).append(NEWLINE);
            }
        }
    }

    private static void appendErrorBlock(StringBuilder sb, ErrorInfo err) {
        String errorType = err.getErrorType() != null ? err.getErrorType() : "Error";
        sb.append("               error:   ").append(errorType).append(NEWLINE);
        String message = err.getMessage();
        if (message != null && !message.isEmpty()) {
            // Indent continuation lines so multi-line messages stay aligned with the "error:" column.
            String[] lines = message.split("\\R");
            for (String line : lines) {
                sb.append("                        ").append(line).append(NEWLINE);
            }
        }
    }

    private static String stageLabel(StageResult stage) {
        // Two-dimensional resolution. State (operation-owned) wins whenever it's moved off
        // NOT_STARTED. While state is still NOT_STARTED, the planner verdict decides.
        StageState state = stage.getState();
        if (state == null) {
            return "[UNKNOWN]   ";
        }
        if (state.isBlockedForManualIntervention()) {
            return "[BLOCKED — manual intervention required]";
        }
        if (state.isFailed()) {
            return "[FAILED]   ";
        }
        if (state.isCompleted()) {
            return "[COMPLETED]";
        }
        if (state.isStarted()) {
            return "[STARTED]  ";
        }
        // state.isNotStarted() — verdict decides.
        if (stage.getPlannerVerdict() == PlannerVerdict.UP_TO_DATE) {
            return "[UP TO DATE]";
        }
        return "[NOT REACHED]";
    }

    private static String statusLabel(ExecuteResponseData result) {
        ExecutionStatus status = result.getStatus();
        if (status == null) {
            return "UNKNOWN";
        }
        // Render the enum name with underscores replaced by spaces so the headline reads naturally
        // ("NO CHANGES" rather than "NO_CHANGES").
        return status.name().replace('_', ' ');
    }

    private static boolean isFailedStatus(ExecutionStatus status) {
        return status == ExecutionStatus.FAILED;
    }

    private static List<StageResult> nonNullStages(ExecuteResponseData result) {
        List<StageResult> raw = result.getStages();
        if (raw == null) {
            return Collections.emptyList();
        }
        List<StageResult> filtered = new ArrayList<>(raw.size());
        for (StageResult s : raw) {
            if (s != null) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    private static List<String> failedStageNames(List<StageResult> stages) {
        return stages.stream()
                .filter(s -> s.getState() != null && s.getState().isFailed())
                .map(s -> s.getStageName() != null ? s.getStageName() : "(unnamed)")
                .collect(Collectors.toList());
    }

    private static List<String> manualInterventionChangeIds(List<StageResult> stages) {
        List<String> ids = new ArrayList<>();
        for (StageResult s : stages) {
            StageState state = s.getState();
            if (state != null && state.isBlockedForManualIntervention()) {
                ids.addAll(changeIdsFromRecoveryIssues(state.getRecoveryIssues()));
            }
        }
        return ids;
    }

    private static List<String> changeIdsFromRecoveryIssues(List<RecoveryIssue> issues) {
        if (issues == null) {
            return Collections.emptyList();
        }
        return issues.stream()
                .filter(i -> i != null && i.getChangeId() != null)
                .map(RecoveryIssue::getChangeId)
                .collect(Collectors.toList());
    }

    private static List<String> failedChangeIds(StageResult stage) {
        List<ChangeResult> changes = stage.getChanges();
        if (changes == null) {
            return Collections.emptyList();
        }
        return changes.stream()
                .filter(c -> c != null && (c.isFailed() || c.isRolledBack()) && c.getChangeId() != null)
                .map(ChangeResult::getChangeId)
                .collect(Collectors.toList());
    }

    private static String banner(String headline) {
        return LINE + NEWLINE + " " + headline + NEWLINE + LINE;
    }
}
