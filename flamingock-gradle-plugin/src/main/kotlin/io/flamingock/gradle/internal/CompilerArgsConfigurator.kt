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
 * The args land on:
 *  - every [JavaCompile] task — `compileJava`, `compileTestJava`, and any source-set–derived
 *    equivalents created by user code or third-party plugins (per-source-set);
 *  - the KAPT extension if `org.jetbrains.kotlin.kapt` is applied (project-global; main
 *    source set's roots);
 *  - the KSP extension if `com.google.devtools.ksp` is applied (project-global; main
 *    source set's roots).
 *
 * KAPT/KSP detection is reflective so this configurator never takes a compile-time dependency
 * on the Kotlin or KSP classpaths — if a plugin isn't on the classpath, its `plugins.withId`
 * callback simply never fires.
 */
internal object CompilerArgsConfigurator {

    private const val SOURCES_OPTION = "flamingock.sources"
    private const val RESOURCES_OPTION = "flamingock.resources"

    private const val KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"
    private const val KSP_PLUGIN_ID = "com.google.devtools.ksp"

    fun configure(project: Project) {
        val sourceSets = project.extensions.findByType(SourceSetContainer::class.java) ?: return
        sourceSets.configureEach(Action<SourceSet> {
            val ss = this
            val sourceDirs = collectSourceDirs(ss)
            if (sourceDirs.isEmpty()) return@Action

            val sourcesArg = sourceDirs.joinToString(File.pathSeparator) { it.absolutePath }
            val resourcesArg = ss.resources.srcDirs.firstOrNull()?.absolutePath

            project.tasks.named(ss.compileJavaTaskName, JavaCompile::class.java)
                .configure(Action<JavaCompile> {
                    options.compilerArgs.add("-A$SOURCES_OPTION=$sourcesArg")
                    if (resourcesArg != null) {
                        options.compilerArgs.add("-A$RESOURCES_OPTION=$resourcesArg")
                    }
                })

            // KAPT/KSP only get configured for the MAIN source set since their option-passing
            // API is project-scoped — there is no per-source-set hook for arguments. Use
            // plugins.withId so the configuration kicks in regardless of plugin-application
            // order; if a plugin is never applied, the callback never fires.
            if (ss.name == SourceSet.MAIN_SOURCE_SET_NAME) {
                project.plugins.withId(KAPT_PLUGIN_ID) {
                    configureKapt(project, ss)
                }
                project.plugins.withId(KSP_PLUGIN_ID) {
                    configureKsp(project, ss)
                }
            }
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

    /**
     * Reflectively call `KaptExtension.arguments(action: KaptArguments.() -> Unit)` to add
     * `flamingock.sources` / `flamingock.resources`. The DSL is a Kotlin lambda receiver,
     * which compiles to a `Function1<KaptArguments, Unit>` parameter — NOT a Gradle `Action`.
     * KAPT also exposes a sibling `arguments(Closure)` overload for Groovy; we deliberately
     * skip that one. KAPT's `arg(name: Any, vararg values: Any)` takes a vararg, so the
     * reflected signature is `arg(Object, Object[])`.
     *
     * Visible to the same module so tests can invoke the dispatch path directly without
     * needing the real Kotlin/KAPT plugin classpath.
     */
    internal fun configureKapt(project: Project, ss: SourceSet) {
        val sourcesArg = sourcesArgFor(ss) ?: return
        val resourcesArg = ss.resources.srcDirs.firstOrNull()?.absolutePath

        val kaptExt = project.extensions.findByName("kapt") ?: return
        val argumentsMethod = kaptExt::class.java.methods.firstOrNull {
            it.name == "arguments"
                    && it.parameterCount == 1
                    && Function1::class.java.isAssignableFrom(it.parameterTypes[0])
        } ?: return

        val action: (Any) -> Unit = { argsObj ->
            invokeArg(argsObj, SOURCES_OPTION, sourcesArg)
            if (resourcesArg != null) invokeArg(argsObj, RESOURCES_OPTION, resourcesArg)
        }
        argumentsMethod.invoke(kaptExt, action)
    }

    /**
     * Reflectively call `KspExtension.arg(String, String)` for each option. Visible to the
     * same module so tests can invoke the dispatch path directly.
     */
    internal fun configureKsp(project: Project, ss: SourceSet) {
        val sourcesArg = sourcesArgFor(ss) ?: return
        val resourcesArg = ss.resources.srcDirs.firstOrNull()?.absolutePath

        val kspExt = project.extensions.findByName("ksp") ?: return
        invokeArg(kspExt, SOURCES_OPTION, sourcesArg)
        if (resourcesArg != null) invokeArg(kspExt, RESOURCES_OPTION, resourcesArg)
    }

    private fun sourcesArgFor(ss: SourceSet): String? {
        val sourceDirs = collectSourceDirs(ss)
        if (sourceDirs.isEmpty()) return null
        return sourceDirs.joinToString(File.pathSeparator) { it.absolutePath }
    }

    /**
     * Look up an `arg` method on [target] by signature. Supports KSP's `arg(String, String)`
     * and KAPT's `arg(Object, Object[])` vararg form. Falls back silently on incompatible
     * shapes — the caller treats this as best-effort.
     */
    private fun invokeArg(target: Any, name: String, value: String) {
        val cls = target::class.java
        val stringString = cls.methods.firstOrNull {
            it.name == "arg" && it.parameterCount == 2
                    && it.parameterTypes[0] == String::class.java
                    && it.parameterTypes[1] == String::class.java
        }
        if (stringString != null) {
            stringString.invoke(target, name, value)
            return
        }
        val vararg = cls.methods.firstOrNull {
            it.name == "arg" && it.parameterCount == 2
                    && it.parameterTypes[1].isArray
        }
        if (vararg != null) {
            vararg.invoke(target, name, arrayOf<Any>(value))
        }
    }
}
