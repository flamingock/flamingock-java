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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JvmLauncher command building.
 */
class JvmLauncherTest {

    // ================== Spring Boot Command Tests ==================

    @Test
    void buildSpringBootCommand_shouldContainJarFlag() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, null, null);

        assertTrue(command.contains("-jar"));
        assertTrue(command.contains("/path/to/app.jar"));
    }

    @Test
    void buildSpringBootCommand_shouldContainSpringWebDisabled() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, null, null);

        assertTrue(command.contains("--spring.main.web-application-type=none"));
    }

    @Test
    void buildSpringBootCommand_shouldContainCliProfile() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, null, null);

        assertTrue(command.contains("--spring.profiles.include=flamingock-cli"));
    }

    @Test
    void buildSpringBootCommand_shouldContainCliModeFlag() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, null, null);

        assertTrue(command.contains("--flamingock.cli.mode=true"));
    }

    @Test
    void buildSpringBootCommand_shouldDisableBanner() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, null, null);

        assertTrue(command.contains("--spring.main.banner-mode=off"));
    }

    @Test
    void buildSpringBootCommand_shouldHaveCorrectFlagCountWithoutOperation() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, null, null);

        // java -jar <jar> + 4 flags = 7 elements
        assertEquals(7, command.size());
    }

    @Test
    void buildSpringBootCommand_shouldIncludeOperationWhenProvided() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", "EXECUTE", null, null);

        assertTrue(command.contains("--flamingock.operation=EXECUTE"));
        // java -jar <jar> + 4 flags + operation = 8 elements
        assertEquals(8, command.size());
    }

    @Test
    void buildSpringBootCommand_shouldIncludeListOperation() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", "LIST", null, null);

        assertTrue(command.contains("--flamingock.operation=LIST"));
    }

    @Test
    void buildSpringBootCommand_shouldNotIncludeOperationWhenEmpty() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", "", null, null);

        // Should not contain any operation flag
        for (String arg : command) {
            assertFalse(arg.startsWith("--flamingock.operation="));
        }
        assertEquals(7, command.size());
    }

    @Test
    void buildSpringBootCommand_shouldIncludeLogLevelWhenProvided() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", "EXECUTE", null, "debug");

        assertTrue(command.contains("--logging.level.root=DEBUG"));
    }

    @Test
    void buildSpringBootCommand_shouldUppercaseLogLevel() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, null, "info");

        assertTrue(command.contains("--logging.level.root=INFO"));
    }

    @Test
    void buildSpringBootCommand_shouldNotIncludeLogLevelWhenNull() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, null, null);

        for (String arg : command) {
            assertFalse(arg.startsWith("--logging.level.root="));
        }
    }

    @Test
    void buildSpringBootCommand_shouldNotIncludeLogLevelWhenEmpty() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, null, "");

        for (String arg : command) {
            assertFalse(arg.startsWith("--logging.level.root="));
        }
    }

    @Test
    void buildSpringBootCommand_shouldIncludeOutputFileWhenProvided() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildSpringBootCommand("/path/to/app.jar", null, "/tmp/output.json", null);

        assertTrue(command.contains("--flamingock.output-file=/tmp/output.json"));
    }

    // ================== Plain Uber JAR Command Tests ==================

    @Test
    void buildPlainUberCommand_usesClasspath() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildPlainUberCommand("/path/to/app.jar", null, null, null);

        assertTrue(command.contains("-cp"));
        assertTrue(command.contains("/path/to/app.jar"));
        assertFalse(command.contains("-jar"));
    }

    @Test
    void buildPlainUberCommand_usesEntryPoint() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildPlainUberCommand("/path/to/app.jar", null, null, null);

        assertTrue(command.contains(JvmLauncher.FLAMINGOCK_CLI_ENTRY_POINT));
    }

    @Test
    void buildPlainUberCommand_noSpringFlags() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildPlainUberCommand("/path/to/app.jar", null, null, null);

        for (String arg : command) {
            assertFalse(arg.startsWith("--spring."), "Should not contain Spring flags: " + arg);
        }
    }

    @Test
    void buildPlainUberCommand_includesFlamingockFlags() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildPlainUberCommand("/path/to/app.jar", "EXECUTE", "/tmp/output.json", null);

        assertTrue(command.contains("--flamingock.cli.mode=true"));
        assertTrue(command.contains("--flamingock.operation=EXECUTE"));
        assertTrue(command.contains("--flamingock.output-file=/tmp/output.json"));
    }

    @Test
    void buildPlainUberCommand_usesFlamingockLogLevel() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildPlainUberCommand("/path/to/app.jar", null, null, "debug");

        assertTrue(command.contains("--flamingock.log.level=DEBUG"));
        // Should not use Spring-style logging
        for (String arg : command) {
            assertFalse(arg.startsWith("--logging.level.root="));
        }
    }

    @Test
    void buildPlainUberCommand_shouldHaveCorrectFlagCount() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildPlainUberCommand("/path/to/app.jar", null, null, null);

        // java -cp <jar> <entrypoint> + cli.mode = 5 elements
        assertEquals(5, command.size());
    }

    @Test
    void buildPlainUberCommand_shouldIncludeOperationWhenProvided() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildPlainUberCommand("/path/to/app.jar", "LIST", null, null);

        assertTrue(command.contains("--flamingock.operation=LIST"));
        // java -cp <jar> <entrypoint> + cli.mode + operation = 6 elements
        assertEquals(6, command.size());
    }

    @Test
    void buildPlainUberCommand_shouldNotIncludeOperationWhenEmpty() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildPlainUberCommand("/path/to/app.jar", "", null, null);

        for (String arg : command) {
            assertFalse(arg.startsWith("--flamingock.operation="));
        }
    }

    @Test
    void buildPlainUberCommand_shouldNotIncludeLogLevelWhenNull() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildPlainUberCommand("/path/to/app.jar", null, null, null);

        for (String arg : command) {
            assertFalse(arg.startsWith("--flamingock.log.level="));
        }
    }

    // ================== JAR Type Routing Tests ==================

    @Test
    void buildCommand_routesToSpringBootForSpringBootJar() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildCommand("/path/to/app.jar", null, null, null, JarType.SPRING_BOOT);

        assertTrue(command.contains("-jar"));
        assertTrue(command.contains("--spring.main.web-application-type=none"));
    }

    @Test
    void buildCommand_routesToPlainUberForPlainUberJar() {
        JvmLauncher launcher = new JvmLauncher();
        List<String> command = launcher.buildCommand("/path/to/app.jar", null, null, null, JarType.PLAIN_UBER);

        assertTrue(command.contains("-cp"));
        assertTrue(command.contains(JvmLauncher.FLAMINGOCK_CLI_ENTRY_POINT));
    }

    // ================== General Tests ==================

    @Test
    void getJavaExecutable_shouldReturnNonEmpty() {
        JvmLauncher launcher = new JvmLauncher();
        String javaExecutable = launcher.getJavaExecutable();

        assertTrue(javaExecutable != null && !javaExecutable.isEmpty());
    }
}
