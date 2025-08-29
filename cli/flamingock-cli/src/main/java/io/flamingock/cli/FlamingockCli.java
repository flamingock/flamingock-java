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
package io.flamingock.cli;

import io.flamingock.cli.command.AuditCommand;
import io.flamingock.cli.logging.LoggingMixin;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(
    name = "flamingock",
    description = "Flamingock CLI - Database change management and audit operations",
    subcommands = {AuditCommand.class},
    mixinStandardHelpOptions = true,
    version = "Flamingock CLI 1.0.0"
)
public class FlamingockCli implements Runnable {

    @Option(names = {"-c", "--config"}, description = "Path to configuration file (default: flamingock.yml)")
    private String configFile = "flamingock.yml";
    
    @Mixin
    private LoggingMixin loggingMixin;

    public static void main(String[] args) {
        FlamingockCli cli = new FlamingockCli();
        CommandLine cmd = new CommandLine(cli);
        
        cmd.setExecutionStrategy(parseResult -> {
            cli.loggingMixin.initializeLogging();
            return new CommandLine.RunLast().execute(parseResult);
        });
        
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            System.err.println("Error: " + ex.getMessage());
            if (System.getProperty("flamingock.debug") != null) {
                ex.printStackTrace();
            }
            return 1;
        });
        
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // Initialize logging when any command is about to run
        loggingMixin.initializeLogging();
        
        // This runs when no subcommand is specified - show help
        new CommandLine(this).usage(System.out);
    }
    
    public String getConfigFile() {
        return configFile;
    }
}