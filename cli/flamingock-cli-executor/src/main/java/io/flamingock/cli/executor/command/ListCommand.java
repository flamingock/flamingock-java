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
import io.flamingock.cli.executor.output.ConsoleFormatter;
import io.flamingock.cli.executor.output.TableFormatter;
import io.flamingock.cli.executor.process.JvmLauncher;
import io.flamingock.cli.executor.result.ResponseResultReader;
import io.flamingock.cli.executor.result.ResponseResultReader.ResponseResult;
import io.flamingock.cli.executor.util.VersionProvider;
import io.flamingock.internal.common.core.response.data.AuditListResponseData;
import io.flamingock.internal.common.core.response.data.AuditListResponseData.AuditEntryDto;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    /**
     * Operation string for LIST operation (matches FlamingockArguments parsing).
     */
    private static final String OPERATION_LIST = "LIST";

    @ParentCommand
    private AuditCommand parent;

    @Option(names = {"--jar", "-j"},
            description = "Path to the application JAR",
            required = true)
    private File jarFile;

    @Override
    public Integer call() {
        FlamingockExecutorCli root = getRootCommand();
        boolean quiet = root != null && root.isQuiet();
        Optional<String> logLevel = root != null ? root.getLogLevel() : Optional.empty();

        // Non-execution ops: only stream output if log level is explicitly set
        boolean streamOutput = logLevel.isPresent();

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

        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("flamingock-response-", ".json");

            JvmLauncher launcher = new JvmLauncher();
            int exitCode = launcher.launch(
                    jarFile.getAbsolutePath(),
                    OPERATION_LIST,
                    outputFile.toString(),
                    logLevel.orElse(null),
                    streamOutput
            );

            ResponseResultReader reader = new ResponseResultReader();
            ResponseResult<AuditListResponseData> result = reader.readTyped(outputFile, AuditListResponseData.class);

            if (exitCode == 0 && result.isSuccess()) {
                if (result.getData() != null) {
                    displayAuditEntries(result.getData().getEntries(), quiet);
                }
                return 0;
            } else {
                ConsoleFormatter.printFailure(result.getErrorCode(), result.getErrorMessage());
                return exitCode != 0 ? exitCode : 1;
            }

        } catch (IOException e) {
            ConsoleFormatter.printError("Failed to create temporary file: " + e.getMessage());
            return 1;
        } finally {
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                }
            }
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
        tableFormatter.printBasicTable(entries);

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
