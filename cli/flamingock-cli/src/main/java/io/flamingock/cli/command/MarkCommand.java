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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "mark",
    description = "Mark a change unit with a specific state"
)
public class MarkCommand implements Runnable {

    @ParentCommand
    private AuditCommand parent;

    @Option(names = {"--change-unit", "-c"}, required = true, description = "The change unit ID to mark")
    private String changeUnit;

    @Option(names = {"--state", "-s"}, required = true, 
            description = "The state to mark the change unit as. Valid values: ${COMPLETION-CANDIDATES}")
    private MarkState state;

    public enum MarkState {
        EXECUTED,
        ROLLED_BACK
    }

    @Override
    public void run() {
        try {
            // Load configuration
            FlamingockConfig config = ConfigLoader.loadConfig(parent.getConfigFile());
            
            // Create audit service
            AuditService auditService = new AuditService(config);
            
            // Mark the change unit based on state
            switch (state) {
                case EXECUTED:
                    auditService.markAsSuccess(changeUnit);
                    System.out.println("Change unit '" + changeUnit + "' marked as EXECUTED successfully.");
                    break;
                case ROLLED_BACK:
                    auditService.markAsRolledBack(changeUnit);
                    System.out.println("Change unit '" + changeUnit + "' marked as ROLLED_BACK successfully.");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported state: " + state);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to mark change unit: " + e.getMessage(), e);
        }
    }
}