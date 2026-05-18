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

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Unit tests for [CompilerArgsConfigurator] using Gradle's `ProjectBuilder` test fixture.
 * Validates that the configurator translates the `main` and `test` source sets'
 * source/resource directories into `-Aflamingock.sources=` and `-Aflamingock.resources=`
 * compiler arguments on the corresponding `JavaCompile` tasks.
 */
class CompilerArgsConfiguratorTest {

    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `java-only project emits sources and resources args on compileJava`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        CompilerArgsConfigurator.configure(project)

        val args = compilerArgsOf(project, "compileJava")
        val sourcesArg = sourcesArg(args)
        val resourcesArg = resourcesArg(args)

        assertNotNull(sourcesArg, "compileJava must carry -Aflamingock.sources=. Args: $args")
        assertEquals(setOf(srcDir(project, "main", "java")),
                splitPathSeparator(sourcesArg!!))
        assertEquals(srcDir(project, "main", "resources"), resourcesArg)
    }

    @Test
    fun `kotlin source dir is included alongside java when kotlin extension is present`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        // Synthesize a Kotlin SourceDirectorySet on the main source set without applying
        // the Kotlin plugin (which would pull a heavy classpath into the unit test). This
        // exercises the same SourceSet ExtensionAware lookup the configurator does.
        val mainSet = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
        val factory = project.objects.javaClass.getMethod(
                "sourceDirectorySet", String::class.java, String::class.java
        )
        val kotlinSet = factory.invoke(project.objects, "kotlin", "Kotlin source")
                as org.gradle.api.file.SourceDirectorySet
        kotlinSet.srcDir(project.file("src/main/kotlin"))
        (mainSet as org.gradle.api.plugins.ExtensionAware).extensions
                .add(org.gradle.api.file.SourceDirectorySet::class.java, "kotlin", kotlinSet)

        CompilerArgsConfigurator.configure(project)

        val sourcesArg = sourcesArg(compilerArgsOf(project, "compileJava"))
        assertNotNull(sourcesArg)
        val parts = splitPathSeparator(sourcesArg!!)
        assertTrue(parts.contains(srcDir(project, "main", "java")),
                "Java src must be included; got $parts")
        assertTrue(parts.any { it.endsWith("src${File.separator}main${File.separator}kotlin") },
                "Kotlin src must be included; got $parts")
    }

    @Test
    fun `custom java source dir is included in sources arg`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        sourceSets.getByName("main").java.srcDirs(project.file("src/extra"))

        CompilerArgsConfigurator.configure(project)

        val sourcesArg = sourcesArg(compilerArgsOf(project, "compileJava"))!!
        val parts = splitPathSeparator(sourcesArg)
        assertTrue(parts.any { it.endsWith("src${File.separator}extra") },
                "Custom src/extra dir must be included; got $parts")
        assertTrue(parts.contains(srcDir(project, "main", "java")),
                "Default src/main/java must still be included; got $parts")
    }

    @Test
    fun `custom resources dir is reflected in resources arg`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        // Replace the default resources srcDirs with a single custom one.
        sourceSets.getByName("main").resources.setSrcDirs(listOf(project.file("custom-resources")))

        CompilerArgsConfigurator.configure(project)

        val resourcesArg = resourcesArg(compilerArgsOf(project, "compileJava"))
        assertNotNull(resourcesArg)
        assertTrue(resourcesArg!!.endsWith("custom-resources"),
                "Resources arg must reflect custom dir; got $resourcesArg")
    }

    @Test
    fun `configureKapt reflectively pushes args into KaptExtension-shaped object`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        val kapt = StubKaptExtension()
        project.extensions.add(StubKaptExtension::class.java, "kapt", kapt)

        val main = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
        CompilerArgsConfigurator.configureKapt(project, main)

        assertEquals(srcDir(project, "main", "java"),
                kapt.arguments.collected["flamingock.sources"])
        assertEquals(srcDir(project, "main", "resources"),
                kapt.arguments.collected["flamingock.resources"])
    }

    @Test
    fun `configureKsp reflectively pushes args into KspExtension-shaped object`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        val ksp = StubKspExtension()
        project.extensions.add(StubKspExtension::class.java, "ksp", ksp)

        val main = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
        CompilerArgsConfigurator.configureKsp(project, main)

        assertEquals(srcDir(project, "main", "java"), ksp.collected["flamingock.sources"])
        assertEquals(srcDir(project, "main", "resources"), ksp.collected["flamingock.resources"])
    }

    @Test
    fun `KAPT and KSP no-op when their plugins are never applied`() {
        // The full configure() path registers withId callbacks for kapt and ksp; without
        // those plugins ever applied, the callbacks don't fire and the stub extensions
        // stay untouched. This test verifies that wiring at the public API surface.
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        val kapt = StubKaptExtension()
        val ksp = StubKspExtension()
        project.extensions.add(StubKaptExtension::class.java, "kapt", kapt)
        project.extensions.add(StubKspExtension::class.java, "ksp", ksp)

        CompilerArgsConfigurator.configure(project)
        // Intentionally do NOT apply the kapt/ksp plugins.

        assertTrue(kapt.arguments.collected.isEmpty(),
                "KAPT must remain untouched; got ${kapt.arguments.collected}")
        assertTrue(ksp.collected.isEmpty(),
                "KSP must remain untouched; got ${ksp.collected}")
    }

    @Test
    fun `compileJava and compileTestJava receive their own source-set's args`() {
        val project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.plugins.apply("java")

        CompilerArgsConfigurator.configure(project)

        val mainSources = sourcesArg(compilerArgsOf(project, "compileJava"))!!
        val testSources = sourcesArg(compilerArgsOf(project, "compileTestJava"))!!

        assertTrue(mainSources.contains("main${File.separator}java"),
                "compileJava must reference main sources; got $mainSources")
        assertFalse(mainSources.contains("test${File.separator}java"),
                "compileJava must not reference test sources; got $mainSources")
        assertTrue(testSources.contains("test${File.separator}java"),
                "compileTestJava must reference test sources; got $testSources")
    }

    private fun compilerArgsOf(project: org.gradle.api.Project, taskName: String): List<String> {
        val task = project.tasks.named(taskName, JavaCompile::class.java).get()
        return task.options.compilerArgs.toList()
    }

    private fun sourcesArg(args: List<String>): String? =
            args.firstOrNull { it.startsWith("-Aflamingock.sources=") }
                    ?.removePrefix("-Aflamingock.sources=")

    private fun resourcesArg(args: List<String>): String? =
            args.firstOrNull { it.startsWith("-Aflamingock.resources=") }
                    ?.removePrefix("-Aflamingock.resources=")

    private fun splitPathSeparator(value: String): Set<String> =
            value.split(File.pathSeparator).filter { it.isNotEmpty() }.toSet()

    private fun srcDir(project: org.gradle.api.Project, sourceSet: String, kind: String): String =
            project.file("src/$sourceSet/$kind").absolutePath

    /**
     * Mirrors the shape of `KaptAnnotationProcessorOptions` enough for our reflective
     * lookup: an `arg(name: Any, vararg values: Any)` method that records into a map.
     */
    open class StubKaptArguments {
        val collected = mutableMapOf<String, String>()
        fun arg(name: Any, vararg values: Any) {
            collected[name.toString()] = values.joinToString(" ") { it.toString() }
        }
    }

    /**
     * Mirrors the shape of `KaptExtension` enough for our reflective lookup: an
     * `arguments(action: StubKaptArguments.() -> Unit)` method. The Kotlin lambda receiver
     * compiles to a `Function1<StubKaptArguments, Unit>` parameter, mirroring the real
     * `KaptExtension.arguments` JVM signature so the production reflective dispatch is
     * exercised the same way as it would be against the real Kotlin plugin classpath.
     */
    open class StubKaptExtension {
        val arguments = StubKaptArguments()
        fun arguments(action: StubKaptArguments.() -> Unit) {
            arguments.action()
        }
    }

    /**
     * Mirrors the shape of `KspExtension` enough for our reflective lookup: an
     * `arg(String, String)` method.
     */
    open class StubKspExtension {
        val collected = mutableMapOf<String, String>()
        fun arg(k: String, v: String) {
            collected[k] = v
        }
    }
}
