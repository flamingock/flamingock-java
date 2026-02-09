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

import io.flamingock.internal.common.core.operation.OperationType;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Launches a JVM process to execute an application with Flamingock CLI mode enabled.
 *
 * <p>This class handles:</p>
 * <ul>
 *   <li>Detecting JAR type (Spring Boot vs plain uber JAR)</li>
 *   <li>Building the java command with appropriate flags</li>
 *   <li>Starting the process via ProcessBuilder</li>
 *   <li>Streaming stdout/stderr in real-time (when enabled)</li>
 *   <li>Returning structured launch results</li>
 * </ul>
 */
public class JvmLauncher {

    /**
     * The fully qualified name of the Flamingock CLI entry point for non-Spring Boot JARs.
     */
    static final String FLAMINGOCK_CLI_ENTRY_POINT = "io.flamingock.core.cli.FlamingockCliMainEntryPoint";

    private final JarTypeDetector jarTypeDetector;

    /**
     * Creates a new JvmLauncher with the default JarTypeDetector.
     */
    public JvmLauncher() {
        this(new JarTypeDetector());
    }

    /**
     * Creates a new JvmLauncher with the specified JarTypeDetector.
     *
     * @param jarTypeDetector the detector to use for JAR type detection
     */
    public JvmLauncher(JarTypeDetector jarTypeDetector) {
        this.jarTypeDetector = jarTypeDetector;
    }

    /**
     * Launches the application with Flamingock CLI mode enabled.
     * Uses the default EXECUTE operation.
     *
     * @param jarPath absolute path to the application JAR
     * @return the launch result
     */
    public LaunchResult launch(String jarPath) {
        return launch(jarPath, null, null, null, true, Collections.emptyMap());
    }

    /**
     * Launches the application with Flamingock CLI mode enabled, a specific operation,
     * an optional output file for result communication, optional log level, and output streaming control.
     *
     * @param jarPath       absolute path to the application JAR
     * @param operation     the Flamingock operation to execute, or null for default
     * @param outputFile    path to the output file for result communication, or null if not needed
     * @param logLevel      the application log level (debug, info, warn, error), or null for app default
     * @param streamOutput  whether to stream stdout/stderr to console (false = consume silently)
     * @param operationArgs additional operation-specific arguments to pass
     * @return the launch result
     */
    public LaunchResult launch(String jarPath, OperationType operation, String outputFile, String logLevel, boolean streamOutput, Map<String, String> operationArgs) {
        String operationName = operation != null ? operation.name() : null;
        List<String> command;
        JarType jarType;

        try {
            jarType = jarTypeDetector.detect(jarPath);
        } catch (JarDetectionException e) {
            return LaunchResult.jarAnalysisFailed(e.getMessage());
        }

        // Early detection: JAR is missing Flamingock runtime
        if (jarType == JarType.MISSING_FLAMINGOCK_RUNTIME) {
            return LaunchResult.missingFlamingockRuntime();
        }

        command = buildCommand(jarPath, operationName, outputFile, logLevel, jarType, operationArgs);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(jarPath).getParentFile());
        processBuilder.redirectErrorStream(false);

