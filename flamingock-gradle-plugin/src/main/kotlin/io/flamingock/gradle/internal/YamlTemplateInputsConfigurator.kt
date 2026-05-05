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

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

/**
 * Registers Flamingock YAML template files as inputs of every compile task that may run the
 * annotation processor. Without this, an incremental build that only modifies a YAML file
 * does not re-trigger Java/Kotlin compilation, and therefore does not re-run the processor.
 *
 * For each source set in the project we collect every {@code *.yaml}/{@code *.yml} file
 * under the source set's {@code java} and {@code resources} src dirs, then attach the
 * resulting [FileCollection] as a tracked input on:
 *
 *  - the standard Java compile task ([SourceSet.getCompileJavaTaskName]),
 *  - the Kotlin compile task ({@code KotlinCompile}) — only if the Kotlin plugin is applied,
 *  - the KAPT task ({@code KaptTask}) — only if the Kotlin KAPT plugin is applied,
 *  - the KSP task ({@code KspTaskJvm}) — only if the KSP plugin is applied.
 *
 * Plugin presence is checked reflectively via [Class.forName] so this configurator does NOT
 * pull the Kotlin/KAPT/KSP plugin classes onto the Flamingock plugin's compile classpath.
 *
 * What this does NOT do:
 *  - It does not package the YAML files into the JAR (that's a separate concern).
 *  - It does not register the YAMLs with GraalVM (also separate).
 *  - It only ensures that YAML changes participate in Gradle's up-to-date check so the
 *    annotation processor runs. The processor itself (Phase 1) is already prepared to read
 *    persisted metadata when a partial round doesn't see {@code @EnableFlamingock}.
 */
internal object YamlTemplateInputsConfigurator {

    private const val PROPERTY_NAME = "flamingockYamlTemplates"
    private val YAML_INCLUDES = listOf("**/*.yaml", "**/*.yml")

    private const val KOTLIN_COMPILE_FQN = "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"
    private const val KAPT_TASK_FQN = "org.jetbrains.kotlin.gradle.internal.KaptTask"
    private const val KSP_TASK_FQN = "com.google.devtools.ksp.gradle.KspTaskJvm"

    fun configure(project: Project) {
        val sourceSets = project.extensions.findByType(SourceSetContainer::class.java) ?: return
        sourceSets.configureEach(Action<SourceSet> {
            val yamlFiles = collectYamlFiles(project, this)
            attachJavaCompile(project, this, yamlFiles)
            attachKotlinTask(project, KOTLIN_COMPILE_FQN, this, yamlFiles, "compile", "Kotlin")
            attachKotlinTask(project, KAPT_TASK_FQN, this, yamlFiles, "kapt", "Kotlin")
            attachKotlinTask(project, KSP_TASK_FQN, this, yamlFiles, "ksp", "Kotlin")
        })
    }

    private fun collectYamlFiles(project: Project, sourceSet: SourceSet): FileCollection {
        val trees: ArrayList<Any> = ArrayList()
        for (dir in sourceSet.java.srcDirs) {
            val tree = project.fileTree(dir)
            tree.include(YAML_INCLUDES)
            trees.add(tree)
        }
        for (dir in sourceSet.resources.srcDirs) {
            val tree = project.fileTree(dir)
            tree.include(YAML_INCLUDES)
            trees.add(tree)
        }
        return project.files(*trees.toArray())
    }

    private fun attachJavaCompile(project: Project, sourceSet: SourceSet, yaml: FileCollection) {
        // The java plugin is always applied (FlamingockPlugin.apply guarantees it), so this
        // task always exists. Use named() for laziness.
        project.tasks.named(sourceSet.compileJavaTaskName)
                .configure(Action<Task> { registerInputs(this, yaml) })
    }

    /**
     * Type-check the Kotlin/KAPT/KSP plugin is applied (via [Class.forName] — no compile-time
     * dep on those plugins), then attach YAML inputs to the per-source-set task that follows
     * the standard naming convention. Tolerant of:
     *  - the plugin not being applied (`Class.forName` throws → silent skip);
     *  - the conventional task not existing for this source set (`matching` collection stays
     *    empty → silent skip).
     */
    private fun attachKotlinTask(
        project: Project,
        taskClassFqn: String,
        sourceSet: SourceSet,
        yaml: FileCollection,
        taskNamePrefix: String,
        taskNameSuffix: String
    ) {
        val cls = tryLoadClass(taskClassFqn) ?: return
        @Suppress("UNCHECKED_CAST")
        val typed = cls as Class<Task>
        val expectedName = composeTaskName(sourceSet, taskNamePrefix, taskNameSuffix)
        project.tasks.withType(typed)
                .matching { it.name == expectedName }
                .configureEach(Action<Task> { registerInputs(this, yaml) })
    }

    private fun registerInputs(task: Task, yaml: FileCollection) {
        task.inputs.files(yaml)
            .withPropertyName(PROPERTY_NAME)
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }

    /**
     * Compose the conventional Kotlin/KAPT/KSP task name for a given source set:
     *   main           + "compile" + "Kotlin" → compileKotlin
     *   test           + "compile" + "Kotlin" → compileTestKotlin
     *   integrationTest + "kapt"   + "Kotlin" → kaptIntegrationTestKotlin
     *   main           + "ksp"     + "Kotlin" → kspKotlin
     */
    private fun composeTaskName(sourceSet: SourceSet, prefix: String, suffix: String): String {
        val infix = if (sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME) ""
        else sourceSet.name.replaceFirstChar { it.uppercase() }
        return "$prefix$infix$suffix"
    }

    private fun tryLoadClass(fqn: String): Class<*>? = try {
        Class.forName(fqn)
    } catch (_: ClassNotFoundException) {
        null
    }
}
