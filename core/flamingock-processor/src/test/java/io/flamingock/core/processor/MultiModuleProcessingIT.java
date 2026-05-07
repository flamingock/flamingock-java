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

import com.fasterxml.jackson.databind.SerializationFeature;
import io.flamingock.core.processor.util.MetadataModuleIdentity;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.metadata.MetadataLoader;
import io.flamingock.internal.common.core.preview.AbstractPreviewChange;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.preview.SystemPreviewStage;
import io.flamingock.internal.common.core.preview.builder.CodePreviewChangeBuilder;
import io.flamingock.internal.util.JsonObjectMapper;
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
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    @Test
    @DisplayName("Same system change id from multiple modules is deduplicated in the composite")
    void sameSystemChangeAcrossModulesIsDeduplicated() throws Exception {
        // Two normal modules — each compiles cleanly with one default-stage @Change.
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

        // The @Change annotation has no `system` flag (system changes today are produced
        // only by AnnotationProcessorPlugins like MongockAnnotationProcessorPlugin), so
        // simulate the "every module ships the same system change" reality by patching
        // each generated metadata.json with an identical SystemPreviewStage.
        String sharedId = "system-change-shared";
        injectIdenticalSystemChange(modAOut, sharedId);
        injectIdenticalSystemChange(modBOut, sharedId);

        FlamingockMetadata composite = loadAggregatedAcross(modAOut, modBOut);

        assertNotNull(composite.getPipeline());
        PreviewStage compositeSystemStage = composite.getPipeline().getSystemStage();
        assertNotNull(compositeSystemStage,
                "Composite must surface a single merged system stage when modules carry one");

        Collection<? extends AbstractPreviewChange> systemChanges = compositeSystemStage.getChanges();
        assertNotNull(systemChanges);
        assertEquals(1, systemChanges.size(),
                "Identical system change ids across modules must be deduplicated to one entry");
        assertEquals(sharedId, systemChanges.iterator().next().getId());

        // Sanity: regular per-module changes still flow through aggregation.
        List<String> ids = changeIds(composite.getPipeline());
        assertTrue(ids.contains("id-A1"));
        assertTrue(ids.contains("id-B1"));
    }

    @Test
    @DisplayName("Filer-first detection finds the submodule when user.dir points elsewhere")
    void filerFirstDetectionLandsOnSubmoduleNotWorkingDirectory() throws Exception {
        // Realistic multi-module Gradle layout: per-submodule build.gradle.kts and the
        // standard src/main/java + build/classes/java/main pair. With Filer-first detection
        // the AP walks up from CLASS_OUTPUT and stops at the per-submodule marker; the YAML
        // template in src/main/java/<pkg>/changes is then scanned and lands in the metadata.
        // Crucially this test does NOT pass -Aflamingock.sources= so detection is exercised.
        Path moduleDir = Files.createDirectories(workDir.resolve("synthetic-submodule"));
        Files.write(moduleDir.resolve("build.gradle.kts"), "// per-submodule marker\n".getBytes());

        String pkg = "com.example.detect";
        Path srcRoot = Files.createDirectories(moduleDir.resolve("src/main/java"));
        Path pkgDir = Files.createDirectories(srcRoot.resolve(pkg.replace('.', '/') + "/changes"));
        // Java config class with @EnableFlamingock.
        Files.write(pkgDir.getParent().resolve("Config.java"), configClass(pkg + ".changes").getBytes());
        // YAML template that should be discovered via filesystem scan.
        Files.write(pkgDir.resolve("_0001__synthetic_template.yaml"),
                ("id: SyntheticTemplateChange\n"
                        + "transactional: false\n"
                        + "template: SqlTemplate\n"
                        + "targetSystem:\n"
                        + "  id: \"detect-target\"\n"
                        + "apply: \"SELECT 1;\"\n"
                        + "rollback: \"SELECT 1;\"\n").getBytes());

        Path classOutput = Files.createDirectories(moduleDir.resolve("build/classes/java/main"));
        runJavac(moduleDir, srcRoot, classOutput);

        FlamingockMetadata composite = loadAggregatedAcross(classOutput);
        List<String> changeIds = changeIds(composite.getPipeline());
        assertTrue(changeIds.contains("SyntheticTemplateChange"),
                "YAML template change must be present once detection lands on the submodule. Got: " + changeIds);
    }

    /**
     * In-process javac invocation for the realistic-layout test. Mirrors {@link #compileModule}
     * but takes explicit src/out paths so the test can place them under a synthetic submodule
     * directory tree, and deliberately omits {@code -Aflamingock.sources=} so source-root
     * detection runs.
     */
    private void runJavac(Path moduleDir, Path src, Path out) throws IOException {
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
                walk.filter(p -> p.toString().endsWith(".java"))
                        .forEach(p -> fm.getJavaFileObjectsFromFiles(Collections.singletonList(p.toFile()))
                                .forEach(javaSources::add));
            }

            StringWriter err = new StringWriter();
            JavaCompiler.CompilationTask task = compiler.getTask(
                    err, fm, null, Collections.emptyList(), null, javaSources);
            task.setProcessors(Collections.singletonList(new FlamingockAnnotationProcessor()));
            if (!task.call()) {
                throw new IllegalStateException("Compilation failed at " + moduleDir + ":\n" + err);
            }
        }
    }

    @Test
    @DisplayName("Multiple modules with the same legacy-stage name → one merged stage in the composite")
    void sameNameLegacyStagesAreMergedAcrossModules() throws Exception {
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

        // Both modules carry the same legacy stage name (today's Mongock pattern) with
        // disjoint change ids — should collapse to one stage carrying both.
        injectLegacyStage(modAOut, "flamingock-legacy-stage", Arrays.asList("legacy-A1", "legacy-A2"));
        injectLegacyStage(modBOut, "flamingock-legacy-stage", Arrays.asList("legacy-B1"));

        FlamingockMetadata composite = loadAggregatedAcross(modAOut, modBOut);

        List<PreviewStage> legacyStages = legacyStagesOf(composite.getPipeline());
        assertEquals(1, legacyStages.size(),
                "Same-named legacy stages from multiple modules must collapse to one");
        PreviewStage merged = legacyStages.get(0);
        assertEquals("flamingock-legacy-stage", merged.getName());
        assertEquals(3, merged.getChanges().size(),
                "Merged stage carries the union of legacy changes");
        List<String> mergedIds = merged.getChanges().stream()
                .map(AbstractPreviewChange::getId).collect(Collectors.toList());
        assertTrue(mergedIds.containsAll(Arrays.asList("legacy-A1", "legacy-A2", "legacy-B1")));

        // Sanity: the regular default-stage changes still flow through.
        List<String> ids = changeIds(composite.getPipeline());
        assertTrue(ids.contains("id-A1"));
        assertTrue(ids.contains("id-B1"));
    }

    @Test
    @DisplayName("Multiple modules with differently-named legacy stages → distinct stages preserved")
    void differentlyNamedLegacyStagesArePreservedAsDistinctStages() throws Exception {
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

        injectLegacyStage(modAOut, "flamingock-legacy-stage",
                Arrays.asList("mongock-A1", "mongock-A2"));
        injectLegacyStage(modBOut, "flamingock-liquibase-legacy-stage",
                Arrays.asList("liquibase-B1"));

        FlamingockMetadata composite = loadAggregatedAcross(modAOut, modBOut);

        List<PreviewStage> legacyStages = legacyStagesOf(composite.getPipeline());
        assertEquals(2, legacyStages.size(),
                "Differently-named legacy stages must remain as distinct peers");
        List<String> legacyNames = legacyStages.stream()
                .map(PreviewStage::getName).collect(Collectors.toList());
        assertTrue(legacyNames.contains("flamingock-legacy-stage"));
        assertTrue(legacyNames.contains("flamingock-liquibase-legacy-stage"));

        PreviewStage mongock = legacyStages.stream()
                .filter(s -> "flamingock-legacy-stage".equals(s.getName())).findFirst().get();
        PreviewStage liquibase = legacyStages.stream()
                .filter(s -> "flamingock-liquibase-legacy-stage".equals(s.getName())).findFirst().get();
        assertEquals(2, mongock.getChanges().size());
        assertEquals(1, liquibase.getChanges().size());
    }

    @Test
    @DisplayName("Mix of same-named and differently-named legacy stages → group-by-name merge")
    void legacyStagesGroupByName() throws Exception {
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

        Path modCOut = compileModule("modC",
                file("com/example/modC/Config.java",
                        configClass("com.example.modC.changes")),
                file("com/example/modC/changes/_0001__ChangeC.java",
                        changeClass("_0001__ChangeC", "id-C1", "com.example.modC.changes")));

        // modA + modB share the Mongock legacy stage name with overlapping ids;
        // modC has a differently-named legacy stage that must stay separate.
        injectLegacyStage(modAOut, "flamingock-legacy-stage",
                Arrays.asList("mongock-shared", "mongock-A-only"));
        injectLegacyStage(modBOut, "flamingock-legacy-stage",
                Arrays.asList("mongock-shared", "mongock-B-only"));
        injectLegacyStage(modCOut, "flamingock-liquibase-legacy-stage",
                Arrays.asList("liquibase-C1"));

        FlamingockMetadata composite = loadAggregatedAcross(modAOut, modBOut, modCOut);

        List<PreviewStage> legacyStages = legacyStagesOf(composite.getPipeline());
        assertEquals(2, legacyStages.size(),
                "Output is one stage per distinct legacy-stage name");

        PreviewStage mongock = legacyStages.stream()
                .filter(s -> "flamingock-legacy-stage".equals(s.getName())).findFirst().get();
        List<String> mongockIds = mongock.getChanges().stream()
                .map(AbstractPreviewChange::getId).collect(Collectors.toList());
        assertEquals(3, mongockIds.size(), "id-deduped union of modA + modB Mongock changes");
        assertTrue(mongockIds.containsAll(
                Arrays.asList("mongock-shared", "mongock-A-only", "mongock-B-only")));

        PreviewStage liquibase = legacyStages.stream()
                .filter(s -> "flamingock-liquibase-legacy-stage".equals(s.getName())).findFirst().get();
        assertEquals(1, liquibase.getChanges().size());
        assertEquals("liquibase-C1", liquibase.getChanges().iterator().next().getId());
    }

    /**
     * Locate the module's generated metadata.json, parse it, append a {@link PreviewStage}
     * of type {@link io.flamingock.api.StageType#LEGACY} carrying one stub change per supplied
     * id, and persist back. Mirrors {@link #injectIdenticalSystemChange} but for the legacy
     * branch of the aggregator.
     */
    private static void injectLegacyStage(Path classOutput, String stageName, List<String> changeIds) throws IOException {
        Optional<MetadataModuleIdentity> identity =
                MetadataModuleIdentity.discoverFromClassOutput(classOutput);
        assertTrue(identity.isPresent(),
                "Compiled module should have produced an SPI registration: " + classOutput);
        Path metadataFile = classOutput.resolve(identity.get().getMetadataResourcePath());

        FlamingockMetadata metadata;
        try (InputStream in = Files.newInputStream(metadataFile)) {
            metadata = JsonObjectMapper.DEFAULT_INSTANCE.readValue(in, FlamingockMetadata.class);
        }

        List<CodePreviewChange> changes = new ArrayList<>();
        for (String id : changeIds) {
            changes.add(CodePreviewChangeBuilder.instance()
                    .setId(id)
                    .setOrder("01000")
                    .setAuthor("test-legacy")
                    .setSourceClassPath("io.flamingock.test.LegacyChangeStub_" + id)
                    .setConstructor(PreviewConstructor.getDefault())
                    .setApplyMethod(new PreviewMethod("apply", Collections.emptyList()))
                    .setRecovery(RecoveryDescriptor.getDefault())
                    .setTransactionalFlag(true)
                    .build());
        }

        PreviewStage legacyStage = new PreviewStage(
                stageName,
                io.flamingock.api.StageType.LEGACY,
                "Synthetic legacy stage for tests",
                null, null,
                changes);

        Collection<PreviewStage> existing = metadata.getPipeline().getStages();
        List<PreviewStage> updated = new ArrayList<>();
        if (existing != null) updated.addAll(existing);
        updated.add(legacyStage);
        metadata.getPipeline().setStages(updated);

        try (Writer out = Files.newBufferedWriter(metadataFile)) {
            out.write(JsonObjectMapper.DEFAULT_INSTANCE
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(metadata));
        }
    }

    /** All stages typed as LEGACY in the composite, in their pipeline order. */
    private static List<PreviewStage> legacyStagesOf(PreviewPipeline pipeline) {
        if (pipeline == null || pipeline.getStages() == null) return Collections.emptyList();
        return pipeline.getStages().stream()
                .filter(s -> s.getType() == io.flamingock.api.StageType.LEGACY)
                .collect(Collectors.toList());
    }

    /**
     * Locate the module's generated metadata.json (the suffix lives in the SPI registration
     * file the AP just wrote), parse it, install a {@link SystemPreviewStage} that holds one
     * synthetic system change with id {@code sharedId}, and persist back. Mimics the per-module
     * output that an {@code AnnotationProcessorPlugin} like the Mongock importer plugin would
     * have produced — minus the dependency on that plugin.
     */
    private static void injectIdenticalSystemChange(Path classOutput, String sharedId) throws IOException {
        Optional<MetadataModuleIdentity> identity =
                MetadataModuleIdentity.discoverFromClassOutput(classOutput);
        assertTrue(identity.isPresent(),
                "Compiled module should have produced an SPI registration: " + classOutput);
        Path metadataFile = classOutput.resolve(identity.get().getMetadataResourcePath());

        FlamingockMetadata metadata;
        try (InputStream in = Files.newInputStream(metadataFile)) {
            metadata = JsonObjectMapper.DEFAULT_INSTANCE.readValue(in, FlamingockMetadata.class);
        }

        CodePreviewChange systemChange = CodePreviewChangeBuilder.instance()
                .setId(sharedId)
                .setOrder("00100")
                .setAuthor("test-system")
                .setSourceClassPath("io.flamingock.test.SystemChangeStub")
                .setConstructor(PreviewConstructor.getDefault())
                .setApplyMethod(new PreviewMethod("apply", Collections.emptyList()))
                .setSystem(true)
                .setRecovery(RecoveryDescriptor.getDefault())
                .setTransactionalFlag(true)
                .build();

        SystemPreviewStage systemStage = new SystemPreviewStage(
                "flamingock-system-stage",
                "Dedicated stage for system-level changes",
                null, null,
                Collections.singletonList(systemChange));

        metadata.getPipeline().setSystemStage(systemStage);

        try (Writer out = Files.newBufferedWriter(metadataFile)) {
            out.write(JsonObjectMapper.DEFAULT_INSTANCE
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(metadata));
        }
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
                    "-Aflamingock.sources=" + src.toAbsolutePath(),
                    "-Aflamingock.resources=" + src.toAbsolutePath());
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