        try {
            Process process = processBuilder.start();

            Thread stdoutThread;
            Thread stderrThread;
            StringBuilder stderrCapture = new StringBuilder();

            if (streamOutput) {
                // Stream stdout and stderr in parallel
                stdoutThread = streamOutput(process.getInputStream(), System.out);
                stderrThread = streamAndCaptureOutput(process.getErrorStream(), System.err, stderrCapture);
            } else {
                // Consume streams silently to prevent blocking, but still capture stderr
                stdoutThread = consumeSilently(process.getInputStream());
                stderrThread = captureOutput(process.getErrorStream(), stderrCapture);
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();

            // Wait for output streaming to complete
            stdoutThread.join();
            stderrThread.join();

            // Check for entry point not found error in non-Spring Boot path
            if (exitCode != 0 && jarType == JarType.PLAIN_UBER) {
                String stderr = stderrCapture.toString();
                if (isEntryPointNotFoundError(stderr)) {
                    return LaunchResult.entryPointNotFound(exitCode);
                }
            }

            if (exitCode == 0) {
                return LaunchResult.success();
            } else {
                return LaunchResult.processFailed(exitCode);
            }

        } catch (IOException e) {
            return LaunchResult.processStartFailed(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LaunchResult.processInterrupted();
        }
    }

    /**
     * Checks if the stderr output indicates the entry point class was not found.
     *
     * @param stderr the captured stderr output
     * @return true if entry point was not found
     */
    private boolean isEntryPointNotFoundError(String stderr) {
        return stderr.contains("Could not find or load main class") &&
                stderr.contains(FLAMINGOCK_CLI_ENTRY_POINT);
    }

    /**
     * Builds the command line for launching the application.
     * Detects the JAR type and routes to the appropriate command builder.
     *
     * @param jarPath    path to the JAR file
     * @param operation  the Flamingock operation to execute, or null for default
     * @param outputFile path to the output file for result communication, or null if not needed
     * @param logLevel   the application log level (debug, info, warn, error), or null for app default
     * @return the command as a list of strings
     * @throws JarDetectionException if the JAR type cannot be determined
     */
    List<String> buildCommand(String jarPath, String operation, String outputFile, String logLevel)
            throws JarDetectionException {
        JarType jarType = jarTypeDetector.detect(jarPath);
        return buildCommand(jarPath, operation, outputFile, logLevel, jarType, Collections.emptyMap());
    }

    /**
     * Builds the command line for launching the application with the specified JAR type.
     *
     * @param jarPath    path to the JAR file
     * @param operation  the Flamingock operation to execute, or null for default
     * @param outputFile path to the output file for result communication, or null if not needed
     * @param logLevel   the application log level (debug, info, warn, error), or null for app default
     * @param jarType    the type of JAR
     * @return the command as a list of strings
     */
    List<String> buildCommand(String jarPath, String operation, String outputFile, String logLevel, JarType jarType) {
        return buildCommand(jarPath, operation, outputFile, logLevel, jarType, Collections.emptyMap());
    }

    /**
     * Builds the command line for launching the application with the specified JAR type.
     *
     * @param jarPath       path to the JAR file
     * @param operation     the Flamingock operation to execute, or null for default
     * @param outputFile    path to the output file for result communication, or null if not needed
     * @param logLevel      the application log level (debug, info, warn, error), or null for app default
     * @param jarType       the type of JAR
     * @param operationArgs additional operation-specific arguments
     * @return the command as a list of strings
     */
    List<String> buildCommand(String jarPath, String operation, String outputFile, String logLevel, JarType jarType, Map<String, String> operationArgs) {
        if (jarType == JarType.SPRING_BOOT) {
            return buildSpringBootCommand(jarPath, operation, outputFile, logLevel, operationArgs);
        } else {
            return buildPlainUberCommand(jarPath, operation, outputFile, logLevel, operationArgs);
        }
    }

    /**
     * Builds the command line for launching a Spring Boot application.
     *
     * @param jarPath    path to the JAR file
     * @param operation  the Flamingock operation to execute, or null for default
     * @param outputFile path to the output file for result communication, or null if not needed
     * @param logLevel   the application log level (debug, info, warn, error), or null for app default
     * @return the command as a list of strings
     */
    List<String> buildSpringBootCommand(String jarPath, String operation, String outputFile, String logLevel) {
        return buildSpringBootCommand(jarPath, operation, outputFile, logLevel, Collections.emptyMap());
    }

    /**
     * Builds the command line for launching a Spring Boot application.
     *
     * @param jarPath       path to the JAR file
     * @param operation     the Flamingock operation to execute, or null for default
     * @param outputFile    path to the output file for result communication, or null if not needed
     * @param logLevel      the application log level (debug, info, warn, error), or null for app default
     * @param operationArgs additional operation-specific arguments
     * @return the command as a list of strings
     */
    List<String> buildSpringBootCommand(String jarPath, String operation, String outputFile, String logLevel, Map<String, String> operationArgs) {
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

        // Add log level if specified (Spring Boot style)
        if (logLevel != null && !logLevel.isEmpty()) {
            command.add("--logging.level.root=" + logLevel.toUpperCase());
        }

        // Add operation-specific arguments
        if (operationArgs != null) {
            for (Map.Entry<String, String> entry : operationArgs.entrySet()) {
                command.add("--" + entry.getKey() + "=" + entry.getValue());
            }
        }

        return command;
    }

    /**
     * Builds the command line for launching a plain uber JAR application.
     *
     * @param jarPath    path to the JAR file
     * @param operation  the Flamingock operation to execute, or null for default
     * @param outputFile path to the output file for result communication, or null if not needed
     * @param logLevel   the application log level (debug, info, warn, error), or null for app default
     * @return the command as a list of strings
     */
    List<String> buildPlainUberCommand(String jarPath, String operation, String outputFile, String logLevel) {
        return buildPlainUberCommand(jarPath, operation, outputFile, logLevel, Collections.emptyMap());
    }

    /**
     * Builds the command line for launching a plain uber JAR application.
     *
     * @param jarPath       path to the JAR file
     * @param operation     the Flamingock operation to execute, or null for default
     * @param outputFile    path to the output file for result communication, or null if not needed
     * @param logLevel      the application log level (debug, info, warn, error), or null for app default
     * @param operationArgs additional operation-specific arguments
     * @return the command as a list of strings
     */
    List<String> buildPlainUberCommand(String jarPath, String operation, String outputFile, String logLevel, Map<String, String> operationArgs) {
        List<String> command = new ArrayList<>();

        // Find the java executable
        command.add(getJavaExecutable());

        // Use classpath instead of -jar
        command.add("-cp");
        command.add(jarPath);

        // Specify the Flamingock CLI entry point
        command.add(FLAMINGOCK_CLI_ENTRY_POINT);

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

        // Add log level if specified (Flamingock-specific style)
        if (logLevel != null && !logLevel.isEmpty()) {
            command.add("--flamingock.log.level=" + logLevel.toUpperCase());
        }

        // Add operation-specific arguments
        if (operationArgs != null) {
            for (Map.Entry<String, String> entry : operationArgs.entrySet()) {
                command.add("--" + entry.getKey() + "=" + entry.getValue());
            }
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
     * Creates a thread that reads from the input stream, writes to the output stream,
     * and captures the content to a StringBuilder.
     *
     * @param inputStream  the stream to read from
     * @param outputStream the stream to write to
     * @param capture      the StringBuilder to capture output
     * @return the thread (already started)
     */
    private Thread streamAndCaptureOutput(InputStream inputStream, java.io.PrintStream outputStream,
                                           StringBuilder capture) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputStream.println(line);
                    synchronized (capture) {
                        capture.append(line).append("\n");
                    }
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
     * Creates a thread that captures the input stream to a StringBuilder without printing.
     *
     * @param inputStream the stream to read from
     * @param capture     the StringBuilder to capture output
     * @return the thread (already started)
     */
    private Thread captureOutput(InputStream inputStream, StringBuilder capture) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (capture) {
                        capture.append(line).append("\n");
                    }
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
