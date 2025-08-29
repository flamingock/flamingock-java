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

/**
 * Represents a table column definition with title, width and alignment
 */
public class TableColumn {
    
    public enum Alignment {
        LEFT, CENTER, RIGHT
    }
    
    private final String title;
    private final int width;
    private final Alignment alignment;
    
    public TableColumn(String title, int width, Alignment alignment) {
        this.title = title;
        this.width = width;
        this.alignment = alignment;
    }
    
    public TableColumn(String title, int width) {
        this(title, width, Alignment.LEFT);
    }
    
    public String getTitle() {
        return title;
    }
    
    public int getWidth() {
        return width;
    }
    
    public Alignment getAlignment() {
        return alignment;
    }
    
    /**
     * Format text according to column width and alignment
     */
    public String format(String text) {
        String truncated = truncate(text);
        return align(truncated);
    }
    
    /**
     * Truncate text if it exceeds column width
     */
    private String truncate(String text) {
        if (text == null) {
            return "-";
        }
        if (text.length() <= width) {
            return text;
        }
        // Account for "..." when truncating
        if (width <= 3) {
            return "...".substring(0, width);
        }
        return text.substring(0, width - 3) + "...";
    }
    
    /**
     * Align text within column width
     */
    private String align(String text) {
        switch (alignment) {
            case RIGHT:
                return String.format("%" + width + "s", text);
            case CENTER:
                int totalPadding = width - text.length();
                if (totalPadding <= 0) {
                    return text;
                }
                int leftPad = totalPadding / 2;
                int rightPad = totalPadding - leftPad;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < leftPad; i++) sb.append(" ");
                sb.append(text);
                for (int i = 0; i < rightPad; i++) sb.append(" ");
                return sb.toString();
            case LEFT:
            default:
                return String.format("%-" + width + "s", text);
        }
    }
}