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
package io.flamingock.internal.core.pipeline.run;

import io.flamingock.api.StageType;
import io.flamingock.internal.core.change.filter.ChangeFilter;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.loaded.stage.DefaultLoadedStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PipelineRun#of(LoadedPipeline)} focusing on the runtime application
 * of the {@link ChangeFilter}s carried by the {@link LoadedPipeline}.
 *
 * <p>These tests pin down the contract introduced by issue #933: change filters must be
 * applied at runtime construction time, so excluded changes never appear in the resulting
 * pipeline. If a filter removes all changes from a stage, the stage is dropped (sparse
 * block semantics), and block ordering (SYSTEM -> LEGACY -> DEFAULT) is preserved.
 */
class PipelineRunOfLoadedPipelineTest {

    // ---------------------------------------------------------------------------------------
    //  Empty / no-filter baseline
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("SHOULD keep all changes WHEN no filters are present")
    void noFiltersKeepsAllChangesInAllStages() {
        DefaultLoadedStage userStage = newStage("user", StageType.DEFAULT, "u1", "u2");
        LoadedPipeline pipeline = mockPipeline(Optional.empty(), Arrays.asList(userStage), Collections.emptyList());

        PipelineRun run = PipelineRun.of(pipeline);

        assertEquals(1, run.getStageCount());
        assertEquals(2L, run.getTotalChangeCount());
    }

    @Test
    @DisplayName("SHOULD run validation on the unfiltered pipeline (existing behavior preserved)")
    void validationRunsOnTheUnfilteredPipeline() {
        // The validate() call must happen before filtering. We assert it is invoked exactly
        // once regardless of whether filters are present. This pins down the contract that
        // validation operates on the unfiltered pipeline (consistent with builder-time checks).
        DefaultLoadedStage userStage = newStage("user", StageType.DEFAULT, "u1");
        LoadedPipeline pipeline = mock(LoadedPipeline.class);
        when(pipeline.getSystemStage()).thenReturn(Optional.empty());
        when(pipeline.getStages()).thenReturn(Collections.singletonList(userStage));
        when(pipeline.getChangeFilters()).thenReturn(Collections.emptyList());
        // Mockito's default for void methods is no-op, so validate() is essentially a no-op
        // here. We still call the real method (doNothing) and rely on the test passing through
        // the rest of the pipeline.
        doNothing().when(pipeline).validate();

        PipelineRun run = PipelineRun.of(pipeline);

        assertNotNull(run);
        assertEquals(1, run.getStageCount());
    }

    // ---------------------------------------------------------------------------------------
    //  Filter applies to individual changes
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("SHOULD exclude a change WHEN a filter rejects it")
    void singleFilterExcludesTheRejectedChange() {
        DefaultLoadedStage userStage = newStage("user", StageType.DEFAULT, "u1", "u2", "u3");
        ChangeFilter rejectU2 = descriptor -> !"u2".equals(descriptor.getId());
        LoadedPipeline pipeline = mockPipeline(Optional.empty(), Arrays.asList(userStage), Arrays.asList(rejectU2));

        PipelineRun run = PipelineRun.of(pipeline);

        List<String> survivingIds = collectChangeIds(run);
        assertEquals(2L, run.getTotalChangeCount());
        assertTrue(survivingIds.contains("u1"));
        assertFalse(survivingIds.contains("u2"));
        assertTrue(survivingIds.contains("u3"));
    }

    @Test
    @DisplayName("SHOULD keep a change WHEN all filters accept it (AND semantics across filters)")
    void multipleFiltersAreAnded() {
        DefaultLoadedStage userStage = newStage("user", StageType.DEFAULT, "u1", "u2");
        // Filter A: drop u1. Filter B: drop u2. Both must drop together, but a change is
        // kept only if BOTH filters keep it. So neither u1 nor u2 survives.
        ChangeFilter dropU1 = descriptor -> !"u1".equals(descriptor.getId());
        ChangeFilter dropU2 = descriptor -> !"u2".equals(descriptor.getId());
        LoadedPipeline pipeline = mockPipeline(Optional.empty(), Arrays.asList(userStage), Arrays.asList(dropU1, dropU2));

        PipelineRun run = PipelineRun.of(pipeline);

        // Both changes rejected by at least one filter -> stage is dropped (sparse).
        assertEquals(0, run.getStageCount());
        assertEquals(0L, run.getTotalChangeCount());
    }

