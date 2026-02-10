/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.cli.executor.command;

import io.flamingock.cli.executor.FlamingockExecutorCli;
import io.flamingock.cli.executor.orchestration.CommandExecutor;
import io.flamingock.cli.executor.orchestration.CommandResult;
import io.flamingock.cli.executor.orchestration.ExecutionOptions;
import io.flamingock.cli.executor.output.ConsoleFormatter;
import io.flamingock.cli.executor.output.IssueFormatter;
import io.flamingock.cli.executor.output.JsonFormatter;
import io.flamingock.cli.executor.util.VersionProvider;
import io.flamingock.internal.common.core.operation.OperationType;
import io.flamingock.internal.common.core.response.data.IssueListResponseData;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Command to list audit issues from the Flamingock audit store.
 */
@Command(
        name = "list",
        aliases = "ls",
        description = "List changes with audit issues",
        mixinStandardHelpOptions = true
)
public class ListIssueCommand implements Callable<Integer> {

    public static final int EXIT_JAR_NOT_FOUND = 126;

    @ParentCommand
    private IssueCommand parent;

    @Option(names = {"--jar", "-j"},
            description = "Path to the application JAR",
            required = true)
    private File jarFile;

    @Option(names = {"--json"},
            description = "Output in JSON format")
    private boolean json;

    private final CommandExecutor commandExecutor;

    public ListIssueCommand() {
        this(new CommandExecutor());
    }

    public ListIssueCommand(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Integer call() {
        FlamingockExecutorCli root = getRootCommand();
        boolean quiet = root != null && root.isQuiet();
        Optional<String> logLevel = root != null ? root.getLogLevel() : Optional.empty();

        if (!quiet && !json) {
            ConsoleFormatter.printHeader(VersionProvider.getVersionString());
        }

        if (!jarFile.exists()) {
            ConsoleFormatter.printError("JAR file not found: " + jarFile.getAbsolutePath());
            return EXIT_JAR_NOT_FOUND;
        }

        if (!jarFile.isFile()) {
            ConsoleFormatter.printError("Path is not a file: " + jarFile.getAbsolutePath());
            return EXIT_JAR_NOT_FOUND;
        }

        ExecutionOptions options = ExecutionOptions.builder()
                .logLevel(logLevel.orElse(null))
                .streamOutput(logLevel.isPresent())
                .build();

        CommandResult<IssueListResponseData> result = commandExecutor.execute(
                jarFile.getAbsolutePath(),
                OperationType.ISSUE_LIST,
                IssueListResponseData.class,
                options
        );

        if (result.isSuccess()) {
            IssueListResponseData data = result.getData();
            if (json) {
                JsonFormatter.print(data);
            } else {
                displayIssues(data, quiet);
            }
            return 0;
        } else {
            ConsoleFormatter.printFailure(result.getErrorCode(), result.getErrorMessage());
            return result.getExitCode();
        }
    }

    private void displayIssues(IssueListResponseData data, boolean quiet) {
        if (data == null || data.getIssues() == null || data.getIssues().isEmpty()) {
            if (!quiet) {
                ConsoleFormatter.printInfo("No issues found. All changes are healthy.");
            }
            return;
        }

        System.out.println();
        IssueFormatter.printList(data);
        System.out.println();
        System.out.println("Total: " + data.getIssues().size() + " issues");
    }

    private FlamingockExecutorCli getRootCommand() {
        if (parent == null) {
            return null;
        }
        try {
            java.lang.reflect.Field parentField = IssueCommand.class.getDeclaredField("parent");
            parentField.setAccessible(true);
            Object grandParent = parentField.get(parent);
            if (grandParent instanceof FlamingockExecutorCli) {
                return (FlamingockExecutorCli) grandParent;
            }
        } catch (Exception e) {
            // Fall through
        }
        return null;
    }
}
