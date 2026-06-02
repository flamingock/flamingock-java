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

import io.flamingock.gradle.FlamingockExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Unit tests for [DependencyConfigurator] focused on the `kaptEnabled` branch — specifically
 * the `mongock-support` registration under the `kapt` configuration that the [#918] fix added.
 *
 * <p>The Kotlin Gradle Plugin is intentionally not on the test classpath. `kaptEnabled` is
 * driven via the fallback `findByName("kapt") != null` path, by pre-creating the configuration
 * — the same observable state the real `org.jetbrains.kotlin.kapt` plugin produces.
 */
class DependencyConfiguratorTest {

    @TempDir
    lateinit var projectDir: Path

    private val version = "TEST-VERSION"
    private val mongockSupport = "io.flamingock:mongock-support"

    @Test
    fun `mongock-support is added to kapt when both kapt is enabled and mongock support is requested`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")
        project.configurations.create("kapt")
        val extension = FlamingockExtension().apply { mongock() }

        DependencyConfigurator.configure(project, extension, version)

        val kapt = project.configurations.getByName("kapt").allDependencies
            .map { "${it.group}:${it.name}" }.toSet()
        assertTrue(
            kapt.contains(mongockSupport),
            "kapt configuration must include mongock-support when kapt + mongock support are both on. Actual: $kapt"
        )
        // The annotationProcessor side also gets it; verifying both keeps the contract explicit.
        val ap = project.configurations.getByName("annotationProcessor").allDependencies
            .map { "${it.group}:${it.name}" }.toSet()
        assertTrue(
            ap.contains(mongockSupport),
            "annotationProcessor must still include mongock-support alongside the kapt registration. Actual: $ap"
        )
    }

    @Test
    fun `mongock-support is NOT added to kapt when kapt is enabled but mongock support is not requested`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")
        project.configurations.create("kapt")
        val extension = FlamingockExtension() // mongock() NOT called

        DependencyConfigurator.configure(project, extension, version)

        val kapt = project.configurations.getByName("kapt").allDependencies
            .map { "${it.group}:${it.name}" }.toSet()
        assertFalse(
            kapt.contains(mongockSupport),
            "kapt configuration must NOT include mongock-support when mongock is disabled. Actual: $kapt"
        )
        // mongock-support also must not appear on annotationProcessor when mongock is disabled.
        val ap = project.configurations.getByName("annotationProcessor").allDependencies
            .map { "${it.group}:${it.name}" }.toSet()
        assertFalse(
            ap.contains(mongockSupport),
            "annotationProcessor must NOT include mongock-support when mongock is disabled. Actual: $ap"
        )
    }
}
