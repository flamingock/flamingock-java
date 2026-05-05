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

import io.flamingock.core.processor.util.MetadataModuleIdentity;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.AbstractPreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.util.JsonObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration tests that drive {@link FlamingockAnnotationProcessor} through real
 * javac invocations to validate incremental-build behavior.
 *
 * <p>Each test sets up a temporary source tree, runs javac in-process via
 * {@link ToolProvider#getSystemJavaCompiler()} with our processor, and inspects the resulting
 * {@code META-INF/flamingock/metadata.json} against expectations.
 */
class IncrementalProcessingIT {

    @TempDir
    Path workDir;

    // No global state to reset — each compile() instantiates a fresh FlamingockAnnotationProcessor.

    @Test
    @DisplayName("Incremental round with only a @Change preserves previously-discovered changes")
    void incrementalChangeOnlyPreservesExistingMetadata() throws Exception {
        Path src = Files.createDirectories(workDir.resolve("src"));
        writeFile(src, "com/example/Config.java", configClass());
        writeFile(src, "com/example/changes/_0001__ChangeOne.java", changeClass("_0001__ChangeOne", "id-1"));
        writeFile(src, "com/example/changes/_0002__ChangeTwo.java", changeClass("_0002__ChangeTwo", "id-2"));

        // ROUND 1: full compile, all sources visible.
        Path round1Out = Files.createDirectories(workDir.resolve("out1"));
        boolean ok1 = compile(src, round1Out, allJavaFilesIn(src));
        assertTrue(ok1, "round 1 must succeed");

        Path metadataR1 = metadataPathIn(round1Out);
        assertTrue(Files.exists(metadataR1), "round 1 must produce metadata.json");
        FlamingockMetadata afterR1 = readMetadata(metadataR1);
        assertEquals(2, totalChanges(afterR1.getPipeline()),
                "round 1 must record both changes");

        // ROUND 2: simulate incremental compile of only ChangeOne. Pre-seed CLASS_OUTPUT
        // with round 1's META-INF (SPI file + metadata) so the processor finds the persisted
        // suffix and reads its metadata via Filer.getResource(...).
        resetProcessorState();
        Path round2Out = Files.createDirectories(workDir.resolve("out2"));
        seedFromPreviousRound(round1Out, round2Out);

        Path changeOneOnly = src.resolve("com/example/changes/_0001__ChangeOne.java");
        boolean ok2 = compile(src, round2Out, Collections.singletonList(changeOneOnly));
        assertTrue(ok2, "round 2 must succeed even without @EnableFlamingock in the round");

        FlamingockMetadata afterR2 = readMetadata(metadataPathIn(round2Out));
        assertEquals(2, totalChanges(afterR2.getPipeline()),
                "round 2 must preserve previously-known change id-2");
        List<String> ids = changeIds(afterR2.getPipeline());
        assertTrue(ids.contains("id-1"), "id-1 (recompiled) present");
        assertTrue(ids.contains("id-2"), "id-2 (preserved) present");
    }

    @Test
    @DisplayName("Round with no relevant elements does not create metadata file and does not fail")
    void roundWithNothingRelevantIsNoop() throws Exception {
        Path src = Files.createDirectories(workDir.resolve("src"));
        writeFile(src, "com/example/Plain.java",
                "package com.example;\npublic class Plain {}\n");

        Path out = Files.createDirectories(workDir.resolve("out"));
        boolean ok = compile(src, out, allJavaFilesIn(src));
        assertTrue(ok, "compilation must succeed");
        assertFalse(metadataWasGenerated(out),
                "no metadata file (and no SPI registration) should be created "
                        + "when nothing relevant is processed");
    }

    @Test
    @DisplayName("Round with a @Change but no existing metadata parks it as orphan (no failure)")
    void changeWithoutExistingMetadataIsHeldAsOrphan() throws Exception {
        Path src = Files.createDirectories(workDir.resolve("src"));
        writeFile(src, "com/example/changes/_0001__ChangeOnly.java",
                changeClass("_0001__ChangeOnly", "id-x"));

        Path out = Files.createDirectories(workDir.resolve("out"));
        boolean ok = compile(src, out, allJavaFilesIn(src));
        assertTrue(ok, "must succeed: missing @EnableFlamingock no longer fails the build");

        Path metadataPath = metadataPathIn(out);
        assertNotNull(metadataPath, "SPI file expected for an orphan-only round");
        assertTrue(Files.exists(metadataPath), "metadata file is written with the change as orphan");
        FlamingockMetadata metadata = readMetadata(metadataPath);
        assertNull(metadata.getPipeline(), "no pipeline yet — only orphans");
        assertNotNull(metadata.getOrphanChanges());
        assertEquals(1, metadata.getOrphanChanges().size());
        assertEquals("id-x", metadata.getOrphanChanges().get(0).getId());
        assertFalse(metadata.isStrictStageMapping(),
                "strictStageMapping defaults to false until @EnableFlamingock sets it");
    }

    @Test
    @DisplayName("Orphan from round 1 is rehomed when round 2 introduces a covering @EnableFlamingock stage")
    void orphanFromRound1IsRehomedByRound2() throws Exception {
        Path src = Files.createDirectories(workDir.resolve("src"));
        writeFile(src, "com/example/changes/_0001__ChangeOnly.java",
                changeClass("_0001__ChangeOnly", "id-x"));

        // Round 1: only the @Change visible. Should produce orphans-only metadata.
        Path round1Out = Files.createDirectories(workDir.resolve("out1"));
        Path changeSrc = src.resolve("com/example/changes/_0001__ChangeOnly.java");
        boolean ok1 = compile(src, round1Out, Collections.singletonList(changeSrc));
        assertTrue(ok1);
        FlamingockMetadata r1 = readMetadata(metadataPathIn(round1Out));
        assertEquals(1, r1.getOrphanChanges().size());

        // Round 2: introduce Config.java with covering stage; only Config + previous metadata.
        writeFile(src, "com/example/Config.java", configClass());
        resetProcessorState();
        Path round2Out = Files.createDirectories(workDir.resolve("out2"));
        seedFromPreviousRound(round1Out, round2Out);

        Path configSrc = src.resolve("com/example/Config.java");
        boolean ok2 = compile(src, round2Out, Collections.singletonList(configSrc));
        assertTrue(ok2);

        FlamingockMetadata r2 = readMetadata(metadataPathIn(round2Out));
        assertNotNull(r2.getPipeline(), "round 2 builds the pipeline");
        assertTrue(r2.getOrphanChanges() == null || r2.getOrphanChanges().isEmpty(),
                "orphan should have been rehomed into the new stage");
        assertEquals(1, totalChanges(r2.getPipeline()));
        assertTrue(changeIds(r2.getPipeline()).contains("id-x"));
    }

    @Test
    @DisplayName("F1: last round prunes a @Change whose source file was deleted")
    void lastRoundPrunesDeletedChange() throws Exception {
        // Round 1: full build with two changes.
        Path src = Files.createDirectories(workDir.resolve("src"));
        writeFile(src, "com/example/Config.java", configClass());
        writeFile(src, "com/example/changes/_0001__Kept.java",
                changeClass("_0001__Kept", "id-keep"));
        writeFile(src, "com/example/changes/_0002__Deleted.java",
                changeClass("_0002__Deleted", "id-del"));
        Path deletedSrc = src.resolve("com/example/changes/_0002__Deleted.java");

        Path round1Out = Files.createDirectories(workDir.resolve("out1"));
        boolean ok1 = compile(src, round1Out, allJavaFilesIn(src));
        assertTrue(ok1);
        FlamingockMetadata r1 = readMetadata(metadataPathIn(round1Out));
        assertEquals(2, totalChanges(r1.getPipeline()));

        // Round 2: simulate deletion — remove the source file. Recompile remaining sources +
        // seed previous metadata. Last-round pruning should drop id-del.
        Files.delete(deletedSrc);
        Path round2Out = Files.createDirectories(workDir.resolve("out2"));
        seedFromPreviousRound(round1Out, round2Out);

        boolean ok2 = compile(src, round2Out, allJavaFilesIn(src));
        assertTrue(ok2);

        FlamingockMetadata r2 = readMetadata(metadataPathIn(round2Out));
        assertEquals(1, totalChanges(r2.getPipeline()),
                "deleted @Change should be pruned at the last round");
        assertTrue(changeIds(r2.getPipeline()).contains("id-keep"));
        assertFalse(changeIds(r2.getPipeline()).contains("id-del"));
    }

    @Test
    @DisplayName("F1: prune predicate handles nested-class binary names (with $)")
    void pruneHandlesNestedClassBinaryNames() throws Exception {
        // Regression: Elements.getTypeElement requires the canonical name (dot-separated)
        // but CodePreviewChange.getSource() stores the binary name (dollar-separated for
        // nested classes). Without the binary→canonical conversion, the prune lookup wrongly
        // returns null for nested classes and removes them as if deleted.
        Path src = Files.createDirectories(workDir.resolve("src"));
        writeFile(src, "com/example/Config.java", configClass());
        writeFile(src, "com/example/changes/_0001__Outer.java", outerWithNestedChange());

        Path out = Files.createDirectories(workDir.resolve("out"));
        boolean ok = compile(src, out, allJavaFilesIn(src));
        assertTrue(ok);

        FlamingockMetadata md = readMetadata(metadataPathIn(out));
        // Without the fix, the prune step would wrongly drop the nested change here.
        assertEquals(1, totalChanges(md.getPipeline()),
                "nested @Change must NOT be pruned (binary name with $ converts to canonical for getTypeElement)");
        assertTrue(changeIds(md.getPipeline()).contains("id-nested"));
        // Confirm the persisted source carries the binary form, so the conversion is exercised.
        String source = md.getPipeline().getStages().iterator().next()
                .getChanges().iterator().next().getSource();
        assertTrue(source.contains("$"),
                "metadata source for the nested change is expected to be the binary name, was: " + source);
    }

    @Test
    @DisplayName("F3: clean build places a @Change in a sub-package into the covering stage")
    void cleanBuildPlacesChangeInSubPackageOfStage() throws Exception {
        Path src = Files.createDirectories(workDir.resolve("src"));
        writeFile(src, "com/example/Config.java", configClass());
        // Change file in com.example.changes.sub — pre-F3 this would be silently dropped.
        writeFile(src, "com/example/changes/sub/_0001__SubPkgChange.java",
                changeClassInPackage("_0001__SubPkgChange", "id-sub", "com.example.changes.sub"));

        Path out = Files.createDirectories(workDir.resolve("out"));
        boolean ok = compile(src, out, allJavaFilesIn(src));
        assertTrue(ok);

        FlamingockMetadata md = readMetadata(metadataPathIn(out));
        assertEquals(1, totalChanges(md.getPipeline()),
                "sub-package change must be placed via covers semantics, not dropped");
        assertTrue(changeIds(md.getPipeline()).contains("id-sub"));
        assertTrue(md.getOrphanChanges() == null || md.getOrphanChanges().isEmpty());
    }

    @Test
    @DisplayName("Round dropping a stage moves its changes to orphans (rather than losing them)")
    void droppedStageMovesChangesToOrphans() throws Exception {
        // Round 1: pipeline has stages 'kept' and 'removed' with one change each.
        Path src = Files.createDirectories(workDir.resolve("src"));
        writeFile(src, "com/example/Config.java", configClassWithTwoStages());
        writeFile(src, "com/example/kept/_0001__KeptChange.java",
                changeClassInPackage("_0001__KeptChange", "id-keep", "com.example.kept"));
        writeFile(src, "com/example/removed/_0002__RemovedChange.java",
                changeClassInPackage("_0002__RemovedChange", "id-rm", "com.example.removed"));

        Path round1Out = Files.createDirectories(workDir.resolve("out1"));
        boolean ok1 = compile(src, round1Out, allJavaFilesIn(src));
        assertTrue(ok1);
        FlamingockMetadata r1 = readMetadata(metadataPathIn(round1Out));
        assertEquals(2, totalChanges(r1.getPipeline()));

        // Round 2: rewrite Config.java to drop the 'removed' stage (keeping 'kept').
        // Recompile all sources — Gradle does this when a non-isolating processor's input changes.
        Files.write(src.resolve("com/example/Config.java"), configClassKeptOnly().getBytes());
        resetProcessorState();
        Path round2Out = Files.createDirectories(workDir.resolve("out2"));
        seedFromPreviousRound(round1Out, round2Out);

        boolean ok2 = compile(src, round2Out, allJavaFilesIn(src));
        assertTrue(ok2);

        FlamingockMetadata r2 = readMetadata(metadataPathIn(round2Out));
        // 'kept' stage covers com.example.kept → id-keep stays placed there.
        assertEquals(1, totalChanges(r2.getPipeline()));
        assertTrue(changeIds(r2.getPipeline()).contains("id-keep"));
        // 'removed' is gone; id-rm has no covering stage → ends up in orphans.
        assertNotNull(r2.getOrphanChanges());
        assertEquals(1, r2.getOrphanChanges().size());
        assertEquals("id-rm", r2.getOrphanChanges().get(0).getId());
    }

    // ---------------------------- compile helpers ----------------------------

    private boolean compile(Path src, Path out, List<Path> sourceFiles) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "No system Java compiler. Tests must run on a JDK, not a JRE.");
        }
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(out.toFile()));
            fm.setLocation(StandardLocation.SOURCE_PATH, Collections.singletonList(src.toFile()));
            fm.setLocation(StandardLocation.CLASS_PATH, currentClasspath());

            List<JavaFileObject> javaSources = new ArrayList<>();
            for (Path p : sourceFiles) {
                fm.getJavaFileObjectsFromFiles(Collections.singletonList(p.toFile()))
                        .forEach(javaSources::add);
            }

            StringWriter err = new StringWriter();
            List<String> options = Arrays.asList(
                    "-proc:only",
                    "-Asources=" + src.toAbsolutePath(),
                    "-Aresources=" + src.toAbsolutePath()
            );
            JavaCompiler.CompilationTask task = compiler.getTask(
                    err, fm, null, options, null, javaSources);
            task.setProcessors(Collections.singletonList(new FlamingockAnnotationProcessor()));
            boolean ok = task.call();
            if (!ok) {
                System.err.println("Compilation diagnostics:\n" + err);
            }
            return ok;
        }
    }

    private static List<File> currentClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(File::new)
                .collect(Collectors.toList());
    }

    private static List<Path> allJavaFilesIn(Path src) throws IOException {
        try (Stream<Path> walk = Files.walk(src)) {
            return walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static void writeFile(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes());
    }

    /** No-op: kept for call-site compatibility — {@code hasProcessed} is now an instance field. */
    private static void resetProcessorState() {
        // intentionally empty
    }

    // ---------------------------- inspection helpers ----------------------------

    /**
     * Resolve the per-module metadata file path inside {@code classOutputDir} by reading the
     * generated SPI registration file. Returns {@code null} if no SPI registration was
     * produced (i.e. the round was a no-op).
     */
    private static Path metadataPathIn(Path classOutputDir) throws IOException {
        return MetadataModuleIdentity.discoverFromClassOutput(classOutputDir)
                .map(id -> classOutputDir.resolve(id.getMetadataResourcePath()))
                .orElse(null);
    }

    /** True iff the SPI file exists in {@code classOutputDir} — i.e. the round was relevant. */
    private static boolean metadataWasGenerated(Path classOutputDir) throws IOException {
        return MetadataModuleIdentity.discoverFromClassOutput(classOutputDir).isPresent();
    }

    /**
     * Seed an incremental round's CLASS_OUTPUT with the previous round's META-INF directory
     * (SPI file + metadata file together). The processor needs the SPI file in the new
     * CLASS_OUTPUT to discover the persisted suffix and read the per-module metadata.
     */
    private static void seedFromPreviousRound(Path previousOut, Path currentOut) throws IOException {
        Path source = previousOut.resolve("META-INF");
        if (!Files.exists(source)) return;
        Files.walk(source).forEach(src -> {
            try {
                Path target = currentOut.resolve(previousOut.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(src, target);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static FlamingockMetadata readMetadata(Path file) throws IOException {
        return JsonObjectMapper.DEFAULT_INSTANCE.readValue(file.toFile(), FlamingockMetadata.class);
    }

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
            for (AbstractPreviewChange c : pipeline.getSystemStage().getChanges()) ids.add(c.getId());
        }
        if (pipeline.getStages() != null) {
            for (PreviewStage s : pipeline.getStages()) {
                if (s.getChanges() != null) {
                    for (AbstractPreviewChange c : s.getChanges()) ids.add(c.getId());
                }
            }
        }
        return ids;
    }

    // ---------------------------- source generators ----------------------------

    private static String configClass() {
        return "package com.example;\n"
                + "import io.flamingock.api.annotations.EnableFlamingock;\n"
                + "import io.flamingock.api.annotations.Stage;\n"
                + "@EnableFlamingock(stages = @Stage(location = \"com.example.changes\"))\n"
                + "public class Config {}\n";
    }

    private static String changeClass(String simpleName, String id) {
        return changeClassInPackage(simpleName, id, "com.example.changes");
    }

    private static String changeClassInPackage(String simpleName, String id, String pkg) {
        return "package " + pkg + ";\n"
                + "import io.flamingock.api.annotations.Change;\n"
                + "import io.flamingock.api.annotations.Apply;\n"
                + "@Change(id = \"" + id + "\", author = \"test\")\n"
                + "public class " + simpleName + " {\n"
                + "    @Apply public void apply() {}\n"
                + "}\n";
    }

    private static String configClassWithTwoStages() {
        return "package com.example;\n"
                + "import io.flamingock.api.annotations.EnableFlamingock;\n"
                + "import io.flamingock.api.annotations.Stage;\n"
                + "@EnableFlamingock(stages = {\n"
                + "    @Stage(location = \"com.example.kept\"),\n"
                + "    @Stage(location = \"com.example.removed\")\n"
                + "})\n"
                + "public class Config {}\n";
    }

    /**
     * Outer class hosting a static-nested {@code @Change} class. The @Change-annotated class
     * is the nested one; its binary name uses {@code $} (e.g. {@code _0001__Outer$NestedChange})
     * which exercises the {@code Elements.getTypeElement} canonical-name conversion in the
     * prune logic.
     */
    private static String outerWithNestedChange() {
        return "package com.example.changes;\n"
                + "import io.flamingock.api.annotations.Change;\n"
                + "import io.flamingock.api.annotations.Apply;\n"
                + "public class _0001__Outer {\n"
                + "    @Change(id = \"id-nested\", author = \"test\")\n"
                + "    public static class NestedChange {\n"
                + "        @Apply public void apply() {}\n"
                + "    }\n"
                + "}\n";
    }

    private static String configClassKeptOnly() {
        return "package com.example;\n"
                + "import io.flamingock.api.annotations.EnableFlamingock;\n"
                + "import io.flamingock.api.annotations.Stage;\n"
                + "@EnableFlamingock(stages = @Stage(location = \"com.example.kept\"))\n"
                + "public class Config {}\n";
    }
}
