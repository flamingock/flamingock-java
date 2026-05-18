/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.gradle.internal

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Unit tests for {@link YamlTemplateInputsConfigurator}, exercised through Gradle's
 * {@code ProjectBuilder} test fixture. The Kotlin/KAPT/KSP paths are not covered here
 * (they require applying those plugins, which is impractical in a synthetic project);
 * they are validated manually as part of Phase 3's smoke-test plan.
 */
class YamlTemplateInputsConfiguratorTest {

    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `attaches YAML inputs to compileJava when java plugin is applied`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        // Fixture: one YAML under src/main/java, one under src/main/resources.
        writeYamlFixture("src/main/java/com/example/changes/_0001__create.yaml")
        writeYamlFixture("src/main/resources/db/_0002__seed.yml")

        YamlTemplateInputsConfigurator.configure(project)

        val compileJava = project.tasks.named("compileJava", JavaCompile::class.java).get()
        val tracked = trackedYamlFiles(compileJava)
        assertTrue(tracked.any { it.endsWith("_0001__create.yaml") },
                "compileJava should track the .yaml fixture; tracked=$tracked")
        assertTrue(tracked.any { it.endsWith("_0002__seed.yml") },
                "compileJava should track the .yml fixture; tracked=$tracked")
    }

    @Test
    fun `attaches YAML inputs to compileTestJava for test source set`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        writeYamlFixture("src/test/java/com/example/changes/_0001__test.yaml")
        writeYamlFixture("src/test/resources/_0002__seed.yml")

        YamlTemplateInputsConfigurator.configure(project)

        val compileTestJava = project.tasks.named("compileTestJava", JavaCompile::class.java).get()
        val tracked = trackedYamlFiles(compileTestJava)
        assertTrue(tracked.any { it.endsWith("_0001__test.yaml") },
                "compileTestJava should track the .yaml fixture; tracked=$tracked")
        assertTrue(tracked.any { it.endsWith("_0002__seed.yml") },
                "compileTestJava should track the .yml fixture; tracked=$tracked")
    }

    @Test
    fun `compileJava and compileTestJava only track their own source set's YAML files`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        writeYamlFixture("src/main/resources/_main_only.yaml")
        writeYamlFixture("src/test/resources/_test_only.yaml")

        YamlTemplateInputsConfigurator.configure(project)

        val compileJava = project.tasks.named("compileJava", JavaCompile::class.java).get()
        val compileTestJava = project.tasks.named("compileTestJava", JavaCompile::class.java).get()
        val mainTracked = trackedYamlFiles(compileJava)
        val testTracked = trackedYamlFiles(compileTestJava)

        assertTrue(mainTracked.any { it.endsWith("_main_only.yaml") })
        assertFalse(mainTracked.any { it.endsWith("_test_only.yaml") },
                "compileJava must NOT track test-source-set YAML; tracked=$mainTracked")
        assertTrue(testTracked.any { it.endsWith("_test_only.yaml") })
        assertFalse(testTracked.any { it.endsWith("_main_only.yaml") },
                "compileTestJava must NOT track main-source-set YAML; tracked=$testTracked")
    }

    @Test
    fun `is a no-op when java plugin is not applied`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        // No java plugin applied.

        assertDoesNotThrow {
            YamlTemplateInputsConfigurator.configure(project)
        }
    }

    @Test
    fun `is a no-op when there are no YAML fixtures`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        assertDoesNotThrow {
            YamlTemplateInputsConfigurator.configure(project)
        }

        val compileJava = project.tasks.named("compileJava", JavaCompile::class.java).get()
        val tracked = trackedYamlFiles(compileJava)
        assertTrue(tracked.isEmpty(),
                "no YAML fixtures should produce no tracked entries; tracked=$tracked")
    }

    // ----------------------------- helpers -----------------------------

    private fun writeYamlFixture(relativePath: String) {
        val file = projectDir.resolve(relativePath).toFile()
        file.parentFile.mkdirs()
        file.writeText("# fixture\n")
    }

    /**
     * Inspect the compile task's tracked input files filtered to YAML/YML, regardless of
     * which Gradle property name they were registered under. Returns absolute paths as
     * strings so callers can endsWith-match.
     */
    private fun trackedYamlFiles(task: org.gradle.api.Task): List<String> {
        return task.inputs.files.files
                .filter { it.name.endsWith(".yaml") || it.name.endsWith(".yml") }
                .map(File::getAbsolutePath)
    }
}
