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
package io.flamingock.core.processor;

import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.metadata.MetadataLoader;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end multi-module integration tests.
 *
 * <p>Compiles two simulated modules ({@code modA}, {@code modB}) independently using the
 * existing in-process javac harness, then assembles a synthetic classpath that contains both
 * modules' generated artefacts and exercises the runtime aggregation in
 * {@link MetadataLoader#loadAggregated()}.
 *
 * <p>Each test case is self-contained: sources are written into a temp dir, compiled with
 * {@code -proc:only}, and the resulting CLASS_OUTPUT directories are loaded via a
 * {@link URLClassLoader} bridged into the thread context so {@link java.util.ServiceLoader}
 * picks up both modules' SPI providers.
 */
class MultiModuleProcessingIT {

    @TempDir
    Path workDir;

    @Test
    @DisplayName("Two modules with disjoint stages aggregate into one composite pipeline")
    void twoModulesAggregateIntoComposite() throws Exception {
        Path modAOut = compileModule("modA",
                file("com/example/modA/Config.java",
                        configClass("com.example.modA.changes")),
                file("com/example/modA/changes/_0001__ChangeA.java",
                        changeClass("_0001__ChangeA", "id-A1", "com.example.modA.changes")));

        Path modBOut = compileModule("modB",
                file("com/example/modB/Config.java",
                        configClass("com.example.modB.changes")),
                file("com/example/modB/changes/_0001__ChangeB.java",
                        changeClass("_0001__ChangeB", "id-B1", "com.example.modB.changes")));

        FlamingockMetadata composite = loadAggregatedAcross(modAOut, modBOut);

        assertNotNull(composite.getPipeline());
        List<String> stageNames = stageNames(composite.getPipeline());
        assertTrue(stageNames.contains("changes"),
                "the 'changes' stage from at least one module should appear: " + stageNames);
        assertEquals(2, totalChanges(composite.getPipeline()),
                "composite contains both modules' changes");
        List<String> ids = changeIds(composite.getPipeline());
        assertTrue(ids.contains("id-A1"));
        assertTrue(ids.contains("id-B1"));

        // Composite resets these — they are runtime-irrelevant after per-module validation.
        assertNull(composite.getOrphanChanges());
        assertEquals(false, composite.isStrictStageMapping());
        assertNull(composite.getPipelineFile());
    }

    @Test
    @DisplayName("Multiple @FlamingockCliBuilder methods across modules → fail at loadAggregated")
    void multipleBuilderProvidersAcrossModulesFail() throws Exception {
        Path modAOut = compileModule("modA",
                file("com/example/modA/Config.java",
                        configClass("com.example.modA.changes")),
                file("com/example/modA/changes/_0001__ChangeA.java",
                        changeClass("_0001__ChangeA", "id-A1", "com.example.modA.changes")),
                file("com/example/modA/builder/AppBuilderA.java", builderClass("AppBuilderA", "modA")));

        Path modBOut = compileModule("modB",
                file("com/example/modB/Config.java",
                        configClass("com.example.modB.changes")),
                file("com/example/modB/changes/_0001__ChangeB.java",
                        changeClass("_0001__ChangeB", "id-B1", "com.example.modB.changes")),
                file("com/example/modB/builder/AppBuilderB.java", builderClass("AppBuilderB", "modB")));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loadAggregatedAcross(modAOut, modBOut));
        assertTrue(ex.getMessage().contains("Multiple @FlamingockCliBuilder"),
                "error must reference multiple builders; got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Per-module strict-stage-mapping fails only the module whose orphans remain")
    void perModuleStrictMappingIsIndependent() throws Exception {
        // modA: strict=true and a @Change in a sub-package no stage covers → orphan → must throw.
        Path modAOut = compileModule("modA",
                file("com/example/modA/Config.java",
                        strictConfigClass("com.example.modA.coveredStage")),
                file("com/example/modA/orphan/_0001__OrphanedChange.java",
                        changeClass("_0001__OrphanedChange", "id-orphan", "com.example.modA.orphan")));

        Path modBOut = compileModule("modB",
                file("com/example/modB/Config.java",
                        configClass("com.example.modB.changes")),
                file("com/example/modB/changes/_0001__ChangeB.java",
                        changeClass("_0001__ChangeB", "id-B1", "com.example.modB.changes")));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loadAggregatedAcross(modAOut, modBOut));
        assertTrue(ex.getMessage().contains("strictStageMapping")
                        || ex.getMessage().contains("not mapped to any stage"),
                "error must reference strict-mode orphan failure; got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("id-orphan"),
                "error must mention the orphan id; got: " + ex.getMessage());
    }

    // ============================================================================
    // Compilation harness — one module per call. Each call uses a fresh javac
    // invocation with its own CLASS_OUTPUT and SOURCE_PATH so the per-module SPI
    // suffix is isolated.
    // ============================================================================

    private Path compileModule(String moduleName, SourceFile... sourceFiles) throws IOException {
        Path src = Files.createDirectories(workDir.resolve(moduleName + "-src"));
        for (SourceFile sf : sourceFiles) {
            Path file = src.resolve(sf.relativePath);
            Files.createDirectories(file.getParent());
            Files.write(file, sf.content.getBytes());
        }

        Path out = Files.createDirectories(workDir.resolve(moduleName + "-out"));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler. Tests must run on a JDK.");
        }
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(out.toFile()));
            fm.setLocation(StandardLocation.SOURCE_PATH, Collections.singletonList(src.toFile()));
            fm.setLocation(StandardLocation.CLASS_PATH, currentClasspath());

            List<JavaFileObject> javaSources = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(src)) {
                List<Path> files = walk.filter(p -> p.toString().endsWith(".java"))
                        .collect(Collectors.toList());
                for (Path p : files) {
                    fm.getJavaFileObjectsFromFiles(Collections.singletonList(p.toFile()))
                            .forEach(javaSources::add);
                }
            }

            StringWriter err = new StringWriter();
            // No -proc:only here: we need the generated provider class compiled too so
            // ServiceLoader can find it on the synthetic runtime classpath built later.
            List<String> options = Arrays.asList(
                    "-Asources=" + src.toAbsolutePath(),
                    "-Aresources=" + src.toAbsolutePath());
            JavaCompiler.CompilationTask task = compiler.getTask(
                    err, fm, null, options, null, javaSources);
            task.setProcessors(Collections.singletonList(new FlamingockAnnotationProcessor()));
            boolean ok = task.call();
            if (!ok) {
                throw new IllegalStateException(
                        "Module " + moduleName + " compilation failed:\n" + err);
            }
        }
        return out;
    }

    /**
     * Run {@link MetadataLoader#loadAggregated()} against a synthetic classpath that
     * contains the supplied module CLASS_OUTPUT directories. Restores the previous TCCL
     * after the call so test isolation is preserved.
     */
    private static FlamingockMetadata loadAggregatedAcross(Path... moduleOutputs) throws Exception {
        URL[] urls = new URL[moduleOutputs.length];
        for (int i = 0; i < moduleOutputs.length; i++) {
            urls[i] = moduleOutputs[i].toUri().toURL();
        }
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader composite = new URLClassLoader(urls, original)) {
            Thread.currentThread().setContextClassLoader(composite);
            return MetadataLoader.loadAggregated();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static List<File> currentClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(File::new)
                .collect(Collectors.toList());
    }

    // ============================================================================
    // Inspection helpers
    // ============================================================================

    private static int totalChanges(PreviewPipeline pipeline) {
        if (pipeline == null) return 0;
        int total = 0;
        if (pipeline.getSystemStage() != null && pipeline.getSystemStage().getChanges() != null) {
            total += pipeline.getSystemStage().getChanges().size();
        }
        if (pipeline.getStages() != null) {
            for (PreviewStage s : pipeline.getStages()) {
                if (s.getChanges() != null) total += s.getChanges().size();
            }
        }
        return total;
    }

    private static List<String> changeIds(PreviewPipeline pipeline) {
        List<String> ids = new ArrayList<>();
        if (pipeline == null) return ids;
        if (pipeline.getSystemStage() != null && pipeline.getSystemStage().getChanges() != null) {
            pipeline.getSystemStage().getChanges().forEach(c -> ids.add(c.getId()));
        }
        if (pipeline.getStages() != null) {
            for (PreviewStage s : pipeline.getStages()) {
                if (s.getChanges() != null) s.getChanges().forEach(c -> ids.add(c.getId()));
            }
        }
        return ids;
    }

    private static List<String> stageNames(PreviewPipeline pipeline) {
        List<String> names = new ArrayList<>();
        if (pipeline.getSystemStage() != null) names.add(pipeline.getSystemStage().getName());
        if (pipeline.getStages() != null) {
            pipeline.getStages().forEach(s -> names.add(s.getName()));
        }
        return names;
    }

    // ============================================================================
    // Source generators
    // ============================================================================

    private static SourceFile file(String relativePath, String content) {
        return new SourceFile(relativePath, content);
    }

    private static String configClass(String stageLocation) {
        // Derive a unique class name + simple package from the stage location to avoid
        // accidental same-FQN config classes across modules.
        String pkg = stageLocation.replace(".changes", "").replace(".coveredStage", "");
        return "package " + pkg + ";\n"
                + "import io.flamingock.api.annotations.EnableFlamingock;\n"
                + "import io.flamingock.api.annotations.Stage;\n"
                + "@EnableFlamingock(stages = @Stage(location = \"" + stageLocation + "\"))\n"
                + "public class Config {}\n";
    }

    private static String strictConfigClass(String stageLocation) {
        String pkg = stageLocation.replace(".coveredStage", "");
        return "package " + pkg + ";\n"
                + "import io.flamingock.api.annotations.EnableFlamingock;\n"
                + "import io.flamingock.api.annotations.Stage;\n"
                + "@EnableFlamingock(stages = @Stage(location = \"" + stageLocation + "\"),\n"
                + "                  strictStageMapping = true)\n"
                + "public class Config {}\n";
    }

    private static String changeClass(String simpleName, String id, String pkg) {
        return "package " + pkg + ";\n"
                + "import io.flamingock.api.annotations.Change;\n"
                + "import io.flamingock.api.annotations.Apply;\n"
                + "@Change(id = \"" + id + "\", author = \"test\")\n"
                + "public class " + simpleName + " {\n"
                + "    @Apply public void apply() {}\n"
                + "}\n";
    }

    private static String builderClass(String simpleClassName, String moduleTag) {
        // Return type is Object — the processor's return-type validator skips when
        // AbstractChangeRunnerBuilder is not on its compile classpath, which is true here
        // (flamingock-core is not a dependency of flamingock-processor at compile time).
        return "package com.example." + moduleTag + ".builder;\n"
                + "import io.flamingock.api.annotations.FlamingockCliBuilder;\n"
                + "public class " + simpleClassName + " {\n"
                + "    @FlamingockCliBuilder\n"
                + "    public static Object create() { return null; }\n"
                + "}\n";
    }

    /** Pair of (relative path under src, contents) — small POJO to keep test setup readable. */
    private static final class SourceFile {
        final String relativePath;
        final String content;
        SourceFile(String relativePath, String content) {
            this.relativePath = relativePath;
            this.content = content;
        }
    }
}
