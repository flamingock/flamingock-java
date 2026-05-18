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
package io.flamingock.core.processor.util;

import io.flamingock.api.StageType;
import io.flamingock.internal.common.core.metadata.BuilderProviderInfo;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.AbstractPreviewChange;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.preview.SystemPreviewStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlamingockMetadataMergerTest {

    @Test
    @DisplayName("mergePipeline preserves existing-only changes by id when fresh stage matches by name")
    void mergePipelinePreservesUnseenChanges() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(stage("default", "com.example.changes",
                change("id-1", "com.example.changes.A"),
                change("id-2", "com.example.changes.B"))));

        PreviewPipeline fresh = pipeline(stage("default", "com.example.changes",
                change("id-1", "com.example.changes.A")));

        FlamingockMetadataMerger.applyRound(target, fresh, Collections.emptyList(), false);

        PreviewStage merged = first(target.getPipeline().getStages());
        List<String> mergedIds = ids(merged.getChanges());
        assertEquals(Arrays.asList("id-1", "id-2"), mergedIds, "id-2 should be preserved from existing");
    }

    @Test
    @DisplayName("mergePipeline drops existing stage with no fresh counterpart")
    void mergePipelineDropsRemovedStage() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(
                stage("kept", "com.example.kept", change("k-1", "com.example.kept.K")),
                stage("removed", "com.example.removed", change("r-1", "com.example.removed.R"))));

        PreviewPipeline fresh = pipeline(stage("kept", "com.example.kept",
                change("k-1", "com.example.kept.K")));

        FlamingockMetadataMerger.applyRound(target, fresh, Collections.emptyList(), false);

        Collection<PreviewStage> stages = target.getPipeline().getStages();
        assertEquals(1, stages.size());
        assertEquals("kept", first(stages).getName());
    }

    @Test
    @DisplayName("mergePipeline merges system stage independently and preserves SystemPreviewStage type")
    void mergePipelineMergesSystemStage() {
        FlamingockMetadata target = new FlamingockMetadata();
        SystemPreviewStage existingSystem = new SystemPreviewStage("system", null, null, null,
                Arrays.asList(systemChange("s-1", "com.example.sys.S1"),
                              systemChange("s-2", "com.example.sys.S2")));
        PreviewPipeline existing = new PreviewPipeline(existingSystem, Collections.emptyList());
        target.setPipeline(existing);

        SystemPreviewStage freshSystem = new SystemPreviewStage("system", null, null, null,
                Collections.singletonList(systemChange("s-1", "com.example.sys.S1")));
        PreviewPipeline fresh = new PreviewPipeline(freshSystem, Collections.emptyList());

        FlamingockMetadataMerger.applyRound(target, fresh, Collections.emptyList(), false);

        PreviewStage system = target.getPipeline().getSystemStage();
        assertNotNull(system);
        assertTrue(system instanceof SystemPreviewStage, "system stage type must be SystemPreviewStage");
        assertEquals(Arrays.asList("s-1", "s-2"), ids(system.getChanges()));
    }

    @Test
    @DisplayName("mergePipeline matches by sourcesPackage when names differ")
    void mergePipelineFallsBackToSourcesPackage() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(stage("old-name", "com.example.changes",
                change("id-1", "com.example.changes.A"),
                change("id-2", "com.example.changes.B"))));

        PreviewPipeline fresh = pipeline(stage("new-name", "com.example.changes",
                change("id-1", "com.example.changes.A")));

        FlamingockMetadataMerger.applyRound(target, fresh, Collections.emptyList(), false);

        PreviewStage merged = first(target.getPipeline().getStages());
        assertEquals("new-name", merged.getName());
        assertEquals(Arrays.asList("id-1", "id-2"), ids(merged.getChanges()));
    }

    @Test
    @DisplayName("applyRound (state 2): in-round change with same id replaces existing in covered stage")
    void applyRoundState2ReplacesById() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(stage("default", "com.example.changes",
                change("id-1", "com.example.changes.A"),
                change("id-2", "com.example.changes.B"))));

        CodePreviewChange replacement = change("id-1", "com.example.changes.A");
        FlamingockMetadataMerger.applyRound(target, null,
                Collections.singletonList(replacement), false);

        PreviewStage stage = first(target.getPipeline().getStages());
        assertEquals(Arrays.asList("id-1", "id-2"), ids(stage.getChanges()));
        assertSame(replacement, first(stage.getChanges()), "id-1 entry must be the replacement");
        assertTrue(target.getOrphanChanges() == null || target.getOrphanChanges().isEmpty());
    }

    @Test
    @DisplayName("applyRound (state 2): new in-round change is placed alongside existing in covered stage")
    void applyRoundState2AppendsNew() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(stage("default", "com.example.changes",
                change("id-1", "com.example.changes.A"))));

        CodePreviewChange added = change("id-3", "com.example.changes.C");
        FlamingockMetadataMerger.applyRound(target, null,
                Collections.singletonList(added), false);

        PreviewStage stage = first(target.getPipeline().getStages());
        // applyRound places candidates in priority order: in-round first, then existing.
        // Final order in metadata is irrelevant at runtime (pipeline sorts by `order` field).
        assertEquals(2, stage.getChanges().size());
        java.util.Set<String> placedIds = new java.util.HashSet<>(ids(stage.getChanges()));
        assertEquals(new java.util.HashSet<>(Arrays.asList("id-1", "id-3")), placedIds);
        assertTrue(target.getOrphanChanges() == null || target.getOrphanChanges().isEmpty());
    }

    @Test
    @DisplayName("applyRound (state 2): in-round change with no covering stage lands in orphans")
    void applyRoundState2NoCoveringStageGoesToOrphans() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(stage("default", "com.example.changes",
                change("id-1", "com.example.changes.A"))));

        CodePreviewChange orphan = change("id-9", "com.other.changes.X");
        FlamingockMetadataMerger.applyRound(target, null,
                Collections.singletonList(orphan), false);

        assertNotNull(target.getOrphanChanges());
        assertEquals(1, target.getOrphanChanges().size());
        assertEquals("id-9", target.getOrphanChanges().get(0).getId());
    }

    @Test
    @DisplayName("mergeProperties initializes null map and overrides on key clash")
    void mergePropertiesOverridesAndInitializes() {
        FlamingockMetadata target = new FlamingockMetadata();
        Map<String, String> incoming = new HashMap<>();
        incoming.put("k1", "v1");
        FlamingockMetadataMerger.mergeProperties(target, incoming);
        assertEquals("v1", target.getProperties().get("k1"));

        Map<String, String> incoming2 = new HashMap<>();
        incoming2.put("k1", "v1-new");
        incoming2.put("k2", "v2");
        FlamingockMetadataMerger.mergeProperties(target, incoming2);
        assertEquals("v1-new", target.getProperties().get("k1"));
        assertEquals("v2", target.getProperties().get("k2"));
    }

    @Test
    @DisplayName("setBuilderProvider replaces field")
    void setBuilderProviderReplaces() {
        FlamingockMetadata target = new FlamingockMetadata();
        BuilderProviderInfo info = new BuilderProviderInfo("com.example.Builder", "create", false);
        FlamingockMetadataMerger.setBuilderProvider(target, info);
        assertSame(info, target.getBuilderProvider());

        BuilderProviderInfo replacement = new BuilderProviderInfo("com.example.Builder", "createWithArgs", true);
        FlamingockMetadataMerger.setBuilderProvider(target, replacement);
        assertSame(replacement, target.getBuilderProvider());
    }

    @Test
    @DisplayName("applyRound with null structure preserves the existing pipeline reference (state 2)")
    void applyRoundNullStructurePreservesExisting() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(stage("default", "com.example.changes",
                change("id-1", "com.example.changes.A"))));
        PreviewPipeline before = target.getPipeline();

        FlamingockMetadataMerger.applyRound(target, null, Collections.emptyList(), false);

        // No fresh structure → existing pipeline kept (same reference). Code changes are
        // stripped and re-routed by covers; here id-1 lands back in the same stage.
        assertSame(before, target.getPipeline());
        assertEquals(Collections.singletonList("id-1"),
                ids(target.getPipeline().getStages().iterator().next().getChanges()));
    }

    @Test
    @DisplayName("applyRound state 3: null structure + no existing pipeline parks all in-round as orphans")
    void applyRoundState3ParksAllAsOrphans() {
        FlamingockMetadata target = new FlamingockMetadata();
        // No existing pipeline.
        FlamingockMetadataMerger.applyRound(target, null,
                Arrays.asList(change("id-1", "com.example.A"), change("id-2", "com.example.B")),
                false);

        assertNull(target.getPipeline(), "no structure provided and none existed");
        assertNotNull(target.getOrphanChanges());
        assertEquals(Arrays.asList("id-1", "id-2"),
                target.getOrphanChanges().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    @DisplayName("mergePipeline parks change in orphans when its package is no longer covered by any stage")
    void mergePipelineParksOrphanWhenStageDropped() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(
                stage("kept", "com.example.kept", change("k-1", "com.example.kept.K")),
                stage("removed", "com.example.removed", change("r-1", "com.example.removed.R"))));

        PreviewPipeline fresh = pipeline(stage("kept", "com.example.kept",
                change("k-1", "com.example.kept.K")));

        FlamingockMetadataMerger.applyRound(target, fresh, Collections.emptyList(), false);

        assertEquals(1, target.getPipeline().getStages().size());
        assertNotNull(target.getOrphanChanges());
        assertEquals(1, target.getOrphanChanges().size());
        assertEquals("r-1", target.getOrphanChanges().get(0).getId());
    }

    @Test
    @DisplayName("mergePipeline rehomes existing orphan into a newly-covering stage")
    void mergePipelineRehomesExistingOrphan() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setOrphanChanges(new ArrayList<>(Collections.singletonList(
                change("id-orphan", "com.example.changes.X"))));

        PreviewPipeline fresh = pipeline(stage("default", "com.example.changes"));
        FlamingockMetadataMerger.applyRound(target, fresh, Collections.emptyList(), false);

        PreviewStage merged = first(target.getPipeline().getStages());
        assertEquals(Collections.singletonList("id-orphan"), ids(merged.getChanges()));
        assertTrue(target.getOrphanChanges() == null || target.getOrphanChanges().isEmpty());
    }

    @Test
    @DisplayName("mergePipeline persists strictStageMapping on target")
    void mergePipelinePersistsStrictFlag() {
        FlamingockMetadata target = new FlamingockMetadata();
        PreviewPipeline fresh = pipeline(stage("default", "com.example.changes"));

        FlamingockMetadataMerger.applyRound(target, fresh, Collections.emptyList(), true);
        assertTrue(target.isStrictStageMapping());

        FlamingockMetadataMerger.applyRound(target, fresh, Collections.emptyList(), false);
        assertFalse(target.isStrictStageMapping());
    }

    @Test
    @DisplayName("applyRound (state 3): in-round change replaces previous orphan with same id")
    void applyRoundState3InRoundOverwritesOrphan() {
        FlamingockMetadata target = new FlamingockMetadata();
        // Previous orphan with id-1.
        target.setOrphanChanges(new ArrayList<>(Collections.singletonList(
                change("id-1", "com.example.changes.A"))));

        CodePreviewChange replacement = change("id-1", "com.example.changes.A");
        CodePreviewChange other = change("id-2", "com.example.changes.B");
        FlamingockMetadataMerger.applyRound(target, null,
                Arrays.asList(replacement, other), false);

        assertNull(target.getPipeline());
        assertEquals(2, target.getOrphanChanges().size());
        assertSame(replacement, target.getOrphanChanges().get(0),
                "in-round wins over previous orphan with same id");
        assertEquals("id-2", target.getOrphanChanges().get(1).getId());
    }

    // -------------------------- F3: covers-based unified placement --------------------------

    @Test
    @DisplayName("F3: in-round change in sub-package of stage's location is placed in that stage (not dropped)")
    void mergePipelinePlacesInRoundChangeInSubPackageOfStage() {
        FlamingockMetadata target = new FlamingockMetadata();
        // Fresh structure: stage 'changes' covering com.example.changes, no pre-placed code.
        PreviewPipeline freshStructure = pipeline(stage("changes", "com.example.changes"));
        // In-round change in a SUB-package — old exact-match code would silently drop this.
        CodePreviewChange subPackageChange = change("id-sub", "com.example.changes.sub.MyChange");

        FlamingockMetadataMerger.applyRound(target, freshStructure,
                Collections.singletonList(subPackageChange), false);

        PreviewStage placed = first(target.getPipeline().getStages());
        assertEquals(Collections.singletonList("id-sub"), ids(placed.getChanges()));
        assertTrue(target.getOrphanChanges() == null || target.getOrphanChanges().isEmpty(),
                "no orphans expected — covers semantics rescues the sub-package change");
    }

    @Test
    @DisplayName("F3: longest-prefix wins when multiple stages cover a change's package")
    void mergePipelineLongestPrefixWins() {
        FlamingockMetadata target = new FlamingockMetadata();
        PreviewPipeline freshStructure = pipeline(
                stage("outer", "com.example.changes"),
                stage("inner", "com.example.changes.sub"));
        CodePreviewChange c = change("id-1", "com.example.changes.sub.A");

        FlamingockMetadataMerger.applyRound(target, freshStructure,
                Collections.singletonList(c), false);

        PreviewStage outer = stagesByName(target.getPipeline()).get("outer");
        PreviewStage inner = stagesByName(target.getPipeline()).get("inner");
        assertTrue(outer.getChanges() == null || outer.getChanges().isEmpty(),
                "outer stage should not contain id-1 (inner is more specific)");
        assertEquals(Collections.singletonList("id-1"), ids(inner.getChanges()),
                "inner stage with the more specific package should win");
    }

    @Test
    @DisplayName("F3: previously-known system change is preserved when in-round has no system change")
    void mergePipelinePreservesPreviouslyKnownSystemChange() {
        FlamingockMetadata target = new FlamingockMetadata();
        SystemPreviewStage existingSystem = new SystemPreviewStage("system", null, null, null,
                Collections.singletonList(systemChange("s-1", "com.example.sys.S1")));
        target.setPipeline(new PreviewPipeline(existingSystem,
                new ArrayList<>(Collections.singletonList(stage("default", "com.example.changes")))));

        // Fresh structure has no system stage — only a default stage.
        PreviewPipeline freshStructure = pipeline(stage("default", "com.example.changes"));

        // No in-round changes.
        FlamingockMetadataMerger.applyRound(target, freshStructure,
                Collections.emptyList(), false);

        assertNotNull(target.getPipeline().getSystemStage(),
                "merger must add a system stage to host the previously-known system change");
        assertEquals(Collections.singletonList("s-1"),
                ids(target.getPipeline().getSystemStage().getChanges()));
    }

    @Test
    @DisplayName("F3: in-round wins on id collision against orphans and previous stages")
    void mergePipelineInRoundWinsOnIdCollision() {
        FlamingockMetadata target = new FlamingockMetadata();
        CodePreviewChange oldVersion = change("id-1", "com.example.changes.A");
        target.setPipeline(pipeline(stage("default", "com.example.changes", oldVersion)));
        target.setOrphanChanges(new ArrayList<>(Collections.singletonList(
                change("id-orphan", "com.example.changes.B"))));

        CodePreviewChange newVersion = change("id-1", "com.example.changes.A");
        PreviewPipeline freshStructure = pipeline(stage("default", "com.example.changes"));

        FlamingockMetadataMerger.applyRound(target, freshStructure,
                Collections.singletonList(newVersion), false);

        PreviewStage placed = first(target.getPipeline().getStages());
        assertSame(newVersion, first(placed.getChanges()), "in-round version must win");
        // id-orphan from previous orphan list also gets placed via covers.
        assertTrue(target.getOrphanChanges() == null || target.getOrphanChanges().isEmpty());
        assertEquals(Arrays.asList("id-1", "id-orphan"), ids(placed.getChanges()));
    }

    // -------------------------- F1: prune deleted classes --------------------------

    @Test
    @DisplayName("pruneDeletedClasses removes entries from stages and orphans, returns true")
    void pruneDeletedClassesRemovesAndReturnsTrue() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(stage("default", "com.example.changes",
                change("id-keep", "com.example.changes.Keep"),
                change("id-del", "com.example.changes.Deleted"))));
        target.setOrphanChanges(new ArrayList<>(Arrays.asList(
                change("id-orph-keep", "com.example.changes.OrphanKeep"),
                change("id-orph-del", "com.example.changes.OrphanDeleted"))));

        java.util.function.Predicate<String> typeExists = fqcn ->
                !fqcn.endsWith("Deleted") && !fqcn.endsWith("OrphanDeleted");

        boolean pruned = FlamingockMetadataMerger.pruneDeletedClasses(target, typeExists);

        assertTrue(pruned);
        assertEquals(Collections.singletonList("id-keep"),
                ids(first(target.getPipeline().getStages()).getChanges()));
        assertEquals(1, target.getOrphanChanges().size());
        assertEquals("id-orph-keep", target.getOrphanChanges().get(0).getId());
    }

    @Test
    @DisplayName("pruneDeletedClasses returns false when nothing is pruned")
    void pruneDeletedClassesReturnsFalseWhenNothingPruned() {
        FlamingockMetadata target = new FlamingockMetadata();
        target.setPipeline(pipeline(stage("default", "com.example.changes",
                change("id-1", "com.example.changes.A"))));

        boolean pruned = FlamingockMetadataMerger.pruneDeletedClasses(target, fqcn -> true);

        assertFalse(pruned);
    }

    // -------------------------- helpers --------------------------

    private static CodePreviewChange change(String id, String fqcn) {
        return new CodePreviewChange(id, null, "author", fqcn, fqcn,
                null, null, null, false, null, false, null, null, false);
    }

    private static CodePreviewChange systemChange(String id, String fqcn) {
        return new CodePreviewChange(id, null, "author", fqcn, fqcn,
                null, null, null, false, null, true /* system */, null, null, false);
    }

    private static PreviewStage stage(String name, String sourcesPackage, AbstractPreviewChange... changes) {
        return new PreviewStage(name, StageType.DEFAULT, null, sourcesPackage, null, Arrays.asList(changes));
    }

    private static PreviewPipeline pipeline(PreviewStage... stages) {
        return new PreviewPipeline(new ArrayList<>(Arrays.asList(stages)));
    }

    private static List<String> ids(Collection<? extends AbstractPreviewChange> changes) {
        List<String> result = new ArrayList<>();
        for (AbstractPreviewChange c : changes) {
            result.add(c.getId());
        }
        return result;
    }

    private static Map<String, PreviewStage> stagesByName(PreviewPipeline pipeline) {
        Map<String, PreviewStage> byName = new HashMap<>();
        if (pipeline.getStages() != null) {
            for (PreviewStage s : pipeline.getStages()) byName.put(s.getName(), s);
        }
        return byName;
    }

    private static <T> T first(Collection<T> coll) {
        return coll.iterator().next();
    }
}