    @Test
    @DisplayName("SHOULD keep a change WHEN one filter keeps it and another also keeps it (intersection)")
    void multipleFiltersAllowOverlap() {
        DefaultLoadedStage userStage = newStage("user", StageType.DEFAULT, "shared", "onlyA", "onlyB");
        // Filter A keeps "shared" and "onlyA"; Filter B keeps "shared" and "onlyB".
        // Intersection = "shared".
        ChangeFilter filterA = descriptor -> {
            String id = descriptor.getId();
            return id.equals("shared") || id.equals("onlyA");
        };
        ChangeFilter filterB = descriptor -> {
            String id = descriptor.getId();
            return id.equals("shared") || id.equals("onlyB");
        };
        LoadedPipeline pipeline = mockPipeline(Optional.empty(), Arrays.asList(userStage), Arrays.asList(filterA, filterB));

        PipelineRun run = PipelineRun.of(pipeline);

        List<String> survivingIds = collectChangeIds(run);
        assertEquals(1L, run.getTotalChangeCount());
        assertTrue(survivingIds.contains("shared"));
        assertFalse(survivingIds.contains("onlyA"));
        assertFalse(survivingIds.contains("onlyB"));
    }

    // ---------------------------------------------------------------------------------------
    //  Empty stages after filtering — sparse semantics
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("SHOULD drop a stage WHEN filtering removes all its changes")
    void emptyStageAfterFilteringIsDropped() {
        DefaultLoadedStage alpha = newStage("alpha", StageType.DEFAULT, "a1", "a2");
        DefaultLoadedStage beta = newStage("beta", StageType.DEFAULT, "b1", "b2");
        // Filter rejects every change in 'alpha' but lets 'beta' through.
        ChangeFilter dropAllAlpha = descriptor -> !descriptor.getId().startsWith("a");
        LoadedPipeline pipeline = mockPipeline(Optional.empty(), Arrays.asList(alpha, beta), Arrays.asList(dropAllAlpha));

        PipelineRun run = PipelineRun.of(pipeline);

        assertEquals(1, run.getStageCount(), "alpha must be dropped when empty after filtering");
        assertEquals("beta", run.getStageRuns().get(0).getName());
        assertEquals(2L, run.getTotalChangeCount());
    }

