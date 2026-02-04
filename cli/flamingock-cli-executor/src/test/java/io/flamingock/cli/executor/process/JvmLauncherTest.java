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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JvmLauncher command building.
 */
class JvmLauncherTest {

    @Test
    void buildCommand_shouldContainJarFlag() {
        JvmLauncher launcher = new JvmLauncher(false);
        List<String> command = launcher.buildCommand("/path/to/app.jar");

        assertTrue(command.contains("-jar"));
        assertTrue(command.contains("/path/to/app.jar"));
    }

    @Test
    void buildCommand_shouldContainSpringWebDisabled() {
        JvmLauncher launcher = new JvmLauncher(false);
        List<String> command = launcher.buildCommand("/path/to/app.jar");

        assertTrue(command.contains("--spring.main.web-application-type=none"));
    }

    @Test
    void buildCommand_shouldContainCliProfile() {
        JvmLauncher launcher = new JvmLauncher(false);
        List<String> command = launcher.buildCommand("/path/to/app.jar");

        assertTrue(command.contains("--spring.profiles.include=flamingock-cli"));
    }

    @Test
    void buildCommand_shouldContainCliModeFlag() {
        JvmLauncher launcher = new JvmLauncher(false);
        List<String> command = launcher.buildCommand("/path/to/app.jar");

        assertTrue(command.contains("--flamingock.cli.mode=true"));
    }

    @Test
    void buildCommand_shouldDisableBanner() {
        JvmLauncher launcher = new JvmLauncher(false);
        List<String> command = launcher.buildCommand("/path/to/app.jar");

        assertTrue(command.contains("--spring.main.banner-mode=off"));
    }

    @Test
    void buildCommand_shouldHaveCorrectFlagCount() {
        JvmLauncher launcher = new JvmLauncher(false);
        List<String> command = launcher.buildCommand("/path/to/app.jar");

        // java -jar <jar> + 4 flags = 7 elements
        assertEquals(7, command.size());
    }

    @Test
    void getJavaExecutable_shouldReturnNonEmpty() {
        JvmLauncher launcher = new JvmLauncher(false);
        String javaExecutable = launcher.getJavaExecutable();

        assertTrue(javaExecutable != null && !javaExecutable.isEmpty());
    }
}
