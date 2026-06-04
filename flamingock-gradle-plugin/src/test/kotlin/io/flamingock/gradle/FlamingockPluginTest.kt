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
    fun `shouldAutoApplyKapt returns true when no property is set`() {
        // The opt-out is read at withId-fire time via Gradle's findProperty. With no value
        // anywhere (gradle.properties, -P, or env), the helper returns true so kapt is
        // auto-applied — preserving the 1.4.1 default behaviour.
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        val plugin = FlamingockPlugin()

        assertTrue(
            plugin.shouldAutoApplyKapt(project),
            "Default (property unset) must auto-apply kapt"
        )
    }

    @Test
    fun `shouldAutoApplyKapt returns false when flamingock autoApplyKapt is set to false`() {
        // Simulates a user setting flamingock.autoApplyKapt=false in gradle.properties or
        // via -Pflamingock.autoApplyKapt=false. ProjectBuilder lets us seed extra properties
        // via the project's ExtraPropertiesExtension; the same lookup path Gradle uses for
        // -P / gradle.properties resolves through findProperty().
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.extensions.extraProperties.set(FlamingockConstants.AUTO_APPLY_KAPT_PROPERTY, "false")
        val plugin = FlamingockPlugin()

        assertFalse(
            plugin.shouldAutoApplyKapt(project),
            "Explicit 'false' must opt out of kapt auto-apply"
        )
    }

    @Test
    fun `shouldAutoApplyKapt treats false-string case-insensitively, other values default to true`() {
        // Case variants of "false" all opt out; anything else (including "no", "0", "FALSE!",
        // arbitrary strings) preserves the default. This avoids surprising the user with
        // values that look like opt-out but aren't (e.g. "no" stays on so the user sees the
        // auto-apply happen and learns the canonical property name from docs).
        val plugin = FlamingockPlugin()

        listOf("false", "False", "FALSE").forEach { v ->
            val p = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
            p.extensions.extraProperties.set(FlamingockConstants.AUTO_APPLY_KAPT_PROPERTY, v)
            assertFalse(plugin.shouldAutoApplyKapt(p), "'$v' must opt out")
        }
        listOf("true", "True", "TRUE", "yes", "no", "0", "1", "").forEach { v ->
            val p = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
            p.extensions.extraProperties.set(FlamingockConstants.AUTO_APPLY_KAPT_PROPERTY, v)
            assertTrue(
                plugin.shouldAutoApplyKapt(p),
                "'$v' must not opt out — only literal case-insensitive 'false' suppresses auto-apply"
            )
        }
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
