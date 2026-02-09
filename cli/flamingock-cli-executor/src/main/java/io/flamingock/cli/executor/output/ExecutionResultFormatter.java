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
package io.flamingock.cli.executor.output;

import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.common.core.response.data.StageResult;

/**
 * Formats execution results for CLI output.
 * Provides professional, scannable output following CLI conventions.
 */
public final class ExecutionResultFormatter {

    private static final String SEPARATOR = "--------------------------------------------------------------------------------";
    private static final int CHANGE_ID_WIDTH = 30;
    private static final int AUTHOR_WIDTH = 20;

    private ExecutionResultFormatter() {
    }

    /**
     * Formats the complete execution result for CLI display.
     *
     * @param result the execution result data
     * @return formatted string for display
     */
    public static String format(ExecuteResponseData result) {
        StringBuilder sb = new StringBuilder("\n");

        for (StageResult stage : result.getStages()) {
            sb.append(formatStage(stage));
        }

        // Print summary
        sb.append("\n").append(SEPARATOR).append("\n");
        sb.append(centerText("EXECUTION SUMMARY", SEPARATOR.length())).append("\n");
        sb.append(SEPARATOR).append("\n");

        sb.append(String.format("  Status:     %s%n", formatStatus(result.getStatus())));
        sb.append(String.format("  Duration:   %s%n", formatDuration(result.getTotalDurationMs())));
        sb.append(formatStagesSummary(result));
        sb.append(formatChangesSummary(result));

        // Print error details if failed
        if (result.isFailed() && result.getError() != null) {
            sb.append(formatErrorDetails(result.getError()));
        }

        sb.append(SEPARATOR).append("\n");

        return sb.toString();
    }

    /**
     * Formats a single stage with its changes.
     */
    private static String formatStage(StageResult stage) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n  Stage: %s%n", stage.getStageName()));

        for (ChangeResult change : stage.getChanges()) {
            sb.append(formatChange(change));
        }

        return sb.toString();
    }

    /**
     * Formats a single change result line.
     */
    private static String formatChange(ChangeResult change) {
        String statusLabel = formatChangeStatus(change.getStatus());
        String changeId = truncateOrPad(change.getChangeId(), CHANGE_ID_WIDTH);
        String author = change.getAuthor() != null
                ? truncateOrPad("(author: " + change.getAuthor() + ")", AUTHOR_WIDTH)
                : truncateOrPad("", AUTHOR_WIDTH);
        String duration = formatChangeDuration(change);

        return String.format("    [%s]  %s  %s  %s%n", statusLabel, changeId, author, duration);
    }

    /**
     * Formats the change status for display.
     */
    private static String formatChangeStatus(ChangeStatus status) {
        switch (status) {
            case APPLIED:
                return "APPLIED";
            case ALREADY_APPLIED:
                return "SKIPPED";
            case FAILED:
                return "FAILED ";
            case ROLLED_BACK:
                return "ROLLBCK";
            case NOT_REACHED:
                return "SKIPPED";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Formats the execution status for summary display.
     */
    private static String formatStatus(ExecutionStatus status) {
        switch (status) {
            case SUCCESS:
                return "SUCCESS";
            case FAILED:
                return "FAILED";
            case PARTIAL:
                return "PARTIAL";
            case NO_CHANGES:
                return "NO CHANGES";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Formats the stages summary line.
     */
    private static String formatStagesSummary(ExecuteResponseData result) {
        if (result.getFailedStages() > 0) {
            return String.format("  Stages:     %d completed, %d failed%n",
                    result.getCompletedStages(), result.getFailedStages());
        } else {
            return String.format("  Stages:     %d completed%n", result.getCompletedStages());
        }
    }

    /**
     * Formats the changes summary line.
     */
    private static String formatChangesSummary(ExecuteResponseData result) {
        return String.format("  Changes:    %d applied, %d skipped, %d failed%n",
                result.getAppliedChanges(), result.getSkippedChanges(), result.getFailedChanges());
    }

    /**
     * Formats error details section.
     */
    private static String formatErrorDetails(ErrorInfo error) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n  Error:\n");
        if (error.getChangeId() != null) {
            sb.append(String.format("    Change:   %s%n", error.getChangeId()));
        }
        if (error.getStageId() != null) {
            sb.append(String.format("    Stage:    %s%n", error.getStageId()));
        }
        if (error.getErrorType() != null) {
            sb.append(String.format("    Type:     %s%n", error.getErrorType()));
        }
        if (error.getMessage() != null) {
            sb.append(String.format("    Message:  %s%n", error.getMessage()));
        }
        return sb.toString();
    }

    /**
     * Formats duration for change line.
     */
    private static String formatChangeDuration(ChangeResult change) {
        if (change.getStatus() == ChangeStatus.ALREADY_APPLIED) {
            return "Already applied";
        } else if (change.getStatus() == ChangeStatus.NOT_REACHED) {
            return "Not executed";
        } else {
            return formatDuration(change.getDurationMs());
        }
    }

    /**
     * Formats duration in human-readable format.
     */
    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%.1fm", millis / 60000.0);
        }
    }

    /**
     * Truncates or pads a string to the specified width.
     */
    private static String truncateOrPad(String s, int width) {
        if (s == null) {
            s = "";
        }
        if (s.length() > width) {
            return s.substring(0, width - 3) + "...";
        }
        return String.format("%-" + width + "s", s);
    }

    /**
     * Centers text within a given width.
     */
    private static String centerText(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        int padding = (width - text.length()) / 2;
        return String.format("%" + padding + "s%s%" + padding + "s", "", text, "");
    }

    /**
     * Prints the execution result to standard output.
     *
     * @param result the execution result data
     */
    public static void print(ExecuteResponseData result) {
        System.out.print(format(result));
    }
}
