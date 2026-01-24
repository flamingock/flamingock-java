/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.template.sql.util;

import io.flamingock.internal.util.log.FlamingockLoggerFactory;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

public class SqlStatementParser {

    private static final Logger logger = FlamingockLoggerFactory.getLogger(SqlStatementParser.class);

    public static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStmt = new StringBuilder();
        boolean inString = false;
        boolean inComment = false;
        boolean inLineComment = false;
        char stringChar = '"';

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (!inComment && !inLineComment && !inString && c == '/' && next == '*') {
                inComment = true;
                i++; // skip next char
            } else if (inComment && c == '*' && next == '/') {
                inComment = false;
                i++; // skip next char
            } else if (!inComment && !inString && c == '-' && next == '-') {
                inLineComment = true;
                i++; // skip next char
            } else if (inLineComment && c == '\n') {
                inLineComment = false;
            } else if (!inComment && !inLineComment && !inString && (c == '"' || c == '\'')) {
                inString = true;
                stringChar = c;
                currentStmt.append(c);
            } else if (inString && c == stringChar) {
                // Check for escaped quote
                if (i > 0 && sql.charAt(i - 1) == '\\') {
                    currentStmt.append(c);
                } else {
                    inString = false;
                    currentStmt.append(c);
                }
            } else if (!inComment && !inLineComment && !inString && c == ';') {
                statements.add(normalizeSpaces(currentStmt.toString().trim()));
                currentStmt.setLength(0);
            } else if (!inComment && !inLineComment) {
                currentStmt.append(c);
            }
            // Skip comments entirely
        }
        if (currentStmt.length() > 0) {
            statements.add(normalizeSpaces(currentStmt.toString().trim()));
        }
        return statements.stream().filter(s -> !s.trim().isEmpty()).collect(java.util.stream.Collectors.toList());
    }

    public static String getCommand(String sql) {
        String trimmed = sql.trim();
        if (trimmed.isEmpty()) {
            return "UNKNOWN";
        }
        String[] parts = trimmed.split("\\s+");
        return parts.length > 0 ? parts[0].toUpperCase() : "UNKNOWN";
    }

    public static void executeSingle(Connection connection, String stmtSql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(stmtSql);
        } catch (SQLException e) {
            String errorMsg = "SQL execution failed: " + stmtSql;
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    public static void executeMany(Connection connection, List<String> statements) {
        try (Statement stmt = connection.createStatement()) {
            for (String stmtSql : statements) {
                stmt.addBatch(stmtSql);
            }
            stmt.executeBatch();
        } catch (BatchUpdateException e) {
            // BatchUpdateException provides updateCounts with failed index
            int[] updateCounts = e.getUpdateCounts();
            int failedIndex = -1;
            for (int i = 0; i < updateCounts.length; i++) {
                if (updateCounts[i] == Statement.EXECUTE_FAILED) {
                    failedIndex = i;
                    break;
                }
            }
            String failedStmt = failedIndex >= 0 ? statements.get(failedIndex) : "unknown";
            String errorMsg = String.format("Batch execution failed at statement %d: %s", failedIndex + 1, failedStmt);
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (SQLException e) {
            String errorMsg = "Batch execution failed: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    private static String normalizeSpaces(String sql) {
        if (sql == null) {
            return null;
        }
        // Replace newlines and tabs with spaces
        String normalized = sql.replaceAll("[\\r\\n\\t]", " ");
        // Replace multiple spaces with single space
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.trim();
    }
}