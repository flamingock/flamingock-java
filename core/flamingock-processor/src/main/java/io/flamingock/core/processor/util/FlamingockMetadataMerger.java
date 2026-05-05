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
import io.flamingock.internal.common.core.pipeline.PipelineHelper;
import io.flamingock.internal.common.core.preview.AbstractPreviewChange;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.preview.SystemPreviewStage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merge policy for {@link FlamingockMetadata}. Pure functions, no I/O.
 *
 * <p>Used by the annotation processor to combine the freshly discovered metadata of a single
 * compilation round with the metadata cached from previous rounds (read from
 * {@code META-INF/flamingock/metadata.json}).
 *
 * <p><b>Placement model</b>: structure (stages with names + packages) is decoupled from change
 * placement. Every {@link CodePreviewChange} candidate (in-round + previously-known + previous
 * orphans) is routed via:
 * <ol>
 *   <li>If {@link CodePreviewChange#isSystem()} → place into the system stage</li>
 *   <li>If {@link CodePreviewChange#isLegacy()} → place into the legacy stage</li>
 *   <li>Otherwise → most-specific {@code sourcesPackage}-covers match across DEFAULT stages
 *       (longest stage package wins on ties)</li>
 * </ol>
 * Unplaced candidates become orphans. Templates ({@code TemplatePreviewChange}) are file-driven
 * and never re-placed by this merger — they remain in the stage where the YAML scan put them.
 */
public final class FlamingockMetadataMerger {

    private FlamingockMetadataMerger() {
    }

    /**
     * Backward-compatible 3-arg form: equivalent to {@link #mergePipeline(FlamingockMetadata,
     * PreviewPipeline, Collection, boolean)} with no explicit in-round changes (any code changes
     * pre-placed in {@code freshPipeline} are treated as in-round candidates).
     */
    public static void mergePipeline(FlamingockMetadata target,
                                     PreviewPipeline freshPipeline,
                                     boolean strictStageMapping) {
        mergePipeline(target, freshPipeline, Collections.emptyList(), strictStageMapping);
    }

    /**
     * Merge a freshly built pipeline structure with the existing metadata, re-placing every
     * known code change via the routing rules above. Templates in {@code freshStructure} are
     * preserved as-is. {@code target.orphanChanges} is rebuilt from scratch each call.
     *
     * @param target              metadata to mutate
     * @param freshStructure      fresh pipeline (may have pre-placed code changes — they're
     *                            treated as in-round; templates are preserved)
     * @param inRoundChanges      explicit in-round code changes (highest priority on id collision)
     * @param strictStageMapping  persisted on target for runtime consumption
     */
    public static void mergePipeline(FlamingockMetadata target,
                                     PreviewPipeline freshStructure,
                                     Collection<CodePreviewChange> inRoundChanges,
                                     boolean strictStageMapping) {
        if (freshStructure == null) {
            target.setStrictStageMapping(strictStageMapping);
            return;
        }

        // 1. Build candidate map by id with the priority order:
        //    explicit in-round > pre-placed in freshStructure > existing pipeline > orphans.
        Map<String, CodePreviewChange> candidatesById = new LinkedHashMap<>();
        if (inRoundChanges != null) {
            for (CodePreviewChange c : inRoundChanges) {
                if (c != null && c.getId() != null) {
                    candidatesById.put(c.getId(), c);
                }
            }
        }
        addCodeChangesIfAbsent(freshStructure, candidatesById);
        PreviewPipeline existing = target.getPipeline();
        if (existing != null) {
            addCodeChangesIfAbsent(existing, candidatesById);
        }
        if (target.getOrphanChanges() != null) {
            for (CodePreviewChange c : target.getOrphanChanges()) {
                if (c != null && c.getId() != null) {
                    candidatesById.putIfAbsent(c.getId(), c);
                }
            }
        }

        // 2. Strip code changes from freshStructure (templates remain).
        clearCodeChangesOnly(freshStructure);

        // 3. Conditionally add system/legacy stages if any candidate flag demands one.
        ensureSystemStageIfNeeded(freshStructure, candidatesById.values());
        ensureLegacyStageIfNeeded(freshStructure, candidatesById.values());

        // 4. Install fresh structure, reset orphans, persist strict flag.
        target.setPipeline(freshStructure);
        target.setOrphanChanges(new ArrayList<>());
        target.setStrictStageMapping(strictStageMapping);

        // 5. Route and place each candidate.
        List<CodePreviewChange> orphans = new ArrayList<>();
        for (CodePreviewChange c : candidatesById.values()) {
            if (!routeAndPlace(freshStructure, c)) {
                orphans.add(c);
            }
        }

        if (!orphans.isEmpty()) {
            addOrphans(target, orphans);
        }
    }

    /**
     * For an incremental round without {@code @EnableFlamingock}: upsert each new change into
     * the existing pipeline's matching stage by package coverage (exact or strict parent).
     *
     * @return the changes that could not be placed (caller should hand to {@link #addOrphans})
     */
    public static List<CodePreviewChange> upsertChangesByPackage(FlamingockMetadata target,
                                                                 Collection<CodePreviewChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return Collections.emptyList();
        }
        PreviewPipeline pipeline = target.getPipeline();
        if (pipeline == null) {
            return new ArrayList<>(changes);
        }

        Map<PreviewStage, List<CodePreviewChange>> grouped = new LinkedHashMap<>();
        List<CodePreviewChange> unmapped = new ArrayList<>();
        for (CodePreviewChange change : changes) {
            PreviewStage match = findMostSpecificStageForChange(pipeline, change);
            if (match == null) {
                unmapped.add(change);
            } else {
                grouped.computeIfAbsent(match, k -> new ArrayList<>()).add(change);
            }
        }

        for (Map.Entry<PreviewStage, List<CodePreviewChange>> entry : grouped.entrySet()) {
            upsertChangesInPlace(entry.getKey(), entry.getValue());
        }

        return unmapped;
    }

    /**
     * Append {@code incoming} to {@code target.orphanChanges} with id-based replace-on-conflict.
     */
    public static void addOrphans(FlamingockMetadata target, Collection<CodePreviewChange> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        List<CodePreviewChange> current = target.getOrphanChanges();
        if (current == null) {
            current = new ArrayList<>();
            target.setOrphanChanges(current);
        }
        Map<String, CodePreviewChange> incomingById = new LinkedHashMap<>();
        for (CodePreviewChange change : incoming) {
            if (change.getId() != null) {
                incomingById.put(change.getId(), change);
            }
        }
        Set<String> consumed = new HashSet<>();
        for (int i = 0; i < current.size(); i++) {
            String id = current.get(i).getId();
            if (id != null && incomingById.containsKey(id)) {
                current.set(i, incomingById.get(id));
                consumed.add(id);
            }
        }
        for (Map.Entry<String, CodePreviewChange> e : incomingById.entrySet()) {
            if (!consumed.contains(e.getKey())) {
                current.add(e.getValue());
            }
        }
    }

    /** {@code putAll}; incoming wins on key clash. Initializes the map if {@code null}. */
    public static void mergeProperties(FlamingockMetadata target, Map<String, String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        Map<String, String> current = target.getProperties();
        if (current == null) {
            current = new HashMap<>();
            target.setProperties(current);
        }
        current.putAll(incoming);
    }

    /** Replace the whole field. */
    public static void setBuilderProvider(FlamingockMetadata target, BuilderProviderInfo info) {
        target.setBuilderProvider(info);
    }

    /**
     * Iterate every {@link CodePreviewChange} in stages + system stage + orphans and remove
     * those whose source class no longer exists per {@code typeExists}.
     *
     * @return true if anything was pruned
     */
    public static boolean pruneDeletedClasses(FlamingockMetadata target,
                                              java.util.function.Predicate<String> typeExists) {
        boolean pruned = false;
        PreviewPipeline pipeline = target.getPipeline();
        if (pipeline != null) {
            if (pipeline.getSystemStage() != null) {
                pruned |= pruneStage(pipeline.getSystemStage(), typeExists);
            }
            if (pipeline.getStages() != null) {
                for (PreviewStage s : pipeline.getStages()) {
                    pruned |= pruneStage(s, typeExists);
                }
            }
        }
        if (target.getOrphanChanges() != null && !target.getOrphanChanges().isEmpty()) {
            List<CodePreviewChange> kept = new ArrayList<>();
            for (CodePreviewChange c : target.getOrphanChanges()) {
                if (c.getSource() != null && typeExists.test(c.getSource())) {
                    kept.add(c);
                } else {
                    pruned = true;
                }
            }
            if (pruned) {
                target.setOrphanChanges(kept);
            }
        }
        return pruned;
    }

    // -------------------------- internals --------------------------

    /** Add every CodePreviewChange in {@code pipeline}'s stages to {@code sink} if id absent. */
    private static void addCodeChangesIfAbsent(PreviewPipeline pipeline,
                                               Map<String, CodePreviewChange> sink) {
        if (pipeline == null) return;
        addCodeChangesIfAbsent(pipeline.getSystemStage(), sink);
        if (pipeline.getStages() != null) {
            for (PreviewStage s : pipeline.getStages()) {
                addCodeChangesIfAbsent(s, sink);
            }
        }
    }

    private static void addCodeChangesIfAbsent(PreviewStage stage,
                                               Map<String, CodePreviewChange> sink) {
        if (stage == null || stage.getChanges() == null) return;
        for (AbstractPreviewChange c : stage.getChanges()) {
            if (c instanceof CodePreviewChange && c.getId() != null) {
                sink.putIfAbsent(c.getId(), (CodePreviewChange) c);
            }
        }
    }

    /** Remove all CodePreviewChange instances from every stage in {@code pipeline}; keep templates. */
    private static void clearCodeChangesOnly(PreviewPipeline pipeline) {
        if (pipeline == null) return;
        if (pipeline.getSystemStage() != null) {
            stripCodeChanges(pipeline.getSystemStage());
        }
        if (pipeline.getStages() != null) {
            for (PreviewStage s : pipeline.getStages()) {
                stripCodeChanges(s);
            }
        }
    }

    private static void stripCodeChanges(PreviewStage stage) {
        Collection<? extends AbstractPreviewChange> current = stage.getChanges();
        if (current == null || current.isEmpty()) return;
        List<AbstractPreviewChange> templatesOnly = new ArrayList<>();
        for (AbstractPreviewChange c : current) {
            if (!(c instanceof CodePreviewChange)) {
                templatesOnly.add(c);
            }
        }
        stage.setChanges(templatesOnly);
    }

    /** If any candidate is system-flagged and pipeline lacks a system stage, add one. */
    private static void ensureSystemStageIfNeeded(PreviewPipeline pipeline,
                                                  Collection<CodePreviewChange> candidates) {
        if (pipeline.getSystemStage() != null) return;
        for (CodePreviewChange c : candidates) {
            if (c.isSystem()) {
                pipeline.setSystemStage(new SystemPreviewStage(
                        PipelineHelper.SYSTEM_STAGE_ID,
                        "Dedicated stage for system-level changes",
                        null, null, new ArrayList<>()));
                return;
            }
        }
    }

    /** If any candidate is legacy-flagged and pipeline lacks a legacy stage, add one. */
    private static void ensureLegacyStageIfNeeded(PreviewPipeline pipeline,
                                                  Collection<CodePreviewChange> candidates) {
        Collection<PreviewStage> stages = pipeline.getStages();
        if (stages != null) {
            for (PreviewStage s : stages) {
                if (s.getType() == StageType.LEGACY) return;
            }
        }
        for (CodePreviewChange c : candidates) {
            if (c.isLegacy()) {
                PreviewStage legacy = new PreviewStage(
                        PipelineHelper.LEGACY_STAGE_ID, StageType.LEGACY,
                        "Flamingock legacy stage", null, null, new ArrayList<>());
                List<PreviewStage> mutable = (stages == null)
                        ? new ArrayList<>() : new ArrayList<>(stages);
                mutable.add(0, legacy);  // legacy comes before default stages
                pipeline.setStages(mutable);
                return;
            }
        }
    }

    /**
     * Route a change to the right stage and append it. Returns true if placed.
     */
    private static boolean routeAndPlace(PreviewPipeline pipeline, CodePreviewChange change) {
        if (change.isSystem()) {
            if (pipeline.getSystemStage() != null) {
                appendToStage(pipeline.getSystemStage(), change);
                return true;
            }
            return false;
        }
        if (change.isLegacy()) {
            PreviewStage legacy = findLegacyStage(pipeline);
            if (legacy != null) {
                appendToStage(legacy, change);
                return true;
            }
            return false;
        }
        PreviewStage best = findMostSpecificDefaultStage(pipeline, change.getSourcePackage());
        if (best != null) {
            appendToStage(best, change);
            return true;
        }
        return false;
    }

    private static PreviewStage findLegacyStage(PreviewPipeline pipeline) {
        if (pipeline.getStages() == null) return null;
        for (PreviewStage s : pipeline.getStages()) {
            if (s.getType() == StageType.LEGACY) return s;
        }
        return null;
    }

    /**
     * Most-specific (longest stage sourcesPackage) covers match across DEFAULT stages.
     * Stable: when two stages have the same length, the first one in iteration order wins.
     */
    private static PreviewStage findMostSpecificDefaultStage(PreviewPipeline pipeline,
                                                             String changePkg) {
        if (changePkg == null || pipeline.getStages() == null) return null;
        List<PreviewStage> candidates = new ArrayList<>();
        for (PreviewStage s : pipeline.getStages()) {
            if (s.getType() == StageType.DEFAULT
                    && covers(s.getSourcesPackage(), changePkg)) {
                candidates.add(s);
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.<PreviewStage>comparingInt(
                s -> s.getSourcesPackage() == null ? -1 : s.getSourcesPackage().length()
        ).reversed());
        return candidates.get(0);
    }

    /** Covers test used by {@link #upsertChangesByPackage}: most-specific covers match. */
    private static PreviewStage findMostSpecificStageForChange(PreviewPipeline pipeline,
                                                               CodePreviewChange change) {
        String changePkg = change.getSourcePackage();
        if (changePkg == null) return null;
        List<PreviewStage> candidates = new ArrayList<>();
        if (pipeline.getSystemStage() != null
                && covers(pipeline.getSystemStage().getSourcesPackage(), changePkg)) {
            candidates.add(pipeline.getSystemStage());
        }
        if (pipeline.getStages() != null) {
            for (PreviewStage s : pipeline.getStages()) {
                if (covers(s.getSourcesPackage(), changePkg)) {
                    candidates.add(s);
                }
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.<PreviewStage>comparingInt(
                s -> s.getSourcesPackage() == null ? -1 : s.getSourcesPackage().length()
        ).reversed());
        return candidates.get(0);
    }

    private static void appendToStage(PreviewStage stage, CodePreviewChange change) {
        Collection<? extends AbstractPreviewChange> current = stage.getChanges();
        List<AbstractPreviewChange> next = new ArrayList<>();
        if (current != null) next.addAll(current);
        next.add(change);
        stage.setChanges(next);
    }

    /**
     * Replace existing changes in {@code stage} that share an id with one in {@code newChanges},
     * append the rest. Builds a fresh list and assigns via {@code setChanges}.
     */
    private static void upsertChangesInPlace(PreviewStage stage, List<CodePreviewChange> newChanges) {
        Collection<? extends AbstractPreviewChange> current = stage.getChanges();
        Map<String, CodePreviewChange> incomingById = new LinkedHashMap<>();
        for (CodePreviewChange c : newChanges) {
            if (c.getId() != null) {
                incomingById.put(c.getId(), c);
            }
        }
        List<AbstractPreviewChange> merged = new ArrayList<>();
        Set<String> consumed = new HashSet<>();
        if (current != null) {
            for (AbstractPreviewChange c : current) {
                if (c.getId() != null && incomingById.containsKey(c.getId())) {
                    merged.add(incomingById.get(c.getId()));
                    consumed.add(c.getId());
                } else {
                    merged.add(c);
                }
            }
        }
        for (Map.Entry<String, CodePreviewChange> e : incomingById.entrySet()) {
            if (!consumed.contains(e.getKey())) {
                merged.add(e.getValue());
            }
        }
        stage.setChanges(merged);
    }

    private static boolean pruneStage(PreviewStage stage, java.util.function.Predicate<String> typeExists) {
        Collection<? extends AbstractPreviewChange> current = stage.getChanges();
        if (current == null || current.isEmpty()) return false;
        List<AbstractPreviewChange> kept = new ArrayList<>();
        boolean pruned = false;
        for (AbstractPreviewChange c : current) {
            if (c instanceof CodePreviewChange) {
                String src = c.getSource();
                if (src == null || !typeExists.test(src)) {
                    pruned = true;
                    continue;
                }
            }
            kept.add(c);
        }
        if (pruned) stage.setChanges(kept);
        return pruned;
    }

    private static boolean covers(String stagePkg, String changePkg) {
        if (stagePkg == null || changePkg == null) {
            return false;
        }
        return changePkg.equals(stagePkg) || changePkg.startsWith(stagePkg + ".");
    }
}
