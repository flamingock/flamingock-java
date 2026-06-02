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
package io.flamingock.gradle

import io.flamingock.gradle.internal.FlamingockConstants
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Unit tests for [FlamingockPlugin] using Gradle's `ProjectBuilder` test fixture.
 *
 * <p>Scope intentionally avoids pulling the Kotlin Gradle Plugin onto the test classpath
 * (the existing {@code CompilerArgsConfiguratorTest} avoids it for the same reason). Tests
 * therefore verify the wiring through {@link DependencyConfigurator}'s fallback path —
 * `project.configurations.findByName("kapt") != null` — which is the same branch the real
 * `org.jetbrains.kotlin.kapt` plugin triggers at apply time. The positive
 * "kotlin-jvm auto-applies kapt" case is best covered by a Gradle TestKit functional test
 * if/when stronger guarantees are needed; visual review of the five-line
 * {@code plugins.withId(KOTLIN_JVM_PLUGIN_ID) { ... }} block in
 * {@link FlamingockPlugin#apply} suffices for now.
 */
class FlamingockPluginTest {

    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `pure-Java project does not auto-apply kotlin kapt`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()

        project.plugins.apply(FlamingockPlugin::class.java)
        (project as ProjectInternal).evaluate()

        assertFalse(
            project.plugins.hasPlugin(FlamingockConstants.KAPT_PLUGIN_ID),
            "Java-only project must not auto-apply org.jetbrains.kotlin.kapt"
        )
        assertNull(
            project.configurations.findByName("kapt"),
            "Java-only project must not have a kapt configuration"
        )
    }

    @Test
    fun `pure-Java project registers flamingock-processor under annotationProcessor only`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()

        project.plugins.apply(FlamingockPlugin::class.java)
        (project as ProjectInternal).evaluate()

        val ap = project.configurations.getByName("annotationProcessor").allDependencies
            .map { "${it.group}:${it.name}" }.toSet()
        assertTrue(
            ap.contains("io.flamingock:flamingock-processor"),
            "annotationProcessor must include flamingock-processor. Actual: $ap"
        )
        // No kapt configuration means no kapt-side registration. Already asserted above; this
        // test is the positive complement to keep the failure mode explicit.
        assertNull(
            project.configurations.findByName("kapt"),
            "Java-only project must not have a kapt configuration"
        )
    }

    @Test
    fun `when a kapt configuration is present, DependencyConfigurator also registers flamingock-processor under kapt`() {
        // Simulate a project where kapt is "enabled" without pulling the Kotlin Gradle Plugin
        // onto the test classpath: pre-create the `kapt` configuration. DependencyConfigurator
        // then takes its fallback `findByName("kapt") != null` branch — the same branch the
        // real kotlin.kapt plugin's apply triggers.
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.configurations.create("kapt")

        project.plugins.apply(FlamingockPlugin::class.java)
        (project as ProjectInternal).evaluate()

        val kapt = project.configurations.getByName("kapt").allDependencies
            .map { "${it.group}:${it.name}" }.toSet()
        assertTrue(
            kapt.contains("io.flamingock:flamingock-processor"),
            "kapt configuration must include flamingock-processor when kapt is enabled. Actual: $kapt"
        )
        // annotationProcessor still receives it too — the fix adds to kapt, doesn't remove
        // from annotationProcessor.
        val ap = project.configurations.getByName("annotationProcessor").allDependencies
            .map { "${it.group}:${it.name}" }.toSet()
        assertTrue(
            ap.contains("io.flamingock:flamingock-processor"),
            "annotationProcessor must still include flamingock-processor even when kapt is wired. Actual: $ap"
        )
    }

    @Test
    fun `applying the plugin creates the flamingock extension`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()

        project.plugins.apply(FlamingockPlugin::class.java)

        // Extension is registered at apply-time (not afterEvaluate); checked without
        // triggering evaluate.
        assertNotNull(
            project.extensions.findByName(FlamingockConstants.EXTENSION_NAME),
            "Applying the plugin must register the '${FlamingockConstants.EXTENSION_NAME}' extension"
        )
    }
}