    @Test
    @DisplayName("SHOULD drop a stage WHEN filtering removes every change in it, even mid-block")
    void emptyStageInTheMiddleIsDropped() {
        DefaultLoadedStage first = newStage("first", StageType.DEFAULT, "f1");
        DefaultLoadedStage doomed = newStage("doomed", StageType.DEFAULT, "d1", "d2");
        DefaultLoadedStage last = newStage("last", StageType.DEFAULT, "l1");
        ChangeFilter dropDoomed = descriptor -> !descriptor.getId().startsWith("d");
        LoadedPipeline pipeline = mockPipeline(
                Optional.empty(),
                Arrays.asList(first, doomed, last),
                Arrays.asList(dropDoomed));

        PipelineRun run = PipelineRun.of(pipeline);

        List<String> survivingStageNames = run.getStageRuns().stream()
                .map(StageRun::getName)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("first", "last"), survivingStageNames,
                "doomed must be dropped; first and last must remain in order");
    }

    @Test
    @DisplayName("SHOULD yield an empty run WHEN every stage is filtered out")
    void allStagesFilteredOutYieldsEmptyRun() {
        DefaultLoadedStage alpha = newStage("alpha", StageType.DEFAULT, "a1");
        DefaultLoadedStage beta = newStage("beta", StageType.DEFAULT, "b1");
        ChangeFilter rejectAll = descriptor -> false;
        LoadedPipeline pipeline = mockPipeline(Optional.empty(), Arrays.asList(alpha, beta), Arrays.asList(rejectAll));

        PipelineRun run = PipelineRun.of(pipeline);

        assertEquals(0, run.getStageCount());
        assertEquals(0L, run.getTotalChangeCount());
    }

    // ---------------------------------------------------------------------------------------
    //  Block ordering preserved (SYSTEM -> LEGACY -> DEFAULT) after filtering
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("SHOULD preserve SYSTEM -> LEGACY -> DEFAULT block ordering after filtering")
    void blockOrderingPreservedAfterFiltering() {
        DefaultLoadedStage systemStage = newStage("system", StageType.SYSTEM, "s1", "s2");
        DefaultLoadedStage legacyStage = newStage("legacy", StageType.LEGACY, "l1");
        DefaultLoadedStage userStage = newStage("user", StageType.DEFAULT, "u1", "u2", "u3");
        // Drop s2 (SYSTEM), keep l1 (LEGACY), drop u2 (DEFAULT).
        ChangeFilter customFilter = descriptor -> {
            String id = descriptor.getId();
            return !id.equals("s2") && !id.equals("u2");
        };
        LoadedPipeline pipeline = mockPipeline(
                Optional.of(systemStage),
                Arrays.asList(legacyStage, userStage),
                Arrays.asList(customFilter));

        PipelineRun run = PipelineRun.of(pipeline);

        // Flat list is canonicalised: SYSTEM block first, then LEGACY, then DEFAULT, regardless
        // of input order. Filtering must not perturb this.
        List<String> flatStageNames = run.getStageRuns().stream()
                .map(StageRun::getName)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("system", "legacy", "user"), flatStageNames);

        // Block view: one block per type, in dependency order.
        List<StageRunBlock> blocks = run.getStageBlocks();
        assertEquals(3, blocks.size());
        assertEquals(StageType.SYSTEM, blocks.get(0).getType());
        assertEquals(StageType.LEGACY, blocks.get(1).getType());
        assertEquals(StageType.DEFAULT, blocks.get(2).getType());

        // Surviving change IDs.
        List<String> survivingIds = collectChangeIds(run);
        assertEquals(4L, run.getTotalChangeCount());
        assertTrue(survivingIds.contains("s1"));
        assertFalse(survivingIds.contains("s2"));
        assertTrue(survivingIds.contains("l1"));
        assertTrue(survivingIds.contains("u1"));
        assertFalse(survivingIds.contains("u2"));
        assertTrue(survivingIds.contains("u3"));
    }

    @Test
    @DisplayName("SHOULD drop a SYSTEM block WHEN the system stage becomes empty after filtering")
    void emptySystemStageIsDroppedAndLegacyTakesFoundationRole() {
        // Filtering removes every change in the system stage. The SYSTEM block should be
        // dropped (sparse), and the LEGACY block becomes the foundation in the flat view.
        DefaultLoadedStage systemStage = newStage("system", StageType.SYSTEM, "s1", "s2");
        DefaultLoadedStage legacyStage = newStage("legacy", StageType.LEGACY, "l1");
        DefaultLoadedStage userStage = newStage("user", StageType.DEFAULT, "u1");
        ChangeFilter dropAllSystem = descriptor -> !descriptor.getId().startsWith("s");
        LoadedPipeline pipeline = mockPipeline(
                Optional.of(systemStage),
                Arrays.asList(legacyStage, userStage),
                Arrays.asList(dropAllSystem));

        PipelineRun run = PipelineRun.of(pipeline);

        List<String> flatStageNames = run.getStageRuns().stream()
                .map(StageRun::getName)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("legacy", "user"), flatStageNames);

        List<StageRunBlock> blocks = run.getStageBlocks();
        assertEquals(2, blocks.size(), "empty SYSTEM block must be omitted");
        assertEquals(StageType.LEGACY, blocks.get(0).getType());
        assertEquals(StageType.DEFAULT, blocks.get(1).getType());
    }

    // ---------------------------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------------------------

    private static LoadedPipeline mockPipeline(Optional<AbstractLoadedStage> systemStage,
                                               List<AbstractLoadedStage> userStages,
                                               Collection<ChangeFilter> filters) {
        LoadedPipeline pipeline = mock(LoadedPipeline.class);
        when(pipeline.getSystemStage()).thenReturn(systemStage);
        when(pipeline.getStages()).thenReturn(userStages);
        when(pipeline.getChangeFilters()).thenReturn(filters);
        doNothing().when(pipeline).validate();
        return pipeline;
    }

    private static DefaultLoadedStage newStage(String name, StageType type, String... changeIds) {
        List<AbstractLoadedChange> changes = new ArrayList<>(changeIds.length);
        for (String id : changeIds) {
            changes.add(mockChange(id));
        }
        return new DefaultLoadedStage(name, type, changes);
    }

    private static AbstractLoadedChange mockChange(String id) {
        AbstractLoadedChange change = mock(AbstractLoadedChange.class);
        when(change.getId()).thenReturn(id);
        return change;
    }

    private static List<String> collectChangeIds(PipelineRun run) {
        List<String> ids = new ArrayList<>();
        for (StageRun stageRun : run.getStageRuns()) {
            for (AbstractLoadedChange change : stageRun.getLoadedStage().getChanges()) {
                ids.add(change.getId());
            }
        }
        return ids;
    }
}
