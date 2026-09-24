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
package io.flamingock.internal.common.core.metadata;

import io.flamingock.api.StageType;
import io.flamingock.internal.common.core.preview.AbstractPreviewChange;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.preview.SystemPreviewStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@code MetadataLoader.CompositePipelineBuilder}, focused on the
 * stage-collapse logic that was generalised to cover default stages (in addition to legacy
 * stages) so that mixed Java + Kotlin modules — where kapt and javac each produce their own
 * metadata provider with the same default stages — converge on a single default stage in the
 * composite instead of two side-by-side entries.
 */
class CompositePipelineBuilderTest {

    @Test
    @DisplayName("same-name default stages across modules collapse into one with merged change list")
    void defaultStagesWithSameNameAreCollapsed() {
        // Mirrors the kapt + javac scenario: two providers, same default stage, disjoint
        // changes (kapt contributed the Kotlin changes, javac contributed the Java ones).
        FlamingockMetadata moduleKapt = metadataWithDefaultStage(
                "main-stage", "com.example", changes("KotlinChangeA", "KotlinChangeB"));
        FlamingockMetadata moduleJavac = metadataWithDefaultStage(
                "main-stage", "com.example", changes("JavaChangeC"));

        PreviewPipeline composite = new MetadataLoader.CompositePipelineBuilder()
                .buildFrom(Arrays.asList(moduleKapt, moduleJavac));

        List<PreviewStage> stages = new ArrayList<>(composite.getStages());
        assertEquals(1, stages.size(),
                "Two providers contributing the same default stage must yield exactly one stage in the composite");
        PreviewStage merged = stages.get(0);
        assertEquals("main-stage", merged.getName());
        assertEquals(StageType.DEFAULT, merged.getType());

        Set<String> changeIds = changeIds(merged);
        assertEquals(setOf("KotlinChangeA", "KotlinChangeB", "JavaChangeC"), changeIds,
                "Merged stage must contain the id-union of both providers' changes");
    }

    @Test
    @DisplayName("same-name default stages with different sourcesPackage still collapse (first identity wins)")
    void defaultStagesWithMismatchedIdentityStillCollapse() {
        // The mismatch case: two modules declared a default stage with the same name but
        // different sourcesPackage. We still merge — the alternative (keep both stages) was
        // worse for any real use case — and first-seen identity wins. The behaviour change
        // also emits a WARN at runtime; we don't assert on logging here (no in-module log
        // capture infrastructure) and rely on code review for the WARN content.
        FlamingockMetadata moduleA = metadataWithDefaultStage(
                "shared-name", "com.example.a", changes("A1"));
        FlamingockMetadata moduleB = metadataWithDefaultStage(
                "shared-name", "com.example.b", changes("B1"));

        PreviewPipeline composite = new MetadataLoader.CompositePipelineBuilder()
                .buildFrom(Arrays.asList(moduleA, moduleB));

        List<PreviewStage> stages = new ArrayList<>(composite.getStages());
        assertEquals(1, stages.size(),
                "Mismatched-identity same-name stages must still collapse to a single stage");
        PreviewStage merged = stages.get(0);
        assertEquals("com.example.a", merged.getSourcesPackage(),
                "First-seen sourcesPackage must win on identity-mismatch");
        assertEquals(setOf("A1", "B1"), changeIds(merged),
                "Change sets must still be merged despite the identity mismatch");
    }

    @Test
    @DisplayName("legacy stage collapse behaviour is preserved by the refactor")
    void legacyStagesStillCollapse() {
        // Regression guard: the legacy-stage collapse used to live in
        // mergeSameNameLegacyStages; after generalisation it routes through the same
        // mergeSameNameStages path. Verify same-name legacy stages still merge with id-dedup.
        FlamingockMetadata m1 = metadataWithStage(StageType.LEGACY,
                "flamingock-legacy-stage", null, changes("L1", "L2"));
        FlamingockMetadata m2 = metadataWithStage(StageType.LEGACY,
                "flamingock-legacy-stage", null, changes("L2", "L3"));

        PreviewPipeline composite = new MetadataLoader.CompositePipelineBuilder()
                .buildFrom(Arrays.asList(m1, m2));

        List<PreviewStage> stages = new ArrayList<>(composite.getStages());
        assertEquals(1, stages.size());
        PreviewStage merged = stages.get(0);
        assertEquals(StageType.LEGACY, merged.getType());
        assertEquals(setOf("L1", "L2", "L3"), changeIds(merged),
                "Duplicate legacy change ids across modules must be id-deduplicated");
    }

