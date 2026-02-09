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

import io.flamingock.internal.common.core.response.data.IssueGetResponseData;
import io.flamingock.internal.common.core.response.data.IssueListResponseData;
import io.flamingock.internal.common.core.response.data.IssueListResponseData.IssueSummaryDto;

import java.time.format.DateTimeFormatter;

/**
 * Formats issue information for console output.
 */
public class IssueFormatter {

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private IssueFormatter() {
    }

    /**
     * Prints a table of issue summaries.
     *
     * @param data the issue list response data
     */
    public static void printList(IssueListResponseData data) {
        if (data == null || data.getIssues() == null || data.getIssues().isEmpty()) {
            return;
        }

        // Simple table format
        String format = "%-32s %-15s %-21s %s%n";
        System.out.printf(format, "Change ID", "State", "Time", "Error Summary");
        System.out.println(repeat("-", 100));

        for (IssueSummaryDto issue : data.getIssues()) {
            String state = issue.getState() != null ? colorState(issue.getState()) : "UNKNOWN";
            String time = issue.getCreatedAt() != null ? issue.getCreatedAt().format(TIME_FORMATTER) : "-";
            String errorSummary = issue.getErrorSummary() != null ? issue.getErrorSummary() : "-";

            System.out.printf("%-32s %s %-21s %s%n",
                    truncate(issue.getChangeId(), 30),
                    padState(state, issue.getState(), 15),
                    time,
                    errorSummary);
        }
    }

    /**
     * Prints detailed information about a single issue.
     *
     * @param data         the issue details
     * @param withGuidance whether to include resolution guidance
     */
    public static void printDetail(IssueGetResponseData data, boolean withGuidance) {
        if (data == null || !data.isFound()) {
            return;
        }

        // Overview section
        System.out.println(BOLD + "📋 OVERVIEW" + RESET);
        System.out.println("  Change ID:         " + data.getChangeId());
        System.out.println("  State:             " + colorState(data.getState()));
        System.out.println("  Author:            " + (data.getAuthor() != null ? data.getAuthor() : "-"));
        System.out.println("  Created At:        " + (data.getCreatedAt() != null ? data.getCreatedAt().format(TIME_FORMATTER) : "-"));
        System.out.println("  Execution ID:      " + (data.getExecutionId() != null ? data.getExecutionId() : "-"));
        System.out.println("  Duration:          " + formatDuration(data.getExecutionMillis()));
        System.out.println("  Target System:     " + (data.getTargetSystemId() != null ? data.getTargetSystemId() : "-"));
        System.out.println("  Recovery Strategy: " + (data.getRecoveryStrategy() != null ? data.getRecoveryStrategy() : "-"));
        System.out.println();

        // Execution details
        System.out.println(BOLD + "📍 EXECUTION DETAILS" + RESET);
        System.out.println("  Class:             " + (data.getClassName() != null ? data.getClassName() : "-"));
        System.out.println("  Method:            " + (data.getMethodName() != null ? data.getMethodName() : "-"));
        System.out.println("  Hostname:          " + (data.getExecutionHostname() != null ? data.getExecutionHostname() : "-"));
        System.out.println();

        // Error details
        System.out.println(BOLD + "⚠️  ERROR DETAILS" + RESET);
        if (data.getErrorTrace() != null && !data.getErrorTrace().isEmpty()) {
            // Print error trace with indentation
            String[] lines = data.getErrorTrace().split("\n");
            for (String line : lines) {
                System.out.println("  " + RED + line + RESET);
            }
        } else {
            System.out.println("  No error trace available");
        }
        System.out.println();

        // Resolution guidance
        if (withGuidance) {
            printGuidance(data);
        }
    }

    private static void printGuidance(IssueGetResponseData data) {
        System.out.println(BOLD + "🔧 RESOLUTION GUIDANCE" + RESET);
        System.out.println();
        System.out.println("  To resolve this issue, follow these steps:");
        System.out.println();
        System.out.println("  1. " + CYAN + "Review the error" + RESET + " - Examine the error trace above to understand what went wrong");
        System.out.println();
        System.out.println("  2. " + CYAN + "Verify actual state" + RESET + " - Check your target system to determine if the change was:");
        System.out.println("     - Successfully applied (despite the error)");
        System.out.println("     - Partially applied (requires manual cleanup)");
        System.out.println("     - Not applied at all (can be retried)");
        System.out.println();
        System.out.println("  3. " + CYAN + "Fix the audit state" + RESET + " - Once you've verified the actual state, mark it accordingly:");
        System.out.println();
        System.out.println("     If the change WAS successfully applied:");
        System.out.println("     " + YELLOW + "flamingock audit fix --jar app.jar -c " + data.getChangeId() + " -r APPLIED" + RESET);
        System.out.println();
        System.out.println("     If the change was NOT applied (or you've rolled it back):");
        System.out.println("     " + YELLOW + "flamingock audit fix --jar app.jar -c " + data.getChangeId() + " -r ROLLED_BACK" + RESET);
        System.out.println();
        System.out.println("  4. " + CYAN + "Retry execution" + RESET + " - After fixing, run the application again to continue");
        System.out.println();
    }

    private static String colorState(String state) {
        if (state == null) {
            return "UNKNOWN";
        }
        switch (state.toUpperCase()) {
            case "FAILED":
            case "ROLLBACK_FAILED":
                return RED + state + RESET;
            case "STARTED":
                return YELLOW + state + RESET;
            default:
                return state;
        }
    }

    private static String padState(String coloredState, String rawState, int width) {
        // Calculate actual display width (without ANSI codes)
        int displayLen = rawState != null ? rawState.length() : 7;
        int padding = width - displayLen;
        if (padding > 0) {
            return coloredState + repeat(" ", padding);
        }
        return coloredState;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "-";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen - 3) + "...";
    }

    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.2fs", millis / 1000.0);
        } else {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }

    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
