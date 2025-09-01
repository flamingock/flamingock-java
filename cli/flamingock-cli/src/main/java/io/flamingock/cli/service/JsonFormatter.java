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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for formatting CLI output as JSON.
 * Provides methods to convert various data structures to pretty-printed JSON.
 */
public class JsonFormatter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Gson gson;

    public JsonFormatter() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class,
                        (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                                context.serialize(src.format(DATE_TIME_FORMATTER)))
                .create();
    }

    /**
     * Print a list of audit entries with issues as JSON.
     *
     * @param issues List of audit entries representing issues
     */
    public void printIssueList(List<AuditEntryIssue> issues) {
        List<Map<String, Object>> issueList = issues.stream()
                .map(AuditEntryIssue::getAuditEntry)
                .map(this::auditEntryToMap)
                .collect(Collectors.toList());

        Map<String, Object> output = new HashMap<>();
        output.put("issues", issueList);
        output.put("total", issues.size());

        System.out.println(gson.toJson(output));
    }

    /**
     * Print detailed issue information as JSON.
     *
     * @param details Issue details to format
     */
    public void printIssueDetails(AuditEntryIssue details) {
        Map<String, Object> output = new HashMap<>();
        output.put("changeId", details.getChangeId() != null ? details.getChangeId() : "");
        output.put("errorMessage", details.getErrorMessage() != null ? details.getErrorMessage() : "");
        output.put("auditEntry", details.getAuditEntry());

        System.out.println(gson.toJson(output));
    }

    /**
     * Print a list of audit entries as JSON.
     *
     * @param entries List of audit entries to format
     */
    public void printAuditEntries(List<AuditEntry> entries) {
        List<Map<String, Object>> entryList = entries.stream()
                .map(this::auditEntryToMap)
                .collect(Collectors.toList());

        Map<String, Object> output = new HashMap<>();
        output.put("entries", entryList);
        output.put("total", entries.size());

        System.out.println(gson.toJson(output));
    }

    /**
     * Convert an AuditEntry to a Map for JSON serialization.
     */
    private Map<String, Object> auditEntryToMap(AuditEntry entry) {
        Map<String, Object> map = new HashMap<>();
        map.put("changeId", entry.getTaskId() != null ? entry.getTaskId() : "");
        map.put("state", entry.getState() != null ? entry.getState().toString() : "");
        map.put("author", entry.getAuthor() != null ? entry.getAuthor() : "");
        map.put("executionId", entry.getExecutionId() != null ? entry.getExecutionId() : "");
        map.put("className", entry.getClassName() != null ? entry.getClassName() : "");
        map.put("methodName", entry.getMethodName() != null ? entry.getMethodName() : "");
        map.put("createdAt", entry.getCreatedAt() != null ? entry.getCreatedAt() : "");
        map.put("executionMillis", entry.getExecutionMillis());
        map.put("executionHostname", entry.getExecutionHostname() != null ? entry.getExecutionHostname() : "");
        return map;
    }
}