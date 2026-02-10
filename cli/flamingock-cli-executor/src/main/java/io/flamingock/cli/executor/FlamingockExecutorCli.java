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
package io.flamingock.cli.executor;

import io.flamingock.cli.executor.command.AuditCommand;
import io.flamingock.cli.executor.command.ExecuteCommand;
import io.flamingock.cli.executor.command.IssueCommand;
import io.flamingock.cli.executor.handler.ExecutorExceptionHandler;
import io.flamingock.cli.executor.util.VersionProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Optional;

/**
 * Main entry point for the Flamingock CLI.
 *
 * <p>This CLI tool spawns the user's application with Flamingock
 * and executes changes, returning the result via exit codes.</p>
 *
 * <p>Usage: flamingock execute apply --jar ./app.jar</p>
 */
@Command(
        name = "flamingock",
        description = "Flamingock CLI - Execute Flamingock changes",
        header = {
                "@|bold,cyan Flamingock CLI|@ - Execute Flamingock changes",
                ""
        },
        footer = {
                "",
                "@|bold Examples:|@",
                "  flamingock execute apply --jar ./app.jar",
                "  flamingock audit list --jar ./app.jar",
                "  flamingock audit fix --jar ./app.jar -c my-change-id -r APPLIED",
                "  flamingock issue list --jar ./app.jar",
                "  flamingock issue get --jar ./app.jar -c my-change-id --guidance",
                "  flamingock --log-level=debug execute apply --jar ./my-app.jar",
                "",
                "For detailed help on any command, use: flamingock <command> --help"
        },
        subcommands = {ExecuteCommand.class, AuditCommand.class, IssueCommand.class},
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class
)
public class FlamingockExecutorCli implements Runnable {

    @Option(names = {"--log-level", "-l"},
            description = "Application log level: debug, info, warn, error",
            scope = CommandLine.ScopeType.INHERIT)
    private String logLevel;

    @Option(names = {"--quiet", "-q"},
            description = "Suppress non-essential output",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean quiet;

    @Option(names = {"--no-color"},
            description = "Disable colored output",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean noColor;

    public static void main(String[] args) {
        FlamingockExecutorCli cli = new FlamingockExecutorCli();
        CommandLine cmd = new CommandLine(cli);

        // Use custom exception handler for better error messages
        cmd.setExecutionExceptionHandler(new ExecutorExceptionHandler());

        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // This runs when no subcommand is specified - show help
        new CommandLine(this).usage(System.out);
    }

    public Optional<String> getLogLevel() {
        return Optional.ofNullable(logLevel);
    }

    public boolean isQuiet() {
        return quiet;
    }

    public boolean isNoColor() {
        return noColor;
    }
}
