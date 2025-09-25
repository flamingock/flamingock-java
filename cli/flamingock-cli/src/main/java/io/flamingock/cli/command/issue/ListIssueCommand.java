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
import io.flamingock.cli.service.TableFormatter;
import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.cli.util.ASCIIColors.inViolet;

@Command(
        name = "list",
        aliases = {"ls"},
        description = "List all current issues (changes with inconsistent audits)"
)
public class ListIssueCommand implements Runnable {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("ListIssueCommand");

    @ParentCommand
    private IssueCommand parent;

    @Option(names = {"-j", "--json"}, description = "Output results in JSON format")
    private boolean json;

    @Override
    public void run() {
        try {
            logger.info("Starting issue list command execution");
            logger.debug("Configuration file: {}", parent.getConfigFile());

            // Load configuration
            FlamingockConfig config = ConfigLoader.loadConfig(parent.getConfigFile());

            // Create audit service
            AuditService auditService = new AuditService(config);

            // Get audit entries with issues
            logger.debug("Fetching audit entries with issues");
            List<AuditEntryIssue> issues = auditService.listAuditEntriesWithIssues();

            // Display results
            if (issues.isEmpty()) {
                System.out.println("\n✅ No issues found! All changes are in consistent state.\n");
            } else {
                if (json) {
                    // Output in JSON format
                    JsonFormatter formatter = new JsonFormatter();
                    formatter.printIssueList(issues);
                } else {
                    // Output as table
                    System.out.println("\nCurrent Issues (Changes with Inconsistent Audits):");
                    System.out.println("========================================================");
                    System.out.println();

                    TableFormatter formatter = new TableFormatter();
                    formatter.printBasicTable(issues.stream().map(AuditEntryIssue::getAuditEntry).collect(Collectors.toList()));

                    System.out.println();
                    System.out.println("Total issues: " + issues.size());
                    System.out.println();
                    System.out.println("🔧 Use '" + inViolet("flamingock issue get -c <change-id> --guidance") + "' for detailed analysis and resolution guidance.\n");
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to list issues: " + e.getMessage(), e);
        }
    }

}