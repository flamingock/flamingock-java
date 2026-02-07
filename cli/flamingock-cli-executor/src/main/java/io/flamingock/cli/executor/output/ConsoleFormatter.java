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
package io.flamingock.cli.executor.output;

/**
 * Provides formatted console output for the CLI executor.
 * Follows professional CLI conventions (docker, kubectl, terraform style).
 */
public final class ConsoleFormatter {

    private ConsoleFormatter() {
    }

    /**
     * Prints the CLI header with version information.
     *
     * @param version the CLI version
     */
    public static void printHeader(String version) {
        System.out.println("flamingock v" + version);
    }

    /**
     * Prints a success message after successful execution.
     *
     * @param durationMs the execution duration in milliseconds
     */
    public static void printSuccess(long durationMs) {
        System.out.println("Completed successfully (" + durationMs + "ms)");
    }

    /**
     * Prints a success message after successful execution.
     */
    public static void printSuccess() {
        System.out.println("Completed successfully");
    }

    /**
     * Prints a failure message after failed execution.
     */
    public static void printFailure() {
        printFailure(null, null);
    }

    /**
     * Prints a failure message with error details.
     *
     * @param errorCode    the error code (optional)
     * @param errorMessage the error message (optional)
     */
    public static void printFailure(String errorCode, String errorMessage) {
        if (errorMessage != null) {
            if (errorCode != null) {
                System.err.println("Error (" + errorCode + "): " + errorMessage);
            } else {
                System.err.println("Error: " + errorMessage);
            }
        } else {
            System.err.println("Error: operation failed");
        }
    }

    /**
     * Prints an error message.
     *
     * @param message the error message
     */
    public static void printError(String message) {
        System.err.println("Error: " + message);
    }

    /**
     * Prints an informational message.
     *
     * @param message the message
     */
    public static void printInfo(String message) {
        System.out.println(message);
    }
}
