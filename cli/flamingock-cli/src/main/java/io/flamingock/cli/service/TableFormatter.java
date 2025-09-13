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
package io.flamingock.cli.service;

import io.flamingock.internal.common.core.audit.AuditEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


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
    
    // Column widths for basic table
    private static final int CHANGE_ID_WIDTH = 30;
    private static final int STATE_WIDTH = 8;
    private static final int EXEC_ID_WIDTH = 34;
    private static final int AUTHOR_WIDTH = 18;
    private static final int TIME_WIDTH = 21;
    
    // Additional column widths for extended table
    private static final int DURATION_WIDTH = 10;
    private static final int CLASS_WIDTH = 34;
    private static final int METHOD_WIDTH = 13;
    private static final int HOSTNAME_WIDTH = 29;
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Print basic table with 4 columns
     *
     * @param entries the audit entries to display in the table
     */
    public void printBasicTable(List<AuditEntry> entries) {
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("Change ID", CHANGE_ID_WIDTH));
        columns.add(new TableColumn("State", STATE_WIDTH, TableColumn.Alignment.CENTER));
        columns.add(new TableColumn("Author", AUTHOR_WIDTH));
        columns.add(new TableColumn("Time", TIME_WIDTH));
        
        printTable(entries, columns, false);
    }
    
    /**
     * Print extended table with 9 columns
     *
     * @param entries the audit entries to display in the extended table
     */
    public void printExtendedTable(List<AuditEntry> entries) {
        List<TableColumn> columns = new ArrayList<>();
        columns.add(new TableColumn("Change ID", CHANGE_ID_WIDTH));
        columns.add(new TableColumn("State", STATE_WIDTH, TableColumn.Alignment.CENTER));
        columns.add(new TableColumn("Execution ID", EXEC_ID_WIDTH));
        columns.add(new TableColumn("Author", AUTHOR_WIDTH));
        columns.add(new TableColumn("Time", TIME_WIDTH));
        columns.add(new TableColumn("Duration", DURATION_WIDTH, TableColumn.Alignment.RIGHT));
        columns.add(new TableColumn("Class", CLASS_WIDTH));
        columns.add(new TableColumn("Method", METHOD_WIDTH));
        columns.add(new TableColumn("Hostname", HOSTNAME_WIDTH));
        
        printTable(entries, columns, true);
    }
    
    private void printTable(List<AuditEntry> entries, List<TableColumn> columns, boolean extended) {
        // Print top border
        printTopBorder(columns);
        
        // Print header row
        printHeaderRow(columns);
        
        // Print header separator
        printMiddleBorder(columns);
        
        // Print data rows
        for (int i = 0; i < entries.size(); i++) {
            printDataRow(entries.get(i), columns);
            if (extended && i < entries.size() - 1) {
                // Add spacing row between entries in extended view
                printSpacingRow(columns);
                printMiddleBorder(columns);
            }
        }
        
        // Print bottom border
        printBottomBorder(columns);
    }
    
    private void printTopBorder(List<TableColumn> columns) {
        System.out.print(TOP_LEFT);
        for (int i = 0; i < columns.size(); i++) {
            for (int j = 0; j < columns.get(i).getWidth(); j++) {
                System.out.print(HORIZONTAL);
            }
            if (i < columns.size() - 1) {
                System.out.print(T_DOWN);
            }
        }
        System.out.println(TOP_RIGHT);
    }
    
    private void printMiddleBorder(List<TableColumn> columns) {
        System.out.print(T_RIGHT);
        for (int i = 0; i < columns.size(); i++) {
            for (int j = 0; j < columns.get(i).getWidth(); j++) {
                System.out.print(HORIZONTAL);
            }
            if (i < columns.size() - 1) {
                System.out.print(CROSS);
            }
        }
        System.out.println(T_LEFT);
    }
    
    private void printBottomBorder(List<TableColumn> columns) {
        System.out.print(BOTTOM_LEFT);
        for (int i = 0; i < columns.size(); i++) {
            for (int j = 0; j < columns.get(i).getWidth(); j++) {
                System.out.print(HORIZONTAL);
            }
            if (i < columns.size() - 1) {
                System.out.print(T_UP);
            }
        }
        System.out.println(BOTTOM_RIGHT);
    }
    
    private void printHeaderRow(List<TableColumn> columns) {
        System.out.print(VERTICAL);
        for (TableColumn column : columns) {
            System.out.print(column.format(column.getTitle()));
            System.out.print(VERTICAL);
        }
        System.out.println();
    }
    
    private void printSpacingRow(List<TableColumn> columns) {
        System.out.print(VERTICAL);
        for (TableColumn column : columns) {
            System.out.print(column.format(""));
            System.out.print(VERTICAL);
        }
        System.out.println();
    }
    
    private void printDataRow(AuditEntry entry, List<TableColumn> columns) {
        System.out.print(VERTICAL);
        for (int i = 0; i < columns.size(); i++) {
            String value = getColumnValue(entry, i, columns.size() > 5);
            System.out.print(columns.get(i).format(value));
            System.out.print(VERTICAL);
        }
        System.out.println();
    }
    
    private String getColumnValue(AuditEntry entry, int columnIndex, boolean extended) {
        if (extended) {
            // Extended table (9 columns)
            switch (columnIndex) {
                case 0: // Change ID
                    return entry.getTaskId();
                case 1: // State (icon only)
                    return getStateIcon(entry.getState());
                case 2: // Execution ID
                    return entry.getExecutionId();
                case 3: // Author
                    return entry.getAuthor();
                case 4: // Time
                    return formatTime(entry.getCreatedAt());
                case 5: // Duration
                    return formatDuration(entry.getExecutionMillis());
                case 6: // Class
                    return shortenClassName(entry.getClassName());
                case 7: // Method
                    return entry.getMethodName();
                case 8: // Hostname
                    return entry.getExecutionHostname();
                default:
                    return "";
            }
        } else {
            // Basic table (4 columns)
            switch (columnIndex) {
                case 0: // Change ID
                    return entry.getTaskId();
                case 1: // State (icon only)
                    return getStateIcon(entry.getState());
                case 2: // Author
                    return entry.getAuthor();
                case 3: // Time
                    return formatTime(entry.getCreatedAt());
                default:
                    return "";
            }
        }
    }
    
    private String getStateIcon(AuditEntry.Status status) {
        if (status == null) return "❓";
        
        switch (status) {
            case APPLIED:
            case MANUAL_MARKED_AS_APPLIED:
                return "✅";
            case ROLLED_BACK:
            case MANUAL_MARKED_AS_ROLLED_BACK:
                return "🔄";
            case FAILED:
            case ROLLBACK_FAILED:
                return "❌";
            case STARTED:
                return "⚠️";
            default:
                return "❓";
        }
    }
    
    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return "-";
        }
        return time.format(TIME_FORMATTER);
    }
    
    private String formatDuration(Long millis) {
        if (millis == null) {
            return "-";
        }
        return millis + "ms";
    }
    
    private String shortenClassName(String className) {
        if (className == null) {
            return "-";
        }
        // Shorten package names: io.flamingock.changes -> i.f.changes
        String[] parts = className.split("\\.");
        StringBuilder shortened = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].length() > 0) {
                shortened.append(parts[i].charAt(0)).append(".");
            }
        }
        if (parts.length > 0) {
            shortened.append(parts[parts.length - 1]);
        }
        return shortened.toString();
    }
    
    /**
     * Print the state legend
     */
    public static void printStateLegend() {
        System.out.println("\nState Legend:");
        System.out.println("✅ APPLIED    - Successfully completed, won't be reapplied");
        System.out.println("🔄 ROLLED_BACK - Successfully reverted, needs to be applied again");
        System.out.println("❌ FAILED      - Execution or rollback failed");
        System.out.println("⚠️ STARTED     - Unknown/incomplete state (partial execution or audit failure)");
    }
}