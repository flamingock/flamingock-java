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
import io.flamingock.cli.executor.util.VersionProvider;
import io.flamingock.internal.common.core.operation.OperationType;
import io.flamingock.internal.common.core.recovery.Resolution;
import io.flamingock.internal.common.core.response.data.AuditFixResponseData;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Command to fix audit state for a change with issues.
 *
 * <p>This command marks a change as either APPLIED or ROLLED_BACK,
 * resolving any audit issues that are blocking execution.</p>
 */
@Command(
        name = "fix",
        description = "Fix audit state for a change with issues",
        mixinStandardHelpOptions = true
)
public class FixCommand implements Callable<Integer> {

    public static final int EXIT_JAR_NOT_FOUND = 126;

    @ParentCommand
    private AuditCommand parent;

    @Option(names = {"--jar", "-j"},
            description = "Path to the application JAR",
            required = true)
    private File jarFile;

    @Option(names = {"-c", "--change-id"},
            description = "The change ID to fix",
            required = true)
    private String changeId;

    @Option(names = {"-r", "--resolution"},
            description = "Resolution type: APPLIED or ROLLED_BACK",
            required = true)
    private Resolution resolution;

    private final CommandExecutor commandExecutor;

    public FixCommand() {
        this(new CommandExecutor());
    }

    public FixCommand(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Integer call() {
        FlamingockExecutorCli root = getRootCommand();
        boolean quiet = root != null && root.isQuiet();
        Optional<String> logLevel = root != null ? root.getLogLevel() : Optional.empty();

        if (!quiet) {
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

        Map<String, String> operationArgs = new HashMap<>();
        operationArgs.put("flamingock.change-id", changeId);
        operationArgs.put("flamingock.resolution", resolution.name());

        ExecutionOptions options = ExecutionOptions.builder()
                .logLevel(logLevel.orElse(null))
                .streamOutput(logLevel.isPresent())
                .operationArgs(operationArgs)
                .build();

        CommandResult<AuditFixResponseData> result = commandExecutor.execute(
                jarFile.getAbsolutePath(),
                OperationType.AUDIT_FIX,
                AuditFixResponseData.class,
                options
        );

        if (result.isSuccess()) {
            AuditFixResponseData data = result.getData();
            if (data != null) {
                if ("APPLIED".equals(data.getResult())) {
                    ConsoleFormatter.printSuccess(0);
                    System.out.println(data.getMessage());
                } else {
                    ConsoleFormatter.printInfo(data.getMessage());
                }
            }
            return 0;
        } else {
            ConsoleFormatter.printFailure(result.getErrorCode(), result.getErrorMessage());
            return result.getExitCode();
        }
    }

    private FlamingockExecutorCli getRootCommand() {
        if (parent == null) {
            return null;
        }
        try {
            java.lang.reflect.Field parentField = AuditCommand.class.getDeclaredField("parent");
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
