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
package io.flamingock.cli.executor.process;

/**
 * Represents the result of launching a JVM process.
 * Provides structured information about the launch outcome.
 */
public class LaunchResult {

    private final LaunchStatus status;
    private final int exitCode;
    private final String errorDetail;

    private LaunchResult(LaunchStatus status, int exitCode, String errorDetail) {
        this.status = status;
        this.exitCode = exitCode;
        this.errorDetail = errorDetail;
    }

    /**
     * Creates a successful launch result.
     *
     * @return a success result with exit code 0
     */
    public static LaunchResult success() {
        return new LaunchResult(LaunchStatus.SUCCESS, 0, null);
    }

    /**
     * Creates a result indicating the entry point was not found.
     *
     * @param exitCode the process exit code
     * @return an entry point not found result
     */
    public static LaunchResult entryPointNotFound(int exitCode) {
        return new LaunchResult(LaunchStatus.ENTRY_POINT_NOT_FOUND, exitCode, null);
    }

    /**
     * Creates a result indicating the process failed to start.
     *
     * @param errorMessage the error message
     * @return a process start failed result
     */
    public static LaunchResult processStartFailed(String errorMessage) {
        return new LaunchResult(LaunchStatus.PROCESS_START_FAILED, 1, errorMessage);
    }

    /**
     * Creates a result indicating the process was interrupted.
     *
     * @return a process interrupted result
     */
    public static LaunchResult processInterrupted() {
        return new LaunchResult(LaunchStatus.PROCESS_INTERRUPTED, 1, "Process was interrupted");
    }

    /**
     * Creates a result indicating the process failed with a non-zero exit code.
     *
     * @param exitCode the process exit code
     * @return a process failed result
     */
    public static LaunchResult processFailed(int exitCode) {
        return new LaunchResult(LaunchStatus.PROCESS_FAILED, exitCode, null);
    }

    /**
     * Creates a result indicating JAR analysis failed.
     *
     * @param errorMessage the error message
     * @return a JAR analysis failed result
     */
    public static LaunchResult jarAnalysisFailed(String errorMessage) {
        return new LaunchResult(LaunchStatus.JAR_ANALYSIS_FAILED, 1, errorMessage);
    }

    /**
     * Creates a result indicating the JAR is missing the Flamingock runtime.
     *
     * @return a missing Flamingock runtime result
     */
    public static LaunchResult missingFlamingockRuntime() {
        return new LaunchResult(LaunchStatus.MISSING_FLAMINGOCK_RUNTIME, 1, null);
    }

    /**
     * Returns the launch status.
     *
     * @return the status
     */
    public LaunchStatus getStatus() {
        return status;
    }

    /**
     * Returns the process exit code.
     *
     * @return the exit code
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Returns additional error details, if any.
     *
     * @return the error detail, or null
     */
    public String getErrorDetail() {
        return errorDetail;
    }

    /**
     * Checks if the launch was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return status == LaunchStatus.SUCCESS;
    }

    /**
     * Checks if the failure was due to entry point not found.
     *
     * @return true if entry point was not found
     */
    public boolean isEntryPointNotFound() {
        return status == LaunchStatus.ENTRY_POINT_NOT_FOUND;
    }

    /**
     * Checks if the launch resulted in any kind of failure.
     *
     * @return true if any failure occurred
     */
    public boolean isFailure() {
        return status != LaunchStatus.SUCCESS;
    }
}
