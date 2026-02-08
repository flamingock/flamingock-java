# Flamingock CLI Execution Feature
## Final Complete Plan v1.0

---

# PART I: OVERVIEW

## 1. Executive Summary

### 1.1 Objective
Create a professional CLI that enables executing Flamingock (apply, undo, dry-run, validate) outside the normal application lifecycle, with a user experience on par with tools like Docker, kubectl, or gh.

### 1.2 Target Audience
| Role           | Primary Need                                        |
|----------------|-----------------------------------------------------|
| DevOps         | Execute changes in CI/CD pipelines                  |
| Platform Eng.  | Control over when changes are applied in production |
| Administrators | Validate change status                              |
| Developers     | Dry-run and local testing                           |

### 1.3 Primary Use Cases
1. Execute changes before deployment (not during startup)
2. Validate that all changes are applied
3. Dry-run to preview what changes would be applied
4. Undo to revert changes
5. CI/CD pipeline integration (GitHub Actions, GitLab CI, Jenkins)

### 1.4 Supported Editions
- Community Edition ✓
- Cloud Edition ✓

---

# PART II: TECHNICAL ARCHITECTURE

## 2. Approach: Process-Based Execution

### 2.1 Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           FLAMINGOCK CLI                                 │
│                                                                          │
│  $ flamingock execute apply --jar ./app.jar                              │
│                                                                          │
│  ┌────────────────────┐         ┌──────────────────────────────────────┐ │
│  │   CLI Process      │         │   User's App (spawned JVM)           │ │
│  │   (Picocli-based)  │         │                                      │ │
│  │                    │ spawns  │   --spring.main.web-application-     │ │
│  │   - Parse args     │────────►│     type=none                        │ │
│  │   - Build command  │         │   --spring.profiles.include=         │ │
│  │   - Launch JVM     │         │     flamingock-cli                   │ │
│  │   - Capture result │◄────────│   --flamingock.cli.mode=true         │ │
│  │                    │  file   │   --flamingock.operation=apply   │ │
│  └────────────────────┘         │   --flamingock.output-file=...   │ │
│                                 └──────────────────────────────────────┘ │
│                                                                          │
│  Communication:                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ • Exit code: 0 (success) / 1 (failure) / 2 (usage error)            │ │
│  │ • stdout/stderr: real-time log streaming                            │ │
│  │ • JSON file: structured result                                      │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Approach Justification

| Alternative Considered                         | Reason for Rejection                                                       |
|------------------------------------------------|----------------------------------------------------------------------------|
| ClassLoader-based (load JAR in same JVM)       | ClassLoader complexity, version conflicts, reflection hell                 |
| Minimal context (load only necessary beans)    | Difficulty inferring dependencies, hard debugging, high maintenance        |
| Socket/Agent                                   | Overkill, unnecessary complexity, firewall issues                          |

