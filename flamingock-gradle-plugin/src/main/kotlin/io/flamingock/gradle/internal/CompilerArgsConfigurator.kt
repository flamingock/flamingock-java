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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

/**
 * Passes Gradle's authoritative source/resource roots to the Flamingock annotation processor
 * via the `-Aflamingock.sources=` and `-Aflamingock.resources=` compiler arguments. Bypasses
 * the AP-side filesystem detection (`ProjectRootDetector`), which can guess wrong in some
 * multi-module Gradle layouts.
 *
 * For each source set in the project we:
 *  - enumerate all source dirs across registered language extensions (Java built-in, plus
 *    Kotlin / Groovy / Scala when their plugins are applied; new languages register the same
 *    way and get picked up automatically),
 *  - join them with [File.pathSeparator] into a single `-Aflamingock.sources=...` arg,
 *  - take the first resources src dir (the universal default; multi-resource support is
 *    deferred until a real use case appears).
 *
 * The args land on every [JavaCompile] task — `compileJava`, `compileTestJava`, and any
 * source-set–derived equivalents created by user code or third-party plugins. KAPT and KSP
 * task wiring is intentionally out of scope for this configurator and will land in a
 * follow-up.
 */
internal object CompilerArgsConfigurator {

    private const val SOURCES_OPTION = "flamingock.sources"
    private const val RESOURCES_OPTION = "flamingock.resources"

    fun configure(project: Project) {
        val sourceSets = project.extensions.findByType(SourceSetContainer::class.java) ?: return
        sourceSets.configureEach(Action<SourceSet> {
            val sourceDirs = collectSourceDirs(this)
            if (sourceDirs.isEmpty()) return@Action

            val sourcesArg = sourceDirs.joinToString(File.pathSeparator) { it.absolutePath }
            val resourcesArg = this.resources.srcDirs.firstOrNull()?.absolutePath

            project.tasks.named(this.compileJavaTaskName, JavaCompile::class.java)
                .configure(Action<JavaCompile> {
                    options.compilerArgs.add("-A$SOURCES_OPTION=$sourcesArg")
                    if (resourcesArg != null) {
                        options.compilerArgs.add("-A$RESOURCES_OPTION=$resourcesArg")
                    }
                })
        })
    }

    private fun collectSourceDirs(sourceSet: SourceSet): Set<File> {
        val all = LinkedHashSet<File>()
        all.addAll(sourceSet.java.srcDirs)
        // Each language plugin (Kotlin, Groovy, Scala, ...) registers a SourceDirectorySet
        // extension on the SourceSet under its own name. Looking these up by well-known names
        // means we don't take a compile-time dependency on those plugins; if a plugin isn't
        // applied the lookup just returns null and we move on.
        listOf("kotlin", "groovy", "scala").forEach { name ->
            val ext = (sourceSet as? ExtensionAware)?.extensions?.findByName(name)
            if (ext is SourceDirectorySet) all.addAll(ext.srcDirs)
        }
        return all
    }
}