    @Test
    @DisplayName("system stage id-dedup behaviour is preserved (separate code path, regression guard)")
    void systemStageDedupUnchanged() {
        // The system-stage merger (CompositePipelineBuilder#mergeSystem) is a different path
        // from collapseStagesByName and is unchanged by this refactor. Sanity-check it.
        SystemPreviewStage sysA = new SystemPreviewStage("system-stage", "desc", null, null,
                new ArrayList<>(changes("S1", "S2")));
        SystemPreviewStage sysB = new SystemPreviewStage("system-stage", "desc", null, null,
                new ArrayList<>(changes("S2", "S3")));
        FlamingockMetadata m1 = new FlamingockMetadata();
        m1.setPipeline(new PreviewPipeline(sysA, Collections.emptyList()));
        FlamingockMetadata m2 = new FlamingockMetadata();
        m2.setPipeline(new PreviewPipeline(sysB, Collections.emptyList()));

        PreviewPipeline composite = new MetadataLoader.CompositePipelineBuilder()
                .buildFrom(Arrays.asList(m1, m2));

        PreviewStage system = composite.getSystemStage();
        assertNotNull(system, "Composite must carry a system stage when contributors had one");
        assertEquals(setOf("S1", "S2", "S3"), changeIds(system),
                "Duplicate system-stage change ids across modules must be id-deduplicated");
    }

    @Test
    @DisplayName("default stages with distinct names remain distinct (sanity: not over-collapsing)")
    void distinctNameDefaultStagesAreKeptSeparate() {
        // Guard against the lazy implementation that would collapse everything into one
        // group. Distinct names must stay distinct.
        FlamingockMetadata m1 = metadataWithDefaultStage("alpha", "com.alpha", changes("a1"));
        FlamingockMetadata m2 = metadataWithDefaultStage("beta", "com.beta", changes("b1"));

        PreviewPipeline composite = new MetadataLoader.CompositePipelineBuilder()
                .buildFrom(Arrays.asList(m1, m2));

        List<PreviewStage> stages = new ArrayList<>(composite.getStages());
        assertEquals(2, stages.size(), "Distinct-name stages must remain separate");
        Set<String> names = stages.stream().map(PreviewStage::getName).collect(Collectors.toSet());
        assertEquals(setOf("alpha", "beta"), names);
    }

    // ----------------------------------------------------------------------
    // Fixture helpers
    // ----------------------------------------------------------------------

    private static FlamingockMetadata metadataWithDefaultStage(String name,
                                                               String sourcesPackage,
                                                               List<AbstractPreviewChange> changes) {
        return metadataWithStage(StageType.DEFAULT, name, sourcesPackage, changes);
    }

    private static FlamingockMetadata metadataWithStage(StageType type,
                                                        String name,
                                                        String sourcesPackage,
                                                        List<AbstractPreviewChange> changes) {
        PreviewStage stage = new PreviewStage(name, type, null, sourcesPackage, null, changes);
        FlamingockMetadata md = new FlamingockMetadata();
        md.setPipeline(new PreviewPipeline(Collections.singletonList(stage)));
        return md;
    }

    private static List<AbstractPreviewChange> changes(String... ids) {
        List<AbstractPreviewChange> result = new ArrayList<>(ids.length);
        for (String id : ids) {
            CodePreviewChange change = new CodePreviewChange();
            change.setId(id);
            result.add(change);
        }
        return result;
    }

    private static Set<String> changeIds(PreviewStage stage) {
        if (stage.getChanges() == null) return Collections.emptySet();
        Set<String> ids = new LinkedHashSet<>();
        for (AbstractPreviewChange c : stage.getChanges()) {
            ids.add(c.getId());
        }
        return ids;
    }

    private static Set<String> setOf(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
