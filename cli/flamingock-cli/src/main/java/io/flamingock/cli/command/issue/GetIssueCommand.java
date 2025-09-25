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
package io.flamingock.cli.command.issue;

import io.flamingock.cli.config.ConfigLoader;
import io.flamingock.cli.config.FlamingockConfig;
import io.flamingock.cli.service.AuditService;
import io.flamingock.cli.service.JsonFormatter;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.Optional;

import static io.flamingock.cli.util.ASCIIColors.inViolet;

@Command(
        name = "get",
        aliases = {"describe"},
        description = "Show details of a specific issue or the next available issue"
)
public class GetIssueCommand implements Runnable {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("GetIssueCommand");

    @ParentCommand
    private IssueCommand parent;

    @Option(names = {"-c", "--change-id"}, required = false, description = "Change ID to inspect (optional - shows next issue if not specified)")
    private String changeId;

    @Option(names = {"-j", "--json"}, description = "Output results in JSON format")
    private boolean json;

    @Option(names = {"-g", "--guidance"}, description = "Show resolution guidance")
    private boolean guidance;

    @Override
    public void run() {
        try {
            logger.debug("Configuration file: {}", parent.getConfigFile());

            // Load configuration
            FlamingockConfig config = ConfigLoader.loadConfig(parent.getConfigFile());

            // Create audit service
            AuditService auditService = new AuditService(config);

            // Determine which issue to show
            AuditEntryIssue issueToShow = null;
            
            if (changeId != null) {
                // Specific change-id provided
                logger.info("Getting issue details for changeId: {}", changeId);
                logger.debug("Fetching issue details for changeId: {}", changeId);
                Optional<AuditEntryIssue> auditEntryIssueOpt = auditService.getAuditEntryIssue(changeId);
                
                if (auditEntryIssueOpt.isPresent()) {
                    issueToShow = auditEntryIssueOpt.get();
                } else {
                    System.err.println("Error: There is no issue with change: " + changeId);
                    return;
                }
            } else {
                // No change-id provided, get the next available issue
                logger.info("Getting next available issue");
                List<AuditEntryIssue> issues = auditService.listAuditEntriesWithIssues();
                
                if (issues.isEmpty()) {
                    System.out.println("\n✅ No issues found! All changes are in consistent state.\n");
                    return;
                } else {
                    issueToShow = issues.get(0);  // Get the first issue
                    logger.debug("Found {} issues, showing first: {}", issues.size(), issueToShow.getChangeId());
                }
            }
            
            // Display the issue
            if (json) {
                JsonFormatter formatter = new JsonFormatter();
                formatter.printIssueDetails(issueToShow);
            } else {
                printIssueDetails(issueToShow, guidance);
            }

        } catch (UnsupportedOperationException e) {
            // Expected for now - stub not implemented
            System.err.println("Error: Issue details retrieval not yet implemented.");
            System.err.println("This feature will be available in a future release.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to get issue details: " + e.getMessage(), e);
        }
    }

    private void printIssueDetails(AuditEntryIssue details, boolean showGuidance) {
        AuditEntry entry = details.getAuditEntry();
        String errorMessage = getSmartErrorMessage(details);
        String targetSystem = extractTargetSystem(entry);

        System.out.println("\nIssue Details: " + details.getChangeId());
        System.out.println("==================================================");
        System.out.println();

        // Overview section
        System.out.println("📋 OVERVIEW");
        System.out.println("  Change ID: " + entry.getTaskId());
        System.out.println("  State: " + entry.getState() + " (❌)");
        System.out.println("  Target System: " + targetSystem);
        System.out.println("  Author: " + (entry.getAuthor() != null ? entry.getAuthor() : "-"));
        System.out.println("  Time: " + (entry.getCreatedAt() != null ? entry.getCreatedAt().toString().replace("T", " ") : "-"));
        System.out.println("  Execution ID: " + (entry.getExecutionId() != null ? entry.getExecutionId() : "-"));
        System.out.println("  Duration: " + entry.getExecutionMillis() + "ms");
        System.out.println();

        // Error Details section
        System.out.println("⚠️  ERROR DETAILS");
        System.out.println("  " + errorMessage);
        System.out.println();
        System.out.println("  Technical Details:");
        System.out.println("  - Class: " + shortenClassName(entry.getClassName()));
        System.out.println("  - Method: " + (entry.getMethodName() != null ? entry.getMethodName() : "-"));
        System.out.println("  - Hostname: " + (entry.getExecutionHostname() != null ? entry.getExecutionHostname() : "-"));

        if (showGuidance) {
            System.out.println();
            printResolutionProcess(details.getChangeId(), targetSystem);
        }
        System.out.println();
    }

    private String getSmartErrorMessage(AuditEntryIssue details) {
        String errorMessage = details.getErrorMessage();

        // If we have a meaningful error message, use it
        if (errorMessage != null && !errorMessage.trim().isEmpty() && !errorMessage.equals("unknown")) {
            return errorMessage;
        }

        // Generate context-aware default based on state
        AuditEntry.Status state = details.getAuditEntry().getState();
        if (state != null) {
            switch (state) {
                case STARTED:
                    return "Execution interrupted unexpectedly";
                case FAILED:
                    return "Change execution failed due to unknown reasons";
                case ROLLBACK_FAILED:
                    return "Rollback operation failed due to unknown reasons";
                default:
                    return "Unknown error occurred";
            }
        }
        return "Unknown error occurred";
    }

    private String extractTargetSystem(AuditEntry entry) {
        if (entry.getTargetSystemId() != null && !entry.getTargetSystemId().isEmpty()) {
            return entry.getTargetSystemId();
        } else {
            return "Unknown System";
        }
    }

    private String shortenClassName(String className) {
        if (className == null) {
            return "-";
        }
        // Shorten package names: io.flamingock.changes -> i.f.changes
        String[] parts = className.split("\\.");
        StringBuilder shortened = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                shortened.append(parts[i].charAt(0)).append(".");
            }
        }
        if (parts.length > 0) {
            shortened.append(parts[parts.length - 1]);
        }
        return shortened.toString();
    }

    private void printResolutionProcess(String changeId, String targetSystem) {
        System.out.println("🔧 Resolution Process:");
        System.out.println();
        System.out.println("     1. Review the error details above to understand the root cause");
        System.out.println();
        System.out.println("     2. Verify the actual state in your target system (" + targetSystem + "):");
        System.out.println("        • Check if the change was successfully applied despite the audit failure");
        System.out.println("        • Determine if the change was partially applied or not applied at all");
        System.out.println();
        System.out.println("     3. Fix the audit state based on your findings:");
        System.out.println();
        System.out.println("        ✅ If change was successfully applied:");
        System.out.println("           " + inViolet("flamingock audit fix -c " + changeId + " -r APPLIED"));
        System.out.println();
        System.out.println("        ↩️  If change was not applied or you've manually rolled it back:");
        System.out.println("           " + inViolet("flamingock audit fix -c " + changeId + " -r ROLLED_BACK"));
        System.out.println("           (Flamingock will retry this change in the next execution)");
        System.out.println();
        System.out.println("     ⚠️  Important: For partially applied changes, you must either:");
        System.out.println("         • Manually complete the change, then fix it with resolution(-r) APPLIED");
        System.out.println("         • Manually revert the change, then fix it with resolution(-r) ROLLED_BACK");
    }
}