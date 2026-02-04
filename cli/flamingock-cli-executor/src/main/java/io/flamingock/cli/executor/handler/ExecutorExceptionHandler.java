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
package io.flamingock.cli.executor.handler;

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

import java.io.PrintWriter;

/**
 * CLI exception handler that provides helpful guidance for common errors.
 */
public class ExecutorExceptionHandler implements IExecutionExceptionHandler {

    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult) {
        PrintWriter err = commandLine.getErr();

        err.println();
        err.println(SEPARATOR);
        err.println("Error: " + ex.getMessage());
        err.println(SEPARATOR);

        // Show stack trace in verbose mode
        if (isVerboseMode(parseResult)) {
            err.println();
            err.println("Stack trace:");
            ex.printStackTrace(err);
        } else {
            err.println();
            err.println("For detailed error information, run with --verbose flag");
        }

        return 1;
    }

    private boolean isVerboseMode(ParseResult parseResult) {
        if (parseResult == null) {
            return false;
        }
        // Check if --verbose was specified
        return parseResult.hasMatchedOption("--verbose");
    }
}
