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
package io.flamingock.cli.command;

import io.flamingock.cli.FlamingockCli;
import io.flamingock.cli.config.ConfigLoader;
import io.flamingock.cli.config.FlamingockConfig;
import io.flamingock.cli.service.AuditService;
import io.flamingock.internal.common.core.audit.AuditEntry;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Command(
    name = "list",
    description = "List audit entries"
)
public class ListCommand implements Runnable {

    @ParentCommand
    private AuditCommand parent;

    @CommandLine.Option(names = "--issues", description = "Show only audits with issues")
    private boolean issues;

    @CommandLine.Option(names = "--history", description = "Show full chronological audit history")
    private boolean history;

    @CommandLine.Option(names = "--since", description = "Show audits since date (ISO-8601 format: 2025-01-01T00:00:00)")
    private String since;

    @CommandLine.Option(names = "--limit", description = "Limit number of results")
    private Integer limit;

    @CommandLine.Option(names = "--page", description = "Page number for pagination")
    private Integer page;

    @Override
    public void run() {
        try {
            // Load configuration
            FlamingockConfig config = ConfigLoader.loadConfig(parent.getConfigFile());
            
            // Create audit service
            AuditService auditService = new AuditService(config);
            
            // Parse since date if provided
            LocalDateTime sinceDate = null;
            if (since != null && !since.trim().isEmpty()) {
                try {
                    sinceDate = LocalDateTime.parse(since, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid date format for --since. Use ISO-8601 format: 2025-01-01T00:00:00");
                }
            }
            
            // Get audit entries based on flags
            List<AuditEntry> entries;
            String listType;
            
            if (history) {
                // Full chronological history
                entries = auditService.listAuditEntriesHistory();
                listType = "Full Audit History";
            } else if (issues) {
                // Only entries with issues
                entries = auditService.listAuditEntriesWithIssues();
                listType = "Audit Entries with Issues";
            } else if (sinceDate != null) {
                // Entries since a specific date
                entries = auditService.listAuditEntriesSince(sinceDate);
                listType = "Audit Entries since " + since;
            } else {
                // Default: snapshot view (latest per changeUnit)
                entries = auditService.listAuditEntriesSnapshot();
                listType = "Audit Entries Snapshot (Latest per Change Unit)";
            }
            
            // Apply pagination if specified
            if (limit != null && limit > 0) {
                entries = auditService.applyPagination(entries, limit, page != null ? page : 1);
            }
            
            // Display results
            if (entries.isEmpty()) {
                System.out.println("No audit entries found.");
            } else {
                System.out.println(listType + ":");
                StringBuilder separator = new StringBuilder();
                for (int i = 0; i <= listType.length(); i++) {
                    separator.append("=");
                }
                System.out.println(separator.toString());
                for (AuditEntry entry : entries) {
                    printAuditEntry(entry);
                    System.out.println("------------------------");
                }
                System.out.println("Total entries: " + entries.size());
                
                if (limit != null && limit > 0) {
                    System.out.println("Page: " + (page != null ? page : 1) + ", Limit: " + limit);
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to list audit entries: " + e.getMessage(), e);
        }
    }

    private void printAuditEntry(AuditEntry entry) {
        System.out.println("ID: " + entry.getTaskId());
        System.out.println("Execution ID: " + entry.getExecutionId());
        System.out.println("Stage ID: " + entry.getStageId());
        System.out.println("Author: " + entry.getAuthor());
        System.out.println("State: " + entry.getState());
        System.out.println("Type: " + entry.getType());
        System.out.println("Class: " + entry.getClassName());
        System.out.println("Method: " + entry.getMethodName());
        System.out.println("Created At: " + entry.getCreatedAt());
        System.out.println("Execution Time: " + entry.getExecutionMillis() + "ms");
        System.out.println("Hostname: " + entry.getExecutionHostname());
        System.out.println("System Change: " + entry.getSystemChange());
        if (entry.getTargetSystemId() != null) {
            System.out.println("Target System: " + entry.getTargetSystemId());
        }
        if (entry.getErrorTrace() != null && !entry.getErrorTrace().isEmpty()) {
            System.out.println("Error: " + entry.getErrorTrace());
        }
    }
}