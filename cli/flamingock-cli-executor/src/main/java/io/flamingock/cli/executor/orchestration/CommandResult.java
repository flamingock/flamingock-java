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

import io.flamingock.cli.executor.process.LaunchResult;
import io.flamingock.cli.executor.process.LaunchStatus;
import io.flamingock.cli.executor.result.ResponseResultReader.ResponseResult;

/**
 * Represents the result of a CLI command execution.
 * Encapsulates both launch-level and response-level outcomes.
 *
 * @param <T> the type of the response data
 */
public class CommandResult<T> {

    private final boolean success;
    private final T data;
    private final String errorCode;
    private final String errorMessage;
    private final int exitCode;
    private final long durationMs;
    private final LaunchStatus launchStatus;

    private CommandResult(boolean success, T data, String errorCode, String errorMessage,
                          int exitCode, long durationMs, LaunchStatus launchStatus) {
        this.success = success;
        this.data = data;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.exitCode = exitCode;
        this.durationMs = durationMs;
        this.launchStatus = launchStatus;
    }

    /**
     * Creates a successful result with data.
     *
     * @param data       the response data
     * @param durationMs the execution duration
     * @param <T>        the data type
     * @return a success result
     */
    public static <T> CommandResult<T> success(T data, long durationMs) {
        return new CommandResult<>(true, data, null, null, 0, durationMs, LaunchStatus.SUCCESS);
    }

    /**
     * Creates a result for entry point not found error.
     *
     * @param exitCode the process exit code
     * @param <T>      the data type
     * @return an entry point not found result
     */
    public static <T> CommandResult<T> entryPointNotFound(int exitCode) {
        return new CommandResult<>(
                false,
                null,
                "ENTRY_POINT_NOT_FOUND",
                "Flamingock CLI entry point not found in your JAR.\n\n" +
                        "Your uber/shaded JAR must include 'flamingock-core' classes.\n" +
                        "If you're using Maven Shade or Gradle Shadow plugin, ensure flamingock-core\n" +
                        "is not excluded from the shading process.\n\n" +
                        "Hint: Check that 'io.flamingock.core.cli.FlamingockCliMainEntryPoint' is present:\n" +
                        "  jar tf your-app.jar | grep FlamingockCliMainEntryPoint",
                exitCode,
                0,
                LaunchStatus.ENTRY_POINT_NOT_FOUND
        );
    }

    /**
     * Creates a result for JAR analysis failure.
     *
     * @param errorMessage the error message
     * @param <T>          the data type
     * @return a JAR analysis failed result
     */
    public static <T> CommandResult<T> jarAnalysisFailed(String errorMessage) {
        return new CommandResult<>(
                false,
                null,
                "JAR_ANALYSIS_FAILED",
                "Failed to analyze JAR: " + errorMessage,
                1,
                0,
                LaunchStatus.JAR_ANALYSIS_FAILED
        );
    }

    /**
     * Creates a result for process start failure.
     *
     * @param errorMessage the error message
     * @param <T>          the data type
     * @return a process start failed result
     */
    public static <T> CommandResult<T> processStartFailed(String errorMessage) {
        return new CommandResult<>(
                false,
                null,
                "PROCESS_START_FAILED",
                "Failed to start process: " + errorMessage,
                1,
                0,
                LaunchStatus.PROCESS_START_FAILED
        );
    }

    /**
     * Creates a result for process interrupted.
     *
     * @param <T> the data type
     * @return a process interrupted result
     */
    public static <T> CommandResult<T> processInterrupted() {
        return new CommandResult<>(
                false,
                null,
                "PROCESS_INTERRUPTED",
                "Process was interrupted",
                1,
                0,
                LaunchStatus.PROCESS_INTERRUPTED
        );
    }

