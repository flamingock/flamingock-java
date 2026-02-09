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
package io.flamingock.cli.executor.command;

import io.flamingock.cli.executor.FlamingockExecutorCli;
import io.flamingock.cli.executor.orchestration.CommandExecutor;
import io.flamingock.cli.executor.orchestration.CommandResult;
import io.flamingock.cli.executor.orchestration.ExecutionOptions;
import io.flamingock.cli.executor.output.ConsoleFormatter;
import io.flamingock.cli.executor.output.ExecutionResultFormatter;
import io.flamingock.cli.executor.util.VersionProvider;
import io.flamingock.internal.common.core.operation.OperationType;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Command to apply pending Flamingock changes.
 *
 * <p>This command spawns the user's application JAR with special flags
 * that enable CLI mode in Flamingock, executes all pending changes,
 * and returns the result via exit code.</p>
 *
 * <p>Exit codes:</p>
 * <ul>
 *   <li>0 - Success (all changes applied)</li>
 *   <li>1 - Failure (execution error or change failed)</li>
 *   <li>2 - Usage error (invalid CLI arguments)</li>
 *   <li>126 - JAR not found</li>
 * </ul>
 */
@Command(
        name = "apply",
        description = "Apply pending Flamingock changes",
        mixinStandardHelpOptions = true
)
public class ApplyCommand implements Callable<Integer> {

    /**
     * Exit code when JAR file is not found.
     */
    public static final int EXIT_JAR_NOT_FOUND = 126;

    @ParentCommand
    private ExecuteCommand parent;

    @Option(names = {"--jar", "-j"},
            description = "Path to the application JAR",
            required = true)
    private File jarFile;

    private final CommandExecutor commandExecutor;

    /**
     * Creates a new ApplyCommand with default dependencies.
     */
    public ApplyCommand() {
        this(new CommandExecutor());
    }

    /**
     * Creates a new ApplyCommand with the specified CommandExecutor.
     *
     * @param commandExecutor the command executor to use
     */
    public ApplyCommand(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Integer call() {
        FlamingockExecutorCli root = getRootCommand();
        boolean quiet = root != null && root.isQuiet();
        Optional<String> logLevel = root != null ? root.getLogLevel() : Optional.empty();

        // Print header unless quiet mode
        if (!quiet) {
            ConsoleFormatter.printHeader(VersionProvider.getVersionString());
        }

        // Validate JAR exists
        if (!jarFile.exists()) {
            ConsoleFormatter.printError("JAR file not found: " + jarFile.getAbsolutePath());
            return EXIT_JAR_NOT_FOUND;
        }

        if (!jarFile.isFile()) {
            ConsoleFormatter.printError("Path is not a file: " + jarFile.getAbsolutePath());
            return EXIT_JAR_NOT_FOUND;
        }

        // Execution ops: always stream output by default
        ExecutionOptions options = ExecutionOptions.builder()
                .logLevel(logLevel.orElse(null))
                .streamOutput(true)
                .build();

        CommandResult<ExecuteResponseData> result = commandExecutor.execute(
                jarFile.getAbsolutePath(),
                OperationType.EXECUTE_APPLY,
                ExecuteResponseData.class,
                options
        );

        if (result.isSuccess()) {
            if (!quiet) {
                // Print detailed execution summary
                ExecuteResponseData data = result.getData();
                if (data != null) {
                    ExecutionResultFormatter.print(data);
                } else {
                    ConsoleFormatter.printSuccess(result.getDurationMs());
                }
            }
            return 0;
        } else {
            // Print execution summary if available (even on failure, shows what was applied)
            if (!quiet && result.getData() != null) {
                ExecutionResultFormatter.print(result.getData());
            }
            ConsoleFormatter.printFailure(result.getErrorCode(), result.getErrorMessage());
            return result.getExitCode();
        }
    }

    private FlamingockExecutorCli getRootCommand() {
        if (parent == null) {
            return null;
        }
        // Navigate up the command hierarchy to find the root
        CommandLine.Model.CommandSpec spec = parent.getClass().getAnnotation(Command.class) != null
                ? CommandLine.Model.CommandSpec.forAnnotatedObject(parent)
                : null;

        // Try to get the parent directly through field access
        try {
            java.lang.reflect.Field parentField = ExecuteCommand.class.getDeclaredField("parent");
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
