/*
 * Copyright 2024 Flamingock (https://www.flamingock.io)
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

import io.flamingock.gradle.internal.CompilerArgsConfigurator
import io.flamingock.gradle.internal.DependencyConfigurator
import io.flamingock.gradle.internal.FlamingockConstants
import io.flamingock.gradle.internal.YamlTemplateInputsConfigurator
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Flamingock Gradle Plugin.
 *
 * Simplifies Flamingock setup by automatically configuring dependencies
 * and annotation processors.
 *
 * Usage:
 * ```
 * plugins {
 *     id("io.flamingock") version "1.0.0"
 * }
 *
 * flamingock {
 *     community()
 *     mongock()     // optional
 *     springboot()  // optional
 *     graalvm()     // optional
 * }
 * ```
 */
class FlamingockPlugin : Plugin<Project> {

    companion object {
        private const val KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
    }

    override fun apply(project: Project) {
        // Apply java plugin if not already applied
        if (!project.plugins.hasPlugin("java")) {
            project.plugins.apply("java")
        }

        // Kotlin projects need KAPT so the Flamingock annotation processor runs on Kotlin
        // sources. Apply it automatically to preserve the plugin's zero-boilerplate intent.
        // Users who want to manage kapt themselves (or who plan to migrate to KSP) can opt
        // out via -Pflamingock.autoApplyKapt=false. The opt-out has to be a Gradle property
        // and not a `flamingock { … }` DSL setting because the `plugins { }` block (where
        // the user applies kotlin.jvm) evaluates before the `flamingock { }` block, so any
        // DSL setter would fire too late to influence the auto-apply that already happened.
        project.plugins.withId(KOTLIN_JVM_PLUGIN_ID) {
            if (shouldAutoApplyKapt(project)
                    && !project.plugins.hasPlugin(FlamingockConstants.KAPT_PLUGIN_ID)) {
                project.pluginManager.apply(FlamingockConstants.KAPT_PLUGIN_ID)
            }
        }

        // Create and register the extension
        val extension = project.extensions.create(
            FlamingockConstants.EXTENSION_NAME,
            FlamingockExtension::class.java
        )

        // Phase 3: register YAML template files as inputs of every compile task that may run
        // the annotation processor, so YAML-only changes invalidate the compile task and
        // trigger annotation processing. Lazy registration — does not require afterEvaluate.
        YamlTemplateInputsConfigurator.configure(project)

        // Pass Gradle's authoritative source/resource roots to the AP via -A options. Skips
        // the AP-side filesystem detection in multi-module Gradle layouts where it can land
        // on the wrong directory.
        CompilerArgsConfigurator.configure(project)

        // Configure dependencies after project evaluation
        project.afterEvaluate {
            validateConfiguration(extension)
            DependencyConfigurator.configure(project, extension, FlamingockConstants.FLAMINGOCK_VERSION)
        }
    }

    /**
     * Decides whether the plugin should auto-apply `org.jetbrains.kotlin.kapt`. Defaults to
     * `true`; users opt out by setting [FlamingockConstants.AUTO_APPLY_KAPT_PROPERTY] to a
     * value equal (case-insensitively) to `"false"`. Any other value — including unset —
     * preserves the auto-apply behaviour. Read at `withId`-fire time so the decision is
     * available before kapt would otherwise be applied. Visible to the same module so tests
     * can exercise the property handling directly.
     */
    internal fun shouldAutoApplyKapt(project: Project): Boolean {
        val raw = project.findProperty(FlamingockConstants.AUTO_APPLY_KAPT_PROPERTY) ?: return true
        return !raw.toString().equals("false", ignoreCase = true)
    }

    private fun validateConfiguration(extension: FlamingockExtension) {
        if (extension.isCommunityEnabled && extension.isCloudEnabled) {
            throw GradleException(
                """
                |
                |FLAMINGOCK CONFIGURATION ERROR
                |
                |Both community() and cloud() editions are selected.
                |
                |Please choose only one:
                |
                |flamingock {
                |    community()  // OR cloud() (default)
                |}
                |
                """.trimMargin()
            )
        }
    }
}
