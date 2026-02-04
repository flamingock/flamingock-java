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

import java.io.PrintStream;

/**
 * Provides formatted console output for the CLI executor.
 */
public final class ConsoleFormatter {

    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    private ConsoleFormatter() {
    }

    /**
     * Prints the CLI header with version information.
     *
     * @param version the CLI version
     */
    public static void printHeader(String version) {
        PrintStream out = System.out;
        out.println();
        out.println(SEPARATOR);
        out.println("Flamingock CLI v" + version);
        out.println(SEPARATOR);
        out.println();
    }

    /**
     * Prints a success message after successful execution.
     */
    public static void printSuccess() {
        PrintStream out = System.out;
        out.println();
        out.println(SEPARATOR);
        out.println("SUCCESS: Flamingock execution completed successfully");
        out.println(SEPARATOR);
    }

    /**
     * Prints a failure message after failed execution.
     */
    public static void printFailure() {
        PrintStream err = System.err;
        err.println();
        err.println(SEPARATOR);
        err.println("FAILED: Flamingock execution failed");
        err.println(SEPARATOR);
    }

    /**
     * Prints an error message.
     *
     * @param message the error message
     */
    public static void printError(String message) {
        PrintStream err = System.err;
        err.println();
        err.println(SEPARATOR);
        err.println("Error: " + message);
        err.println(SEPARATOR);
    }

    /**
     * Prints an informational message.
     *
     * @param message the message
     */
    public static void printInfo(String message) {
        System.out.println(message);
    }

    /**
     * Prints a verbose message (only shown in verbose mode).
     *
     * @param message the message
     * @param verbose whether verbose mode is enabled
     */
    public static void printVerbose(String message, boolean verbose) {
        if (verbose) {
            System.out.println("[VERBOSE] " + message);
        }
    }
}
