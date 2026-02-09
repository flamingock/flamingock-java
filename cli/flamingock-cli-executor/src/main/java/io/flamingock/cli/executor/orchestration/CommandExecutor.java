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
package io.flamingock.cli.executor.orchestration;

import io.flamingock.cli.executor.process.JvmLauncher;
import io.flamingock.cli.executor.process.LaunchResult;
import io.flamingock.cli.executor.result.ResponseResultReader;
import io.flamingock.cli.executor.result.ResponseResultReader.ResponseResult;
import io.flamingock.internal.common.core.operation.OperationType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orchestrates the execution of CLI commands.
 *
 * <p>This class handles the common flow of:
 * <ol>
 *   <li>Creating a temporary output file for response communication</li>
 *   <li>Launching the JVM process with the user's JAR</li>
 *   <li>Handling common launch failures (entry point not found, process errors)</li>
 *   <li>Reading and parsing the response file</li>
 *   <li>Cleaning up temporary files</li>
 * </ol>
 *
 * <p>By centralizing this logic, individual commands only need to handle
 * their specific presentation logic.</p>
 */
public class CommandExecutor {

    private final JvmLauncher launcher;
    private final ResponseResultReader reader;

    /**
     * Creates a new CommandExecutor with default dependencies.
     */
    public CommandExecutor() {
        this(new JvmLauncher(), new ResponseResultReader());
    }

    /**
     * Creates a new CommandExecutor with the specified dependencies.
     *
     * @param launcher the JVM launcher
     * @param reader   the response result reader
     */
    public CommandExecutor(JvmLauncher launcher, ResponseResultReader reader) {
        this.launcher = launcher;
        this.reader = reader;
    }

    /**
     * Executes a command by launching the user's JAR and reading the response.
     *
     * <p>This method handles all common error scenarios:
     * <ul>
     *   <li>Entry point not found - JAR missing flamingock-core classes</li>
     *   <li>Process start failures - java not found, permissions, etc.</li>
     *   <li>Process interruption</li>
     *   <li>Response file read errors</li>
     * </ul>
     *
     * @param jarPath      the path to the user's JAR file
     * @param operation    the Flamingock operation to execute
     * @param responseType the expected type of the response data
     * @param options      execution options (log level, stream output, etc.)
     * @param <T>          the response data type
     * @return the command result
     */
    public <T> CommandResult<T> execute(
            String jarPath,
            OperationType operation,
            Class<T> responseType,
            ExecutionOptions options
    ) {
        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("flamingock-response-", ".json");

            LaunchResult launchResult = launcher.launch(
                    jarPath,
                    operation,
                    outputFile.toString(),
                    options.getLogLevel(),
                    options.isStreamOutput(),
                    options.getOperationArgs()
            );

            // Handle launch-level failures - don't try to read response file
            if (launchResult.isFailure()) {
                return CommandResult.fromLaunchFailure(launchResult);
            }

            // Launch succeeded, read the response file
            ResponseResult<T> responseResult = reader.readTyped(outputFile, responseType);

            if (responseResult.isSuccess()) {
                return CommandResult.success(responseResult.getData(), responseResult.getDurationMs());
            } else {
                return CommandResult.fromResponse(responseResult);
            }

        } catch (IOException e) {
            return CommandResult.processStartFailed("Failed to create temporary file: " + e.getMessage());
        } finally {
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                    // Best effort cleanup
                }
            }
        }
    }
}