    /**
     * Creates a result for missing Flamingock runtime.
     *
     * <p>This occurs when the JAR does not contain the Flamingock CLI entry point,
     * which can happen in two scenarios:
     * <ol>
     *   <li>User provided a thin JAR without bundled dependencies</li>
     *   <li>User built an uber JAR but flamingock-core was excluded/relocated</li>
     * </ol>
     *
     * @param <T> the data type
     * @return a missing Flamingock runtime result
     */
    public static <T> CommandResult<T> missingFlamingockRuntime() {
        return new CommandResult<>(
                false,
                null,
                "MISSING_FLAMINGOCK_RUNTIME",
                "Your JAR does not include the Flamingock CLI entry point.\n\n" +
                        "This usually means one of the following:\n\n" +
                        "1. THIN JAR: You provided a thin JAR without bundled dependencies.\n" +
                        "   Solution: Build an uber/shaded JAR that includes all dependencies.\n\n" +
                        "2. MISSING DEPENDENCY: You built an uber JAR but flamingock-core\n" +
                        "   was not included (wrong scope, excluded, or relocated).\n" +
                        "   Solution: Ensure flamingock-core is an 'implementation' dependency\n" +
                        "   and not excluded from your shading configuration.\n\n" +
                        "To create an uber JAR:\n\n" +
                        "  Maven:  maven-shade-plugin\n" +
                        "          https://maven.apache.org/plugins/maven-shade-plugin/\n\n" +
                        "  Gradle: Shadow plugin or custom Jar task\n" +
                        "          https://github.com/johnrengelman/shadow\n\n" +
                        "Example Gradle uber JAR task:\n\n" +
                        "  val uberJar by tasks.registering(Jar::class) {\n" +
                        "      archiveClassifier.set(\"uber\")\n" +
                        "      duplicatesStrategy = DuplicatesStrategy.EXCLUDE\n" +
                        "      from(sourceSets.main.get().output)\n" +
                        "      from(configurations.runtimeClasspath.get().map { zipTree(it) })\n" +
                        "  }\n\n" +
                        "Verify the entry point is present:\n" +
                        "  jar tf your-app.jar | grep FlamingockCliMainEntryPoint",
                1,
                0,
                LaunchStatus.MISSING_FLAMINGOCK_RUNTIME
        );
    }

    /**
     * Creates a result from a launch failure.
     *
     * @param launchResult the launch result
     * @param <T>          the data type
     * @return a command result based on the launch failure
     */
    public static <T> CommandResult<T> fromLaunchFailure(LaunchResult launchResult) {
        switch (launchResult.getStatus()) {
            case MISSING_FLAMINGOCK_RUNTIME:
                return missingFlamingockRuntime();
            case ENTRY_POINT_NOT_FOUND:
                return entryPointNotFound(launchResult.getExitCode());
            case JAR_ANALYSIS_FAILED:
                return jarAnalysisFailed(launchResult.getErrorDetail());
            case PROCESS_START_FAILED:
                return processStartFailed(launchResult.getErrorDetail());
            case PROCESS_INTERRUPTED:
                return processInterrupted();
            case PROCESS_FAILED:
            default:
                return new CommandResult<>(
                        false,
                        null,
                        "PROCESS_FAILED",
                        launchResult.getErrorDetail() != null
                                ? launchResult.getErrorDetail()
                                : "Process exited with code " + launchResult.getExitCode(),
                        launchResult.getExitCode(),
                        0,
                        launchResult.getStatus()
                );
        }
    }

    /**
     * Creates a result from a response result.
     *
     * @param responseResult the response result
     * @param <T>            the data type
     * @return a command result based on the response
     */
    public static <T> CommandResult<T> fromResponse(ResponseResult<T> responseResult) {
        if (responseResult.isSuccess()) {
            return success(responseResult.getData(), responseResult.getDurationMs());
        } else {
            return new CommandResult<>(
                    false,
                    null,
                    responseResult.getErrorCode(),
                    responseResult.getErrorMessage(),
                    1,
                    responseResult.getDurationMs(),
                    LaunchStatus.SUCCESS // Launch succeeded, but response indicated failure
            );
        }
    }

    /**
     * Creates a result for response file read error.
     *
     * @param errorMessage the error message
     * @param exitCode     the process exit code
     * @param <T>          the data type
     * @return a response read error result
     */
    public static <T> CommandResult<T> responseReadError(String errorMessage, int exitCode) {
        return new CommandResult<>(
                false,
                null,
                "RESPONSE_READ_ERROR",
                errorMessage,
                exitCode != 0 ? exitCode : 1,
                0,
                LaunchStatus.SUCCESS // Launch succeeded, but response couldn't be read
        );
    }

    /**
     * Returns whether the command was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the response data.
     *
     * @return the data, or null if not successful
     */
    public T getData() {
        return data;
    }

    /**
     * Returns the error code.
     *
     * @return the error code, or null if successful
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the error message.
     *
     * @return the error message, or null if successful
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the exit code for the command.
     *
     * @return the exit code
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Returns the execution duration in milliseconds.
     *
     * @return the duration
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * Returns the launch status.
     *
     * @return the launch status
     */
    public LaunchStatus getLaunchStatus() {
        return launchStatus;
    }

    /**
     * Checks if this was a launch-level failure (before response could be read).
     *
     * @return true if launch failed
     */
    public boolean isLaunchFailure() {
        return launchStatus != LaunchStatus.SUCCESS;
    }
}
