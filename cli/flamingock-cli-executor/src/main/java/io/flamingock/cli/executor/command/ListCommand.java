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
import io.flamingock.cli.executor.output.TableFormatter;
import io.flamingock.cli.executor.util.VersionProvider;
import io.flamingock.internal.common.core.operation.OperationType;
import io.flamingock.internal.common.core.response.data.AuditListResponseData;
import io.flamingock.internal.common.core.response.data.AuditListResponseData.AuditEntryDto;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Command to list audit entries from the Flamingock audit store.
 *
 * <p>This command spawns the user's application JAR with special flags
 * that enable CLI mode in Flamingock and executes the LIST operation,
 * which retrieves the audit history.</p>
 *
 * <p>Exit codes:</p>
 * <ul>
 *   <li>0 - Success</li>
 *   <li>1 - Failure (execution error)</li>
 *   <li>2 - Usage error (invalid CLI arguments)</li>
 *   <li>126 - JAR not found</li>
 * </ul>
 */
@Command(
        name = "list",
        description = "List audit entries from the change history",
        mixinStandardHelpOptions = true
)
public class ListCommand implements Callable<Integer> {

    /**
     * Exit code when JAR file is not found.
     */
    public static final int EXIT_JAR_NOT_FOUND = 126;

    @ParentCommand
    private AuditCommand parent;

    @Option(names = {"--jar", "-j"},
            description = "Path to the application JAR",
            required = true)
    private File jarFile;

    @Option(names = {"--history"},
            description = "Show full chronological history instead of snapshot")
    private boolean history;

    @Option(names = {"--since"},
            description = "Filter entries since date (ISO-8601: yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss)")
    private String since;

    @Option(names = {"-e", "--extended"},
            description = "Show extended information (execution ID, class, method, hostname)")
    private boolean extended;

    private final CommandExecutor commandExecutor;

    /**
     * Creates a new ListCommand with default dependencies.
     */
    public ListCommand() {
        this(new CommandExecutor());
    }

    /**
     * Creates a new ListCommand with the specified CommandExecutor.
     *
     * @param commandExecutor the command executor to use
     */
    public ListCommand(CommandExecutor commandExecutor) {
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

        // Build operation-specific arguments
        Map<String, String> operationArgs = new HashMap<>();
        if (history) {
            operationArgs.put("flamingock.audit.history", "true");
        }
        if (since != null && !since.isEmpty()) {
            operationArgs.put("flamingock.audit.since", since);
        }
        if (extended) {
            operationArgs.put("flamingock.audit.extended", "true");
        }

        // Non-execution ops: only stream output if log level is explicitly set
        ExecutionOptions options = ExecutionOptions.builder()
                .logLevel(logLevel.orElse(null))
                .streamOutput(logLevel.isPresent())
                .operationArgs(operationArgs)
                .build();

        CommandResult<AuditListResponseData> result = commandExecutor.execute(
                jarFile.getAbsolutePath(),
                OperationType.AUDIT_LIST,
                AuditListResponseData.class,
                options
        );

        if (result.isSuccess()) {
            if (result.getData() != null) {
                displayAuditEntries(result.getData().getEntries(), quiet);
            }
            return 0;
        } else {
            ConsoleFormatter.printFailure(result.getErrorCode(), result.getErrorMessage());
            return result.getExitCode();
        }
    }

    private void displayAuditEntries(List<AuditEntryDto> entries, boolean quiet) {
        if (entries == null || entries.isEmpty()) {
            if (!quiet) {
                ConsoleFormatter.printInfo("No audit entries found.");
            }
            return;
        }

        System.out.println();
        TableFormatter tableFormatter = new TableFormatter();
        if (extended) {
            tableFormatter.printExtendedTable(entries);
        } else {
            tableFormatter.printBasicTable(entries);
        }

        TableFormatter.printStateLegend();

        System.out.println();
        System.out.println("Total: " + entries.size() + " entries");
    }

    private FlamingockExecutorCli getRootCommand() {
        if (parent == null) {
            return null;
        }
        // Navigate up the command hierarchy to find the root
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
