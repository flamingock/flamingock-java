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
package io.flamingock.cli.command.audit;

import io.flamingock.cli.config.ConfigLoader;
import io.flamingock.cli.config.FlamingockConfig;
import io.flamingock.cli.service.AuditService;
import io.flamingock.internal.common.core.recovery.FixResult;
import io.flamingock.internal.common.core.recovery.Resolution;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "fix",
    description = "Fix audit state for a change with inconsistent audit"
)
public class FixCommand implements Runnable {
    
    private static final Logger logger = FlamingockLoggerFactory.getLogger("FixCommand");

    @ParentCommand
    private AuditCommand parent;

    @Option(names = {"-c", "--change-id"}, required = true, description = "Change ID to fix")
    private String changeId;

    @Option(names = {"-r", "--resolution"}, required = true, description = "Resolution state: APPLIED or ROLLED_BACK")
    private Resolution resolution;

    @Override
    public void run() {
        try {
            logger.info("Fixing audit issue for changeId: {} with resolution: {}", changeId, resolution);
            logger.debug("Configuration file: {}", parent.getConfigFile());
            
            // Load configuration
            FlamingockConfig config = ConfigLoader.loadConfig(parent.getConfigFile());
            
            // Create audit service
            AuditService auditService = new AuditService(config);
            
            // Fix the audit issue
            logger.debug("Calling fixAuditIssue with changeId: {} and resolution: {}", changeId, resolution);
            FixResult result = auditService.fixAuditIssue(changeId, resolution);
            
            // Handle different results with appropriate messaging
            switch (result) {
                case APPLIED:
                    System.out.println("✅ Successfully fixed audit state for change '" + changeId + "'");
                    System.out.println("   Resolution applied: " + resolution);
                    
                    if (resolution == Resolution.ROLLED_BACK) {
                        System.out.println("   📝 Note: Flamingock will retry this change in the next execution.");
                    }
                    break;
                    
                case NO_ISSUE_FOUND:
                    System.out.println("⚠️  Fix not applied for change '" + changeId + "'");
                    System.out.println("   Reason: The audit state is already consistent and healthy.");
                    System.out.println("   No action required - change has no issues to fix.");
                    break;
                    
                default:
                    System.err.println("❌ Unexpected result: " + result);
                    System.exit(1);
            }
            
        } catch (IllegalArgumentException e) {
            System.err.println("❌ Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            logger.error("Failed to fix audit issue", e);
            System.err.println("❌ Failed to fix audit issue: " + e.getMessage());
            System.exit(1);
        }
    }
}