**Advantages of Process-Based approach:**
- Total isolation between CLI and user's app
- Works with any Spring Boot version
- Simple to implement and maintain
- Easy debugging (it's a normal Java execution with flags)
- No dependency conflicts

### 2.3 Communication Flow

```
┌─────────────┐     spawn      ┌─────────────────┐
│   CLI       │───────────────►│   User's App    │
│             │                │                 │
│             │   stdout/err   │   Logs          │
│             │◄═══════════════│   (streaming)   │
│             │                │                 │
│             │   temp file    │   Result JSON   │
│             │◄───────────────│   (on exit)     │
│             │                │                 │
│             │   exit code    │   0 or 1        │
│             │◄───────────────│   (on exit)     │
└─────────────┘                └─────────────────┘
```

---

# PART III: CLI STRUCTURE

## 3. Modules

### 3.1 Directory Structure

```
cli/
├── flamingock-cli/                    # EXISTING - audit operations
│   ├── src/main/java/
│   │   └── io/flamingock/cli/
│   │       ├── FlamingockCli.java
│   │       ├── commands/
│   │       │   ├── AuditCommand.java
│   │       │   └── IssueCommand.java
│   │       └── ...
│   └── build.gradle.kts
│
└── flamingock-cli-executor/           # NEW - Flamingock execution
    ├── src/main/java/
    │   └── io/flamingock/cli/executor/
    │       ├── FlamingockExecutorCli.java       # Main entry point
    │       ├── commands/
    │       │   ├── ExecuteCommand.java          # Parent "execute" command
    │       │   ├── ApplyCommand.java            # "apply" subcommand
    │       │   ├── UndoCommand.java             # "undo" subcommand
    │       │   ├── DryRunCommand.java           # "dry-run" subcommand
    │       │   └── ValidateCommand.java         # "validate" subcommand
    │       ├── process/
    │       │   ├── JvmLauncher.java             # Process spawn and management
    │       │   ├── ExecutionResult.java         # Result model
    │       │   └── CliOperation.java            # Operations enum
    │       ├── output/
    │       │   ├── ConsoleFormatter.java        # Terminal output
    │       │   ├── JsonFormatter.java           # JSON output for CI/CD
    │       │   ├── Spinner.java                 # Progress indicator
    │       │   └── ErrorFormatter.java          # Error formatting
    │       ├── errors/
    │       │   ├── CliError.java                # Error model
    │       │   └── KnownErrors.java             # Known errors catalog
    │       ├── config/
    │       │   ├── CliConfig.java               # Global configuration
    │       │   └── EnvironmentDetector.java     # Environment detection
    │       └── util/
    │           └── VersionProvider.java         # Version provider
    ├── src/main/resources/
    │   └── META-INF/
    │       └── MANIFEST.MF
    └── build.gradle.kts
```

### 3.2 Technology: Picocli

**Why Picocli?**
- Already used in the existing CLI (`flamingock-cli`)
- Mature and well-maintained framework
- Native support for hierarchical subcommands
- Automatic professional help generation
- Professional colors and formatting out-of-the-box
- Shell completion (bash, zsh, fish)
- Declarative annotations

### 3.3 build.gradle.kts

```kotlin
plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.flamingock"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // CLI Framework
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.mockito:mockito-core:4.11.0")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "io.flamingock.cli.executor.FlamingockExecutorCli",
            "Implementation-Title" to "Flamingock CLI",
            "Implementation-Version" to project.version
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("flamingock-cli")
    archiveClassifier.set("uber")
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}
```

---

# PART IV: CLI IMPLEMENTATION

## 4. CLI Code

### 4.1 Main Entry Point

```java
package io.flamingock.cli.executor;

import io.flamingock.cli.executor.commands.ExecuteCommand;
import io.flamingock.cli.executor.config.EnvironmentDetector;
import io.flamingock.cli.executor.util.VersionProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "flamingock",
    description = "Flamingock CLI - Change-as-Code for distributed systems evolution",
    version = "1.0.0",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {
        ExecuteCommand.class,
        CommandLine.HelpCommand.class
    },
    footer = {
        "",
        "Documentation: https://docs.flamingock.io/cli",
        "Report issues: https://github.com/flamingock/flamingock/issues"
    }
)
public class FlamingockExecutorCli implements Runnable {

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output"
    )
    boolean verbose;

    @Option(
        names = {"--no-color"},
        description = "Disable colored output"
    )
    boolean noColor;

    @Option(
        names = {"--quiet", "-q"},
        description = "Suppress all output except errors and final result"
    )
    boolean quiet;

    public static void main(String[] args) {
        // Configure colors based on environment
        if (EnvironmentDetector.shouldUseColor()) {
            System.setProperty("picocli.ansi", "true");
        }

        int exitCode = new CommandLine(new FlamingockExecutorCli())
            .setExecutionExceptionHandler(new CliExceptionHandler())
            .setParameterExceptionHandler(new CliParameterExceptionHandler())
            .execute(args);

        System.exit(exitCode);
    }

    @Override
    public void run() {
        // No arguments, show help
        CommandLine.usage(this, System.out);
    }
}
```

### 4.2 Apply Command

```java
package io.flamingock.cli.executor.commands;

import io.flamingock.cli.executor.process.JvmLauncher;
import io.flamingock.cli.executor.process.ExecutionResult;
import io.flamingock.cli.executor.process.CliOperation;
import io.flamingock.cli.executor.process.LaunchOptions;
import io.flamingock.cli.executor.output.ConsoleFormatter;
import io.flamingock.cli.executor.output.Spinner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "apply",
    description = "Apply pending changes to the target system",
    footer = {
        "",
        "Examples:",
        "  flamingock execute apply --jar ./app.jar",
        "  flamingock execute apply --jar ./app.jar --output json",
        "  flamingock execute apply --jar ./app.jar --jvm-arg \"-Xmx512m\" --jvm-arg \"-Dkey=value\"",
        "  flamingock execute apply --jar ./app.jar -- --spring.config.additional-location=/etc/app/",
        "",
        "For production environments, we recommend using a dedicated changes module.",
        "See: https://docs.flamingock.io/cli/recommended-pattern"
    }
)
public class ApplyCommand implements Callable<Integer> {

    @ParentCommand
    private ExecuteCommand parent;

    @Option(
        names = {"--jar", "-j"},
        description = "Path to the application JAR file",
        required = true,
        paramLabel = "<path>"
    )
    private Path jarPath;

    @Option(
        names = {"--output", "-o"},
        description = "Output format: console, json (default: ${DEFAULT-VALUE})",
        defaultValue = "console",
        paramLabel = "<format>"
    )
    private String outputFormat;

    @Option(
        names = {"--jvm-arg"},
        description = "JVM arguments to pass (repeatable). Examples: -Xmx512m, -Dkey=value",
        paramLabel = "<arg>"
    )
    private List<String> jvmArgs = new ArrayList<>();

    @Option(
        names = {"--capture-logs"},
        description = "Capture logs for inclusion in error reports (default: auto-detect CI)",
        negatable = true
    )
    private Boolean captureLogs;

    @Parameters(
        description = "Additional arguments to pass to the application (after --)",
        hidden = true
    )
    private List<String> appArgs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        // Validate JAR exists
        if (!Files.exists(jarPath)) {
            ConsoleFormatter.printError("JAR file not found: " + jarPath);
            return 126;
        }

        ConsoleFormatter.printHeader("Flamingock CLI v1.0.0");

        Spinner spinner = new Spinner("Loading application...");
        spinner.start();

        try {
            LaunchOptions options = LaunchOptions.builder()
                .jvmArgs(jvmArgs)
                .appArgs(appArgs)
                .captureLogs(captureLogs != null ? captureLogs : EnvironmentDetector.isCI())
                .build();

            JvmLauncher launcher = new JvmLauncher();
            ExecutionResult result = launcher.launch(jarPath.toString(), CliOperation.APPLY, options);

            spinner.stop(result.isSuccess());

            if ("json".equals(outputFormat)) {
                System.out.println(result.toJson());
            } else {
                ConsoleFormatter.printResult(result);
            }

            return result.getExitCode();

        } catch (Exception e) {
            spinner.stop(false);
            throw e;
        }
    }
}
```

### 4.3 JvmLauncher (with Signal Handling)

```java
package io.flamingock.cli.executor.process;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class JvmLauncher {

    private static final int DEFAULT_LOG_TAIL_LINES = 50;

    public ExecutionResult launch(String jarPath, CliOperation operation, LaunchOptions options)
            throws Exception {

        // Create temp file for result
        Path outputFile = Files.createTempFile("flamingock-result-", ".json");

        try {
            List<String> command = buildCommand(jarPath, operation, outputFile, options);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Register for signal handling (Ctrl+C)
            ProcessSignalHandler.registerChildProcess(process);

            try {
                // Circular buffer for log capture
                CircularBuffer<String> logBuffer = options.isCaptureLogs()
                    ? new CircularBuffer<>(options.getLogTailLines())
                    : null;

                // Stream output in real-time
                streamOutput(process.getInputStream(), logBuffer);

                int exitCode = process.waitFor();

                // Check if we were interrupted
                if (ProcessSignalHandler.isShuttingDown()) {
                    return ExecutionResult.interrupted();
                }

                // Read result from file
                return parseResult(outputFile, exitCode, logBuffer);

            } finally {
                ProcessSignalHandler.clearChildProcess();
            }

        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private List<String> buildCommand(String jarPath, CliOperation operation,
                                       Path outputFile, LaunchOptions options) {
        List<String> command = new ArrayList<>();

        // Java executable
        command.add(ProcessHandle.current()
            .info()
            .command()
            .orElse("java"));

        // JVM args (user-provided)
        if (options.getJvmArgs() != null) {
            command.addAll(options.getJvmArgs());
        }

        // JAR
        command.add("-jar");
        command.add(jarPath);

        // Flamingock CLI flags
        command.add("--spring.main.web-application-type=none");
        command.add("--spring.profiles.include=flamingock-cli");
        command.add("--flamingock.cli.mode=true");
        command.add("--flamingock.operation=" + operation.name().toLowerCase());
        command.add("--flamingock.output-file=" + outputFile.toAbsolutePath());

        // App args (user-provided, after --)
        if (options.getAppArgs() != null) {
            command.addAll(options.getAppArgs());
        }

        return command;
    }

    private void streamOutput(InputStream inputStream, CircularBuffer<String> logBuffer)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (logBuffer != null) {
                    logBuffer.add(line);
                }
            }
        }
    }

    private ExecutionResult parseResult(Path outputFile, int exitCode,
                                        CircularBuffer<String> logBuffer) throws IOException {
        if (Files.exists(outputFile) && Files.size(outputFile) > 0) {
            String json = Files.readString(outputFile);
            return ExecutionResult.fromJson(json);
        }

        // No output file = bootstrap failure
        ExecutionResult result = ExecutionResult.bootstrapFailure();
        if (logBuffer != null) {
            result.setLogTail(logBuffer.getAll());
        }
        return result;
    }
}
```

### 4.4 ProcessSignalHandler

```java
package io.flamingock.cli.executor.process;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles Ctrl+C (SIGINT) by forwarding the signal to the child process.
 * Prevents orphan Java processes in CI/CD and terminal environments.
 */
public class ProcessSignalHandler {

    private static final AtomicReference<Process> CHILD_PROCESS = new AtomicReference<>();
    private static volatile boolean shuttingDown = false;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shuttingDown = true;
            Process child = CHILD_PROCESS.get();
            if (child != null && child.isAlive()) {
                // Graceful attempt first
                child.destroy();

                try {
                    // Wait for clean shutdown
                    boolean terminated = child.waitFor(3, TimeUnit.SECONDS);
                    if (!terminated) {
                        // Force kill if unresponsive
                        child.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    child.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
        }, "flamingock-signal-handler"));
    }

    public static void registerChildProcess(Process process) {
        CHILD_PROCESS.set(process);
    }

    public static void clearChildProcess() {
        CHILD_PROCESS.set(null);
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }
}
```

### 4.5 ExecutionResult

```java
package io.flamingock.cli.executor.process;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class ExecutionResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean success;
    private int exitCode;

    @JsonProperty("error_type")
    private String errorType;

    private String message;

    @JsonProperty("duration_ms")
    private long durationMs;

    private List<ChangeResult> changes = new ArrayList<>();
    private Summary summary;

    @JsonProperty("log_tail")
    private List<String> logTail;

    // Factory methods
    public static ExecutionResult bootstrapFailure() {
        ExecutionResult result = new ExecutionResult();
        result.success = false;
        result.exitCode = 1;
        result.errorType = "BOOTSTRAP_FAILURE";
        result.message = "Application failed to start";
        return result;
    }

    public static ExecutionResult interrupted() {
        ExecutionResult result = new ExecutionResult();
        result.success = false;
        result.exitCode = 130;
        result.errorType = "INTERRUPTED";
        result.message = "Operation interrupted by user";
        return result;
    }

    public static ExecutionResult fromJson(String json) throws Exception {
        return MAPPER.readValue(json, ExecutionResult.class);
    }

    public String toJson() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"Serialization failed\"}";
        }
    }

    public boolean isBootstrapFailure() {
        return "BOOTSTRAP_FAILURE".equals(errorType);
    }

    // Getters and setters
    public boolean isSuccess() { return success; }
    public int getExitCode() { return exitCode; }
    public String getErrorType() { return errorType; }
    public String getMessage() { return message; }
    public long getDurationMs() { return durationMs; }
    public List<ChangeResult> getChanges() { return changes; }
    public Summary getSummary() { return summary; }
    public List<String> getLogTail() { return logTail; }
    public void setLogTail(List<String> logTail) { this.logTail = logTail; }

    // Nested classes
    public static class ChangeResult {
        private String id;
        private String author;
        private String status;
        @JsonProperty("duration_ms")
        private long durationMs;
        private String error;

        // Getters
        public String getId() { return id; }
        public String getAuthor() { return author; }
        public String getStatus() { return status; }
        public long getDurationMs() { return durationMs; }
        public String getError() { return error; }
    }

    public static class Summary {
        private int total;
        private int applied;
        private int failed;
        private int skipped;
        private int pending;

        // Getters
        public int getTotal() { return total; }
        public int getApplied() { return applied; }
        public int getFailed() { return failed; }
        public int getSkipped() { return skipped; }
        public int getPending() { return pending; }
    }
}
```

---

# PART V: PRODUCTION OPERABILITY

## 5. Operability Features

These features are critical for production and CI/CD environments.

### 5.1 JVM Args Passthrough

Users need to pass JVM arguments like `-Xmx`, `-Xms`, SSL properties, etc.

**CLI Option:** `--jvm-arg` (repeatable)

```bash
# Memory configuration
flamingock execute apply --jar ./app.jar \
  --jvm-arg "-Xmx512m" \
  --jvm-arg "-Xms256m"

# SSL/TLS configuration
flamingock execute apply --jar ./app.jar \
  --jvm-arg "-Djavax.net.ssl.trustStore=/path/to/truststore.jks" \
  --jvm-arg "-Djavax.net.ssl.trustStorePassword=changeit"

# System properties
flamingock execute apply --jar ./app.jar \
  --jvm-arg "-Dspring.profiles.active=prod" \
  --jvm-arg "-Dlogging.level.root=DEBUG"
```

### 5.2 App Args Passthrough

Arguments after `--` separator are passed directly to the application.

```bash
# Override Spring configuration
flamingock execute apply --jar ./app.jar \
  -- --spring.config.additional-location=/etc/myapp/

# Multiple app arguments
flamingock execute apply --jar ./app.jar \
  -- --spring.datasource.url=jdbc:postgresql://prod-db:5432/myapp \
     --spring.datasource.password=secret
```

### 5.3 Log Capture for CI/CD

When bootstrap fails, there's no Flamingock result because the app never started. In CI/CD environments, this makes diagnosis difficult.

**Solution:** Capture the last N lines of output and include them in the result.

**CLI Option:** `--capture-logs` / `--no-capture-logs` (default: auto-detect CI)

```bash
# Force enable
flamingock execute apply --jar ./app.jar --capture-logs

# Force disable
flamingock execute apply --jar ./app.jar --no-capture-logs
```

**JSON Result with log_tail:**

```json
{
  "success": false,
  "error_type": "BOOTSTRAP_FAILURE",
  "message": "Application failed to start",
  "log_tail": [
    "2024-01-15 10:23:45.123 ERROR --- [main] o.s.boot.SpringApplication: Application run failed",
    "org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'dataSource'",
    "Caused by: com.zaxxer.hikari.pool.HikariPool$PoolInitializationException: Failed to initialize pool",
    "Caused by: java.sql.SQLException: Cannot create PoolableConnectionFactory (Connection refused)"
  ]
}
```

### 5.4 Configurable Exit Delay

The delay before `System.exit()` is configurable for special cases (Windows, some CI runners, etc.).

**Property:** `flamingock.cli.exit-delay-ms` (default: `50`)

```bash
# If logs are being truncated, increase the delay
flamingock execute apply --jar ./app.jar \
  -- --flamingock.cli.exit-delay-ms=100
```

### 5.5 Signal Handling (Ctrl+C)

Common problem in CLI tools: user presses Ctrl+C and the parent process dies but the child stays alive (or vice versa).

**Implemented Solution:**

```java
package io.flamingock.cli.executor.process;

import java.util.concurrent.atomic.AtomicReference;

public class ProcessSignalHandler {

    private static final AtomicReference<Process> CHILD_PROCESS = new AtomicReference<>();
    private static volatile boolean shuttingDown = false;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shuttingDown = true;
            Process child = CHILD_PROCESS.get();
            if (child != null && child.isAlive()) {
                // Graceful attempt first
                child.destroy();

                try {
                    // Wait for clean shutdown
                    boolean terminated = child.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                    if (!terminated) {
                        // Force kill if unresponsive
                        child.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    child.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
        }, "flamingock-signal-handler"));
    }

    public static void registerChildProcess(Process process) {
        CHILD_PROCESS.set(process);
    }

    public static void clearChildProcess() {
        CHILD_PROCESS.set(null);
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }
}
```

**Behavior:**

| Event                | Action                                      |
|----------------------|---------------------------------------------|
| User presses Ctrl+C  | Shutdown hook activates                     |
| Child process exists | `destroy()` → wait 3s → `destroyForcibly()` |
| Exit code            | 130 (Unix standard for SIGINT)              |

**Important:** This handling is critical for CI/CD where pipelines can be cancelled and we don't want orphan Java processes consuming resources.

---

# PART VI: SPRING BOOT IMPLEMENTATION

## 6. Code in `flamingock-springboot-integration`

### 6.1 FlamingockCliModeAutoConfiguration

```java
package io.flamingock.springboot.cli;

import io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Auto-configuration for Flamingock CLI mode.
 * Only activates when flamingock.cli.mode=true
 */
@Configuration
@ConditionalOnProperty(name = "flamingock.cli.mode", havingValue = "true")
public class FlamingockCliModeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlamingockCliModeAutoConfiguration.class);

    @Bean
    public CommandLineRunner flamingockCliExecutor(
            ConfigurableApplicationContext context,
            AbstractChangeRunnerBuilder<?, ?> builder,
            @Value("${flamingock.operation}") String operation,
            @Value("${flamingock.output-file}") String outputFile,
            @Value("${flamingock.cli.count:1}") int undoCount) {

        return args -> {
            log.info("Flamingock CLI mode activated");
            log.info("Operation: {}", operation);

            CliResult result;
            int exitCode;

            try {
                CliOperationExecutor executor = new CliOperationExecutor(builder);
                CliOperationParams params = new CliOperationParams(undoCount);

                result = executor.execute(
                    CliOperation.valueOf(operation.toUpperCase().replace("-", "_")),
                    params
                );
                exitCode = result.isSuccess() ? 0 : 1;

            } catch (Exception e) {
                log.error("Flamingock CLI operation failed", e);
                result = CliResult.error(e);
                exitCode = 1;
            }

            // Write result to file
            Files.writeString(Path.of(outputFile), result.toJson());
            log.info("Result written to {}", outputFile);

            // Store result for exit hook
            FlamingockCliResultHolder.set(exitCode, result);

            // Close context (triggers exit hook)
            context.close();
        };
    }
}
```

### 6.2 FlamingockCliExitHook

```java
package io.flamingock.springboot.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exit hook that terminates the process when context closes in CLI mode.
 * System.exit() is here (at the process edge), not in business logic.
 *
 * The micro-delay is configurable for special cases (Windows, CI, etc.)
 * Property: flamingock.cli.exit-delay-ms (default: 50)
 */
@Component
@ConditionalOnProperty(name = "flamingock.cli.mode", havingValue = "true")
public class FlamingockCliExitHook implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(FlamingockCliExitHook.class);
    private static final AtomicBoolean exitCalled = new AtomicBoolean(false);

    @Value("${flamingock.cli.exit-delay-ms:50}")
    private int exitDelayMs;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        // Avoid multiple executions
        if (!exitCalled.compareAndSet(false, true)) {
            return;
        }

        int exitCode = FlamingockCliResultHolder.getExitCode();
        log.debug("CLI exit hook triggered with exit code: {}", exitCode);

        // Flush streams before exit
        System.out.flush();
        System.err.flush();

        // Configurable micro-delay for clean shutdown
        // (log flush, @PreDestroy, DisposableBean, etc.)
        new Thread(() -> {
            try {
                if (exitDelayMs > 0) {
                    Thread.sleep(exitDelayMs);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            // Second flush just in case
            System.out.flush();
            System.err.flush();

            System.exit(exitCode);
        }, "flamingock-cli-exit").start();
    }
}
```

### 6.3 @ExcludeFromCliMode Annotation

```java
package io.flamingock.springboot.cli;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a component to NOT be created in CLI mode.
 * Equivalent to @ConditionalOnProperty(name="flamingock.cli.mode", havingValue="false", matchIfMissing=true)
 *
 * Usage:
 * <pre>
 * {@code
 * @Component
 * @ExcludeFromCliMode
 * public class MyScheduledTasks {
 *     @Scheduled(fixedRate = 5000)
 *     public void process() { ... }
 * }
 * }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(
    name = "flamingock.cli.mode",
    havingValue = "false",
    matchIfMissing = true
)
public @interface ExcludeFromCliMode {
}
```

---

# PART VII: USER CONTRACT

## 7. Guarantees and Responsibilities

### 7.1 What Flamingock CLI GUARANTEES

| Guarantee               | Description                                  | How                                        |
|-------------------------|----------------------------------------------|--------------------------------------------|
| **Isolated process**    | CLI and user's app run in separate JVMs      | `java -jar`                                |
| **Web server disabled** | No HTTP server starts                        | `--spring.main.web-application-type=none`  |
| **CLI mode activated**  | Property available for conditionals          | `--flamingock.cli.mode=true`               |
| **CLI profile added**   | Profile available without overwriting others | `--spring.profiles.include=flamingock-cli` |
| **Operation executed**  | Requested operation is executed              | apply / undo / dry-run / validate          |
| **Correct exit code**   | Standard exit code                           | 0 = success, 1 = failure, 2 = usage error  |
| **JSON result**         | Always available, even if bootstrap fails    | Temp file                                  |
| **Clean shutdown**      | Context closed, shutdown hooks executed      | `context.close()` + delay                  |
| **Signal forwarding**   | Ctrl+C properly terminates child process     | Shutdown hook + destroy                    |

### 7.2 What Flamingock CLI DOES NOT GUARANTEE (user responsibility)

| Aspect                              | Description                                              | Recommended Solution                                 |
|-------------------------------------|----------------------------------------------------------|------------------------------------------------------|
| **Other runners don't execute**     | External ApplicationRunner/CommandLineRunner may execute | Dedicated module or `@ExcludeFromCliMode`            |
| **Scheduling disabled**             | `@Scheduled` tasks may execute                           | `@Profile("!flamingock-cli")` on `@EnableScheduling` |
| **Listeners disabled**              | Kafka/RabbitMQ listeners may connect                     | `auto-startup: false` in profile                     |
| **`@PostConstruct` / constructors** | Execute if bean is created                               | Dedicated module or conditional bean                 |
| **Initialization logic**            | Code in constructors/InitializingBean                    | Dedicated module                                     |

### 7.3 Important Notes

> **Profile include vs active**: Flamingock adds the `flamingock-cli` profile using `spring.profiles.include`, meaning it ADDS to existing profiles, doesn't replace them.

> **Transparency**: When you run your application via Flamingock CLI, the Spring context loads normally but with web server disabled. Your beans will initialize. If you have components with side-effects, you must condition them or use the dedicated module pattern.

---

# PART VIII: CONFIGURATION PATTERNS

## 8. Recommendation Hierarchy

### 8.1 Level 1: Dedicated Module (★★★) - THE PATH FOR PRODUCTION

**This is THE recommended pattern for production. It's not "an option" - it's THE recommendation.**

```
my-application/
├── my-app-core/              # Business logic
│   └── src/main/java/
│       └── com/myapp/
│           ├── service/
│           └── repository/
│
├── my-app-web/               # REST API, controllers
│   └── src/main/java/
│       └── com/myapp/
│           ├── controller/
│           └── config/
│
└── my-app-changes/           ← DEDICATED FLAMINGOCK MODULE
    ├── src/main/java/
    │   └── com/myapp/changes/
    │       ├── ChangesApplication.java
    │       └── changes/
    │           ├── V001_CreateUsersTable.java
    │           ├── V002_AddEmailIndex.java
    │           └── V003_SeedAdminUser.java
    ├── src/main/resources/
    │   ├── application.yml   # Only: DB + Flamingock config
    │   └── flamingock/
    │       └── pipeline.yaml
    └── build.gradle
```

**Benefits:**

| Benefit                        | Description                            |
|--------------------------------|----------------------------------------|
| ✅ **Clean context**            | Only beans necessary for changes       |
| ✅ **No side-effects**          | Kafka, schedulers, runners don't exist |
| ✅ **Performance**              | Fast startup (~1-2s vs ~10-30s)        |
| ✅ **Clear ownership**          | DevOps/DBAs manage the module          |
| ✅ **Independent testing**      | Changes testable in isolation          |
| ✅ **Simple CI/CD**             | One specific JAR for changes           |
| ✅ **Single guaranteed pattern**| No additional configuration required   |

**Usage:**

```bash
flamingock execute apply --jar ./my-app-changes/build/libs/my-app-changes.jar
```

### 8.2 Level 2: Unified Application with Profile (★★☆)

**For applications where a separate module is not viable.**

**User is responsible for conditioning their beans.**

Flamingock provides:
- `flamingock.cli.mode=true` (property)
- `flamingock-cli` (profile)
- `@ExcludeFromCliMode` (annotation)

**User configuration:**

```java
// Condition scheduling
@Configuration
@Profile("!flamingock-cli")
@EnableScheduling
public class SchedulingConfig {
}

// Condition runners
@Component
@ExcludeFromCliMode
public class DataInitializer implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        // Does not execute in CLI mode
    }
}

// Condition listeners
@Component
@Profile("!flamingock-cli")
public class OrderEventListener {
    @KafkaListener(topics = "orders")
    public void handle(OrderEvent event) { }
}
```

---

# PART IX: USER EXPERIENCE

## 9. Output Examples

### 9.1 Successful Execution

```bash
$ flamingock execute apply --jar ./my-app-changes.jar

Flamingock CLI v1.0.0
────────────────────────────────────────

  ✓ Loading application... done (1.2s)
  ✓ Flamingock configuration detected
  ✓ Connected to MongoDB (localhost:27017)

Executing changes:
  ├─ [1/3] V001_CreateUsersTable ✓ (234ms)
  │        Author: john.doe
  │
  ├─ [2/3] V002_AddEmailIndex ✓ (89ms)
  │        Author: jane.smith
  │
  └─ [3/3] V003_SeedAdminUser ✓ (12ms)
           Author: john.doe

────────────────────────────────────────
SUCCESS │ 3 applied │ 1.5s

$ echo $?
0
```

### 9.2 Execution with Error

```bash
$ flamingock execute apply --jar ./my-app.jar

Flamingock CLI v1.0.0
────────────────────────────────────────

  ✓ Loading application... done (2.1s)
  ✓ Flamingock configuration detected

Executing changes:
  ├─ [1/2] V001_CreateUsersTable ✓ (234ms)
  │        Author: john.doe
  │
  └─ [2/2] V002_MigrateUserData ✗ (1.2s)
           Author: jane.smith
           Error: NullPointerException at MigrateUserData.java:45

────────────────────────────────────────
FAILED │ 1 applied, 1 failed │ 1.8s

The change V002_MigrateUserData has been marked for MANUAL_INTERVENTION.
After fixing the issue, run:
  flamingock audit fix V002_MigrateUserData

$ echo $?
1
```

### 9.3 Bootstrap Failure

```bash
$ flamingock execute apply --jar ./broken-app.jar

Flamingock CLI v1.0.0
────────────────────────────────────────

  ✗ Loading application... failed

✗ Error: Application failed to start

  Possible causes:
    • Missing configuration
    • Bean initialization error
    • External system connection failed

  To fix:
    1. Check application logs above for details
    2. Verify application.yml configuration
    3. Try running the JAR directly: java -jar ./broken-app.jar

  Documentation: https://docs.flamingock.io/troubleshooting/bootstrap

────────────────────────────────────────
BOOTSTRAP_FAILURE │ Check application logs

$ echo $?
1
```

### 9.4 JSON Output (for CI/CD)

```bash
$ flamingock execute apply --jar ./my-app.jar --output json

{
  "success": true,
  "duration_ms": 1523,
  "changes": [
    {
      "id": "V001_CreateUsersTable",
      "author": "john.doe",
      "status": "APPLIED",
      "duration_ms": 234
    },
    {
      "id": "V002_AddEmailIndex",
      "author": "jane.smith",
      "status": "APPLIED",
      "duration_ms": 89
    }
  ],
  "summary": {
    "total": 2,
    "applied": 2,
    "failed": 0,
    "skipped": 0,
    "pending": 0
  }
}
```

### 9.5 Interrupted (Ctrl+C)

```bash
$ flamingock execute apply --jar ./my-app.jar
^C
────────────────────────────────────────
INTERRUPTED │ Operation cancelled by user

$ echo $?
130
```

---

# PART X: TECHNICAL SUMMARY

## 10. Technical Decisions

| Aspect                              | Decision                                          | Justification                              |
|-------------------------------------|---------------------------------------------------|--------------------------------------------|
| **Architecture**                    | Process-based (spawn JVM)                         | Isolation, simplicity, compatibility       |
| **CLI Framework**                   | Picocli 4.7.5                                     | Already used, mature, professional         |
| **Exit code**                       | Holder + System.exit with configurable delay      | Enables clean shutdown                     |
| **Exit delay**                      | Configurable via `flamingock.cli.exit-delay-ms`   | Escape valve for special cases             |
| **Stream flush**                    | Explicit flush before exit                        | Ensures logs are written                   |
| **No output file**                  | Synthetic JSON BOOTSTRAP_FAILURE + log tail       | Enterprise traceability                    |
| **Profiles**                        | `include` not `active`                            | Doesn't overwrite user config              |
| **Automatic bean removal**          | NO                                                | Invasive, user responsibility              |
| **Dedicated module**                | Main recommendation                               | Only guaranteed pattern                    |
| **CLI↔App communication**           | Temporary JSON file                               | Simple, robust, universal                  |
| **JVM args passthrough**            | `--jvm-arg` repeatable                            | Needed for production (Xmx, SSL, etc.)     |
| **App args passthrough**            | Standard `--` separator                           | Flexibility without ambiguity              |
| **Log capture for CI**              | `--capture-logs` (auto in CI)                     | Diagnosis in bootstrap failures            |
| **Signal handling (Ctrl+C)**        | Shutdown hook + child process destroy             | Prevents zombie processes in CI/terminal   |

---

# PART XI: EXIT CODES

| Code | Meaning            | Example                         |
|------|--------------------|---------------------------------|
| 0    | Success            | Changes applied successfully    |
| 1    | Execution error    | A change failed                 |
| 2    | Usage error        | Invalid arguments               |
| 126  | Not executable     | JAR not found                   |
| 130  | Interrupted        | Ctrl+C                          |

---

# PART XII: IMPLEMENTATION ROADMAP

## 12. Milestones and Estimates

### 12.1 Overview

| Milestone | Scope                | Estimated Effort | Notes                                      |
|-----------|----------------------|------------------|--------------------------------------------|
| **MVP**   | Core CLI + Apply     | 0.5-1 day        | Code already in plan, Apply exists in core |
| **M1**    | Full Operations      | 0.5-1 day        | Same pattern as Apply                      |
| **M2**    | Production Hardening | 0.5-1 day        | Code already in plan                       |
| **M3**    | Documentation        | 0.5 day          | Generated from plan + implementation       |
| **Total** | Complete Feature     | **2-3.5 days**   | With Claude Code                           |

> **Note:** These estimates assume implementation with Claude Code based on the already-defined plan, where ~90% of the code is already written. The CLI framework is simply wiring together existing pieces.

### 12.2 MVP - Minimum Viable Product

**Goal:** A working CLI that can execute `apply` on a Spring Boot JAR.

**Deliverables:**
- [ ] `flamingock-cli-executor` module structure
- [ ] `FlamingockExecutorCli` entry point with `--help`, `--version`
- [ ] `ExecuteCommand` parent command
- [ ] `ApplyCommand` (only operation in MVP)
- [ ] `JvmLauncher` (process spawn with flags)
- [ ] `ExecutionResult` model
- [ ] `FlamingockCliModeAutoConfiguration` (Spring Boot side)
- [ ] `FlamingockCliResultHolder` + `FlamingockCliExitHook`
- [ ] Basic console output
- [ ] Exit codes (0, 1, 2)
- [ ] Framework ready to add more operations (undo, dry-run, validate in M1)

**Estimated Effort:** 0.5-1 day

> **Why so fast?** Code is already written in this plan. Apply operation exists in Flamingock core. CLI is just wiring + spawn.

**Success Criteria:**
```bash
flamingock --help                              # Shows help
flamingock --version                           # Shows version
flamingock execute apply --jar ./my-app.jar    # Executes apply
```

### 12.3 M1 - Full Operations

**Goal:** All four operations working with proper output.

**Deliverables:**
- [ ] `UndoCommand`, `DryRunCommand`, `ValidateCommand`
- [ ] `ConsoleFormatter` with colors and icons
- [ ] `Spinner` for progress indication
- [ ] `@ExcludeFromCliMode` annotation

**Estimated Effort:** 0.5-1 day

> **Why so fast?** Same pattern as ApplyCommand. Copy, adjust operation enum, done.

**Success Criteria:**
```bash
flamingock execute apply --jar ./app.jar      # ✓
flamingock execute undo --jar ./app.jar       # ✓
flamingock execute dry-run --jar ./app.jar    # ✓
flamingock execute validate --jar ./app.jar   # ✓
```

### 12.4 M2 - Production Hardening

**Goal:** Production-ready with all operability features.

**Deliverables:**
- [ ] `--jvm-arg` and `--` app args passthrough
- [ ] `--capture-logs` with CircularBuffer
- [ ] `ProcessSignalHandler` (Ctrl+C handling)
- [ ] `--output json` format
- [ ] `EnvironmentDetector`, `KnownErrors`, `ErrorFormatter`

**Estimated Effort:** 0.5-1 day

> **Why so fast?** All code is in the plan. Just implementation.

**Success Criteria:**
```bash
flamingock execute apply --jar ./app.jar --jvm-arg "-Xmx512m"
flamingock execute apply --jar ./app.jar -- --spring.profiles.active=prod
flamingock execute apply --jar ./app.jar --output json
# Ctrl+C properly terminates child process
```

### 12.5 M3 - Documentation & Polish

**Goal:** Production deployment ready.

**Deliverables:**
- [ ] User documentation (CLI section)
- [ ] CI/CD examples (GitHub Actions, GitLab CI)
- [ ] Shell completion (optional, can defer)

**Estimated Effort:** 0.5 day

> **Why so fast?** Generated from plan + implementation with Claude Code.

**Success Criteria:**
- Documentation covers all commands
- CI/CD examples work

### 12.6 Risk Factors

| Risk                               | Impact     | Mitigation                 |
|------------------------------------|------------|----------------------------|
| Undo not fully implemented in core | +0.5-1 day | Can defer undo to post-MVP |
| Spring Boot 2.x vs 3.x differences | +0.5 day   | Test early                 |

> **Low risk overall:** Plan is detailed, code is written, patterns are standard.

### 12.7 Recommended Approach

1. **MVP first** - Get apply working end-to-end
2. **Test on real app** - Validate with actual Spring Boot project
3. **Iterate quickly** - Add operations, hardening, docs incrementally

### 12.8 Team Presentation Summary

**What we're building:**
- Professional CLI for executing Flamingock outside app startup
- Process-based (spawn JVM) for isolation and simplicity
- Full support for apply, undo, dry-run, validate
- Production-ready with CI/CD integration

**Why it matters:**
- DevOps can control when changes run
- Proper CI/CD integration (exit codes, JSON output)
- Decouples system changes from deployment
- Professional UX (like Docker/kubectl)

**Implementation approach:**
- Plan is well-defined with code already written
- Using Claude Code for implementation
- **~2-3.5 days estimated effort** (code is in the plan, just need to wire it up)
- Incremental milestones: MVP → Operations → Hardening → Docs

**Key decisions:**
- Process-based execution (not ClassLoader magic)
- Honest contract (user conditions their beans)
- Dedicated module as THE recommendation for production

---

**FINAL COMPLETE PLAN v1.0**

**Date:** 2026-02-02

**Status:** Ready for implementation

**Next Steps:**
1. Team review
2. External validation
3. Begin implementation (MVP first)
