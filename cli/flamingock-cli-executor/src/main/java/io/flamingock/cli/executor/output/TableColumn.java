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

/**
 * Represents a table column definition with title, width and alignment.
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
     * Format text according to column width and alignment.
     *
     * @param text the text to format
     * @return formatted text that fits within the column width and alignment
     */
    public String format(String text) {
        String truncated = truncate(text);
        return align(truncated);
    }

    /**
     * Truncate text if it exceeds column width.
     */
    private String truncate(String text) {
        if (text == null) {
            return "-";
        }
        // Calculate visual length (emojis count as 2 chars visually)
        int visualLength = getVisualLength(text);
        if (visualLength <= width) {
            return text;
        }
        // Account for "..." when truncating
        if (width <= 3) {
            return "...".substring(0, width);
        }
        // Truncate based on visual length
        return truncateToVisualWidth(text, width - 3) + "...";
    }

    /**
     * Get the visual length of a string, accounting for emojis and variation selectors.
     */
    private int getVisualLength(String text) {
        int length = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            // Skip variation selectors (they're invisible modifiers)
            if (isVariationSelector(codePoint)) {
                i += Character.charCount(codePoint);
                continue;
            }
            // Emojis and other wide characters take 2 visual spaces
            if (Character.isSupplementaryCodePoint(codePoint) || isEmojiCodePoint(codePoint)) {
                length += 2;
            } else {
                length += 1;
            }
            i += Character.charCount(codePoint);
        }
        return length;
    }

    /**
     * Check if a code point is a variation selector (invisible modifier).
     */
    private boolean isVariationSelector(int codePoint) {
        // U+FE0E (text style) and U+FE0F (emoji style) variation selectors
        return codePoint == 0xFE0E || codePoint == 0xFE0F;
    }

    /**
     * Check if a code point is likely an emoji.
     */
    private boolean isEmojiCodePoint(int codePoint) {
        return (codePoint >= 0x1F300 && codePoint <= 0x1F9FF) // Misc Symbols, Emoticons, etc.
                || (codePoint >= 0x2600 && codePoint <= 0x26FF) // Misc Symbols
                || (codePoint >= 0x2700 && codePoint <= 0x27BF) // Dingbats
                || (codePoint >= 0x2300 && codePoint <= 0x23FF); // Misc Technical
    }

    /**
     * Truncate string to a visual width.
     */
    private String truncateToVisualWidth(String text, int maxWidth) {
        StringBuilder result = new StringBuilder();
        int currentWidth = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            // Always include variation selectors (they follow their base character)
            if (isVariationSelector(codePoint)) {
                result.appendCodePoint(codePoint);
                i += Character.charCount(codePoint);
                continue;
            }
            int charWidth = (Character.isSupplementaryCodePoint(codePoint) || isEmojiCodePoint(codePoint)) ? 2 : 1;
            if (currentWidth + charWidth > maxWidth) {
                break;
            }
            result.appendCodePoint(codePoint);
            currentWidth += charWidth;
            i += Character.charCount(codePoint);
        }
        return result.toString();
    }

    /**
     * Align text within column width.
     */
    private String align(String text) {
        int visualLength = getVisualLength(text);
        int padding = width - visualLength;

        if (padding <= 0) {
            return text;
        }

        switch (alignment) {
            case RIGHT:
                return spaces(padding) + text;
            case CENTER:
                int leftPad = padding / 2;
                int rightPad = padding - leftPad;
                return spaces(leftPad) + text + spaces(rightPad);
            case LEFT:
            default:
                return text + spaces(padding);
        }
    }

    private String spaces(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
