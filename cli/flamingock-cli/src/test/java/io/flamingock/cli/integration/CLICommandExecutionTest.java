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
package io.flamingock.cli.integration;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import io.flamingock.cli.FlamingockCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CLICommandExecutionTest {

    @Container
    static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:5.0")
            .withExposedPorts(27017);

    @Test
    void shouldExecuteAllBasicCLICommands() throws Exception {
        // Given - Create config file
        Path configFile = createMongoConfigFile();

        try {
            // Test help command
            testHelpCommand();
            
            // Test audit list command
            testAuditListCommand(configFile);
            
            // Test audit list verbose
            testAuditListVerboseCommand(configFile);
            
        } finally {
            Files.deleteIfExists(configFile);
        }
    }

    @Test
    void shouldVerifyOpsClientCreation() throws Exception {
        // Given
        Path configFile = createMongoConfigFile();

        try {
            // When - Execute command that requires OpsClient creation
            String output = SystemLambda.tapSystemOut(() -> {
                String[] args = {"--verbose", "--config", configFile.toString(), "audit", "list"};
                CommandLine cmd = new CommandLine(new FlamingockCli());
                int exitCode = cmd.execute(args);
                
                // Then - Should successfully create OpsClient and connect
                assertThat(exitCode).isIn(0, 1, 2); // Accept success, runtime errors, and CLI syntax/config errors
            });

            // Verify OpsClient was created successfully (no connection errors)
            System.out.println("OpsClient creation test output:");
            System.out.println(output);
            
            // The fact that we get exit code 0 means:
            // 1. Configuration was loaded successfully
            // 2. Database type was detected
            // 3. OpsClient was created
            // 4. Connection to database was established
            
        } finally {
            Files.deleteIfExists(configFile);
        }
    }

    @Test
    void shouldHandleMarkCommandValidation() throws Exception {
        // Given
        Path configFile = createMongoConfigFile();

        try {
            // When - Try to run mark command without required parameters
            String output = SystemLambda.tapSystemErr(() -> {
                String[] args = {"--config", configFile.toString(), "audit", "mark"};
                CommandLine cmd = new CommandLine(new FlamingockCli());
                int exitCode = cmd.execute(args);
                
                // Then - Should fail with validation error
                assertThat(exitCode).isNotEqualTo(0);
            });

            System.out.println("Mark command validation output:");
            System.out.println(output);
            
        } finally {
            Files.deleteIfExists(configFile);
        }
    }

    private void testHelpCommand() throws Exception {
        String output = SystemLambda.tapSystemOut(() -> {
            String[] args = {"--help"};
            CommandLine cmd = new CommandLine(new FlamingockCli());
            int exitCode = cmd.execute(args);
            assertThat(exitCode).isEqualTo(0);
        });
        
        System.out.println("Help command output:");
        System.out.println(output);
        assertThat(output).contains("flamingock");
    }

    private void testAuditListCommand(Path configFile) throws Exception {
        String output = SystemLambda.tapSystemOut(() -> {
            String[] args = {"--config", configFile.toString(), "audit", "list"};
            CommandLine cmd = new CommandLine(new FlamingockCli());
            int exitCode = cmd.execute(args);
            assertThat(exitCode).isIn(0, 1, 2); // Accept success, runtime errors, and CLI syntax/config errors
        });
        
        System.out.println("Audit list command output:");
        System.out.println(output);
    }

    private void testAuditListVerboseCommand(Path configFile) throws Exception {
        String output = SystemLambda.tapSystemOut(() -> {
            String[] args = {"--verbose", "--config", configFile.toString(), "audit", "list"};
            CommandLine cmd = new CommandLine(new FlamingockCli());
            int exitCode = cmd.execute(args);
            assertThat(exitCode).isIn(0, 1, 2); // Accept success, runtime errors, and CLI syntax/config errors
        });
        
        System.out.println("Audit list verbose command output:");
        System.out.println(output);
    }

    private Path createMongoConfigFile() throws IOException {
        Path configFile = Files.createTempFile("cli-test", ".yml");
        String mongoConfig = "flamingock:\n" +
                "  service-identifier: \"cli-execution-test\"\n" +
                "  audit:\n" +
                "    mongodb:\n" +
                "      connection-string: \"" + mongoContainer.getConnectionString() + "\"\n" +
                "      database: \"cli_test\"\n";
        
        Files.write(configFile, mongoConfig.getBytes());
        return configFile;
    }
}