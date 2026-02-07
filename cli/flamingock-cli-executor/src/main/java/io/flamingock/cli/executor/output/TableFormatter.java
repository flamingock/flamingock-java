/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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

import io.flamingock.internal.common.core.response.data.AuditListResponseData.AuditEntryDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats audit entries as professional box-drawing tables with colored state text.
 */
public class TableFormatter {

    // Unicode box-drawing characters
    private static final String VERTICAL = "│";
    private static final String HORIZONTAL = "─";
    private static final String TOP_LEFT = "┌";
    private static final String TOP_RIGHT = "┐";
    private static final String BOTTOM_LEFT = "└";
    private static final String BOTTOM_RIGHT = "┘";
    private static final String T_DOWN = "┬";
    private static final String T_UP = "┴";
    private static final String T_RIGHT = "├";
    private static final String T_LEFT = "┤";
    private static final String CROSS = "┼";

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";

    // Column widths
    private static final int CHANGE_ID_WIDTH = 30;
    private static final int STATE_WIDTH = 13;
    private static final int AUTHOR_WIDTH = 18;
    private static final int TIME_WIDTH = 21;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Print basic audit table with 4 columns: Change ID, State, Author, Time.
     *
     * @param entries the audit entries to display
     */
    public void printBasicTable(List<AuditEntryDto> entries) {
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("Change ID", CHANGE_ID_WIDTH));
        columns.add(new TableColumn("State", STATE_WIDTH, TableColumn.Alignment.CENTER));
        columns.add(new TableColumn("Author", AUTHOR_WIDTH));
        columns.add(new TableColumn("Time", TIME_WIDTH));

        printTable(entries, columns);
    }

    private void printTable(List<AuditEntryDto> entries, List<TableColumn> columns) {
        // Print top border
        printTopBorder(columns);

        // Print header row
        printHeaderRow(columns);

        // Print header separator
        printMiddleBorder(columns);

        // Print data rows
        for (AuditEntryDto entry : entries) {
            printDataRow(entry, columns);
        }

        // Print bottom border
        printBottomBorder(columns);
    }

    private void printTopBorder(List<TableColumn> columns) {
        System.out.print(TOP_LEFT);
        for (int i = 0; i < columns.size(); i++) {
            System.out.print(repeat(HORIZONTAL, columns.get(i).getWidth()));
            if (i < columns.size() - 1) {
                System.out.print(T_DOWN);
            }
        }
        System.out.println(TOP_RIGHT);
    }

    private void printMiddleBorder(List<TableColumn> columns) {
        System.out.print(T_RIGHT);
        for (int i = 0; i < columns.size(); i++) {
            System.out.print(repeat(HORIZONTAL, columns.get(i).getWidth()));
            if (i < columns.size() - 1) {
                System.out.print(CROSS);
            }
        }
        System.out.println(T_LEFT);
    }

    private void printBottomBorder(List<TableColumn> columns) {
        System.out.print(BOTTOM_LEFT);
        for (int i = 0; i < columns.size(); i++) {
            System.out.print(repeat(HORIZONTAL, columns.get(i).getWidth()));
            if (i < columns.size() - 1) {
                System.out.print(T_UP);
            }
        }
        System.out.println(BOTTOM_RIGHT);
    }

    private void printHeaderRow(List<TableColumn> columns) {
        System.out.print(VERTICAL);
        for (TableColumn column : columns) {
            // Always center headers
            String title = column.getTitle();
            int padding = column.getWidth() - title.length();
            int leftPad = padding / 2;
            int rightPad = padding - leftPad;
            System.out.print(spaces(leftPad) + title + spaces(rightPad));
            System.out.print(VERTICAL);
        }
        System.out.println();
    }

    private void printDataRow(AuditEntryDto entry, List<TableColumn> columns) {
        System.out.print(VERTICAL);
        for (int i = 0; i < columns.size(); i++) {
            if (i == 1) {
                // State column: handle ANSI codes separately
                String stateText = getStateText(entry.getState());
                int displayLen = getStateDisplayLength(entry.getState());
                int padding = columns.get(i).getWidth() - displayLen;
                int leftPad = padding / 2;
                int rightPad = padding - leftPad;
                System.out.print(spaces(leftPad) + stateText + spaces(rightPad));
            } else {
                String value = getColumnValue(entry, i);
                System.out.print(columns.get(i).format(value));
            }
            System.out.print(VERTICAL);
        }
        System.out.println();
    }

    private String getColumnValue(AuditEntryDto entry, int columnIndex) {
        switch (columnIndex) {
            case 0: // Change ID
                return entry.getTaskId();
            case 2: // Author
                return entry.getAuthor();
            case 3: // Time
                return formatTime(entry.getCreatedAt());
            default:
                return "";
        }
    }

    private String spaces(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private String getStateText(String state) {
        if (state == null) {
            return "UNKNOWN";
        }

        switch (state.toUpperCase()) {
            case "APPLIED":
            case "MANUAL_MARKED_AS_APPLIED":
                return GREEN + "APPLIED" + RESET;
            case "ROLLED_BACK":
            case "MANUAL_MARKED_AS_ROLLED_BACK":
                return CYAN + "ROLLED_BACK" + RESET;
            case "FAILED":
            case "ROLLBACK_FAILED":
                return RED + "FAILED" + RESET;
            case "STARTED":
                return YELLOW + "STARTED" + RESET;
            default:
                return state;
        }
    }

    /**
     * Get the display length of the state (without ANSI codes).
     */
    private int getStateDisplayLength(String state) {
        if (state == null) {
            return 7; // "UNKNOWN"
        }
        switch (state.toUpperCase()) {
            case "APPLIED":
            case "MANUAL_MARKED_AS_APPLIED":
                return 7; // "APPLIED"
            case "ROLLED_BACK":
            case "MANUAL_MARKED_AS_ROLLED_BACK":
                return 11; // "ROLLED_BACK"
            case "FAILED":
            case "ROLLBACK_FAILED":
                return 6; // "FAILED"
            case "STARTED":
                return 7; // "STARTED"
            default:
                return state.length();
        }
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return "-";
        }
        return time.format(TIME_FORMATTER);
    }

    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * Print the state legend explaining what each state means.
     */
    public static void printStateLegend() {
        System.out.println();
        System.out.println("State Legend:");
        System.out.println(GREEN + "APPLIED" + RESET + "     - Successfully completed");
        System.out.println(CYAN + "ROLLED_BACK" + RESET + " - Reverted, needs reapplication");
        System.out.println(RED + "FAILED" + RESET + "      - Execution failed");
        System.out.println(YELLOW + "STARTED" + RESET + "     - Incomplete state");
    }
}
