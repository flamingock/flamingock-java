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

import io.flamingock.cli.executor.output.ConsoleFormatter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches a JVM process to execute an application with Flamingock CLI mode enabled.
 *
 * <p>This class handles:</p>
 * <ul>
 *   <li>Building the java command with appropriate flags</li>
 *   <li>Starting the process via ProcessBuilder</li>
 *   <li>Streaming stdout/stderr in real-time (when enabled)</li>
 *   <li>Returning the process exit code</li>
 * </ul>
 */
public class JvmLauncher {

    /**
     * Creates a new JvmLauncher.
     */
    public JvmLauncher() {
    }

    /**
     * Launches the application with Flamingock CLI mode enabled.
     * Uses the default EXECUTE operation.
     *
     * @param jarPath absolute path to the application JAR
     * @return the process exit code (0 = success, non-zero = failure)
     */
    public int launch(String jarPath) {
        return launch(jarPath, null, null, null, true);
    }

    /**
     * Launches the application with Flamingock CLI mode enabled, a specific operation,
     * an optional output file for result communication, optional log level, and output streaming control.
     *
     * @param jarPath      absolute path to the application JAR
     * @param operation    the Flamingock operation to execute (e.g., "EXECUTE", "LIST"), or null for default
     * @param outputFile   path to the output file for result communication, or null if not needed
     * @param logLevel     the application log level (debug, info, warn, error), or null for app default
     * @param streamOutput whether to stream stdout/stderr to console (false = consume silently)
     * @return the process exit code (0 = success, non-zero = failure)
     */
    public int launch(String jarPath, String operation, String outputFile, String logLevel, boolean streamOutput) {
        List<String> command = buildCommand(jarPath, operation, outputFile, logLevel);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(jarPath).getParentFile());
        processBuilder.redirectErrorStream(false);

        try {
            Process process = processBuilder.start();

            Thread stdoutThread;
            Thread stderrThread;

            if (streamOutput) {
                // Stream stdout and stderr in parallel
                stdoutThread = streamOutput(process.getInputStream(), System.out);
                stderrThread = streamOutput(process.getErrorStream(), System.err);
            } else {
                // Consume streams silently to prevent blocking
                stdoutThread = consumeSilently(process.getInputStream());
                stderrThread = consumeSilently(process.getErrorStream());
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();

            // Wait for output streaming to complete
            stdoutThread.join();
            stderrThread.join();

            return exitCode;

        } catch (IOException e) {
            ConsoleFormatter.printError("Failed to start process: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ConsoleFormatter.printError("Process was interrupted");
            return 1;
        }
    }

    /**
     * Builds the command line for launching the application.
     *
     * @param jarPath    path to the JAR file
     * @param operation  the Flamingock operation to execute, or null for default
     * @param outputFile path to the output file for result communication, or null if not needed
     * @param logLevel   the application log level (debug, info, warn, error), or null for app default
     * @return the command as a list of strings
     */
    List<String> buildCommand(String jarPath, String operation, String outputFile, String logLevel) {
        List<String> command = new ArrayList<>();

        // Find the java executable
        command.add(getJavaExecutable());

        // Add the JAR
        command.add("-jar");
        command.add(jarPath);

        // Spring Boot flags to disable web server
        command.add("--spring.main.web-application-type=none");

        // Disable Spring Boot banner
        command.add("--spring.main.banner-mode=off");

        // Add the flamingock-cli profile
        command.add("--spring.profiles.include=flamingock-cli");

        // Enable Flamingock CLI mode
        command.add("--flamingock.cli.mode=true");

        // Add operation if specified
        if (operation != null && !operation.isEmpty()) {
            command.add("--flamingock.operation=" + operation);
        }

        // Add output file if specified
        if (outputFile != null && !outputFile.isEmpty()) {
            command.add("--flamingock.output-file=" + outputFile);
        }

        // Add log level if specified
        if (logLevel != null && !logLevel.isEmpty()) {
            command.add("--logging.level.root=" + logLevel.toUpperCase());
        }

        return command;
    }

    /**
     * Gets the path to the java executable.
     *
     * @return the java executable path
     */
    String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isEmpty()) {
            String separator = File.separator;
            String executable = javaHome + separator + "bin" + separator + "java";

            // On Windows, add .exe extension
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                executable += ".exe";
            }

            File javaFile = new File(executable);
            if (javaFile.exists()) {
                return executable;
            }
        }

        // Fall back to relying on PATH
        return "java";
    }

    /**
     * Creates a thread that reads from the input stream and writes to the output stream.
     *
     * @param inputStream  the stream to read from
     * @param outputStream the stream to write to
     * @return the thread (already started)
     */
    private Thread streamOutput(InputStream inputStream, java.io.PrintStream outputStream) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputStream.println(line);
                }
            } catch (IOException e) {
                // Stream closed, ignore
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Creates a thread that consumes the input stream silently to prevent blocking.
     *
     * @param inputStream the stream to consume
     * @return the thread (already started)
     */
    private Thread consumeSilently(InputStream inputStream) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                while (reader.readLine() != null) {
                    // Discard output
                }
            } catch (IOException e) {
                // Stream closed, ignore
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
