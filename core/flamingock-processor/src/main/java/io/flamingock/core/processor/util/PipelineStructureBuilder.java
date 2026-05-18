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
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.internal.common.core.change.ChangeDescriptor;
import io.flamingock.internal.common.core.pipeline.PipelineHelper;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.preview.SystemPreviewStage;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds a fresh {@link PreviewPipeline} from a {@link EnableFlamingock} annotation plus the
 * round's {@link CodePreviewChange}s. Pure compile-time logic: validation, YAML resolution +
 * parsing, and stage assembly. No interaction with the Filer or with persisted metadata.
 *
 * <p>Two equivalent input sources, one output:
 * <ul>
 *   <li>{@code @EnableFlamingock(stages = ...)} — inline {@link Stage} annotations.</li>
 *   <li>{@code @EnableFlamingock(configFile = "...")} — a YAML pipeline definition resolved
 *       via the file-system search order documented on
 *       {@link io.flamingock.core.processor.FlamingockAnnotationProcessor}.</li>
 * </ul>
 *
 * <p>Stage discovery rules apply uniformly to both sources:
 * <ul>
 *   <li>System stage: built from changes flagged {@link CodePreviewChange#isSystem()};
 *       not declarable in YAML or annotations.</li>
 *   <li>Legacy stage: built from changes flagged {@link CodePreviewChange#isLegacy()};
 *       inserted before all DEFAULT stages.</li>
 *   <li>DEFAULT stages: declared inline (annotation) or in YAML; YAML may also declare
 *       LEGACY stages (max 1).</li>
 * </ul>
 */
public final class PipelineStructureBuilder {

    private final List<String> sourceRoots;
    private final String resourcesRoot;
    private final LoggerPreProcessor logger;

    public PipelineStructureBuilder(List<String> sourceRoots,
                                    String resourcesRoot,
                                    LoggerPreProcessor logger) {
        this.sourceRoots = sourceRoots;
        this.resourcesRoot = resourcesRoot;
        this.logger = logger;
    }

    /**
     * Build a {@link PreviewPipeline} from the round's annotation + code-changes. The returned
     * pipeline carries the structural skeleton — system/legacy/default stages with their
     * sourcesPackage/resourcesDir — and pre-places every in-round change in its target stage.
     * Changes that don't fit any stage are returned via the pipeline anyway (in their stage)
     * or, for unknown packages, simply absent from the pipeline (the caller's
     * {@link FlamingockMetadataMerger} routes them to {@code orphanChanges}).
     */
    public PreviewPipeline build(EnableFlamingock annotation,
                                 Collection<CodePreviewChange> roundChanges) {
        List<CodePreviewChange> systemChanges = roundChanges.stream()
                .filter(ChangeDescriptor::isSystem).collect(Collectors.toList());
        List<CodePreviewChange> legacyChanges = roundChanges.stream()
                .filter(CodePreviewChange::isLegacy).collect(Collectors.toList());
        List<CodePreviewChange> standardChanges = roundChanges.stream()
                .filter(ChangeDescriptor::isStandard).collect(Collectors.toList());
        Map<String, List<CodePreviewChange>> standardChangesByPackage =
                groupByPackage(standardChanges);

        boolean hasFile = !annotation.configFile().isEmpty();
        boolean hasStages = annotation.stages().length > 0;
        validateExclusive(hasFile, hasStages);

        if (hasFile) {
            logger.info("Reading flamingock pipeline from file specified in @EnableFlamingock annotation: '"
                    + annotation.configFile() + "'");
            File pipelineFile = resolvePipelineFile(annotation.configFile());
            return buildFromYaml(pipelineFile, systemChanges, legacyChanges, standardChangesByPackage);
        }
        logger.info("Reading flamingock pipeline from @EnableFlamingock annotation stages configuration");
        return buildFromAnnotation(annotation, systemChanges, legacyChanges, standardChangesByPackage);
    }

    // ---------------------------------------------------------------- annotation source

    private PreviewPipeline buildFromAnnotation(EnableFlamingock annotation,
                                                List<CodePreviewChange> systemChanges,
                                                List<CodePreviewChange> legacyChanges,
                                                Map<String, List<CodePreviewChange>> standardChangesByPackage) {
        List<PreviewStage> stages = new ArrayList<>();
        buildLegacyStageIfNeeded(legacyChanges).ifPresent(stages::add);
        for (Stage stageAnnotation : annotation.stages()) {
            stages.add(stageFromAnnotation(stageAnnotation, standardChangesByPackage));
        }
        return assemble(systemChanges, stages);
    }

    private PreviewStage stageFromAnnotation(Stage stageAnnotation,
                                             Map<String, List<CodePreviewChange>> changesByPackage) {
        String location = requireLocation(stageAnnotation.location(),
                "@Stage annotation requires a location. Please specify the location field.");
        String name = stageAnnotation.name().isEmpty()
                ? PathResolver.deriveNameFromLocation(location)
                : stageAnnotation.name();
        String description = stageAnnotation.description().isEmpty()
                ? null : stageAnnotation.description();
        return buildStage(name, description, StageType.DEFAULT, location, changesByPackage);
    }

    // ---------------------------------------------------------------- YAML source

    private PreviewPipeline buildFromYaml(File yamlFile,
                                          List<CodePreviewChange> systemChanges,
                                          List<CodePreviewChange> legacyCodedChanges,
                                          Map<String, List<CodePreviewChange>> standardChangesByPackage) {
        try (InputStream input = Files.newInputStream(yamlFile.toPath())) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(input);
            @SuppressWarnings("unchecked")
            Map<String, Object> pipelineMap = (Map<String, Object>) config.get("pipeline");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> stageList = (List<Map<String, String>>) pipelineMap.get("stages");

            validateYamlStageTypes(stageList);

            List<PreviewStage> stages = new ArrayList<>();
            for (Map<String, String> stageMap : stageList) {
                stages.add(stageFromYaml(stageMap, standardChangesByPackage));
            }
            buildLegacyStageIfNeeded(legacyCodedChanges).ifPresent(stages::add);
            return assemble(systemChanges, stages);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private PreviewStage stageFromYaml(Map<String, String> stageMap,
                                       Map<String, List<CodePreviewChange>> changesByPackage) {
        String location = requireLocation(stageMap.get("location"),
                "Stage in YAML pipeline requires a 'location' field. Please specify the location where changes are found.");
        String rawName = stageMap.get("name");
        String name = (rawName == null || rawName.trim().isEmpty())
                ? PathResolver.deriveNameFromLocation(location)
                : rawName;
        return buildStage(name, stageMap.get("description"),
                StageType.from(stageMap.get("type")), location, changesByPackage);
    }

    // ---------------------------------------------------------------- shared stage build

    /**
     * Common DEFAULT/LEGACY-stage construction. {@code location} is interpreted as either a
     * package or a resource directory; the corresponding code-changes are looked up only when
     * it is a package.
     */
    private PreviewStage buildStage(String name,
                                    String description,
                                    StageType type,
                                    String location,
                                    Map<String, List<CodePreviewChange>> changesByPackage) {
        logger.verbose("Building stage: " + name);

        String sourcesPackage = null;
        String resourcesDir = null;
        Collection<CodePreviewChange> changeClasses = null;

        if (PathResolver.isPackageName(location)) {
            sourcesPackage = location;
            changeClasses = changesByPackage.get(sourcesPackage);
            logger.verbose("Sources package: " + sourcesPackage);
            if (changeClasses != null) {
                logger.verbose("Found " + changeClasses.size() + " code-based changes in " + sourcesPackage);
            }
        } else {
            resourcesDir = PathResolver.processResourceLocation(location);
            logger.verbose("Resources directory: " + resourcesDir);
        }

        return PreviewStage.defaultBuilder(type)
                .setName(name)
                .setDescription(description)
                .setSourcesRoots(sourceRoots)
                .setSourcesPackage(sourcesPackage)
                .setResourcesRoot(resourcesRoot)
                .setResourcesDir(resourcesDir)
                .setChanges(changeClasses)
                .build();
    }

    private Optional<SystemPreviewStage> buildSystemStageIfNeeded(List<CodePreviewChange> systemChanges) {
        if (systemChanges.isEmpty()) {
            return Optional.empty();
        }
        logger.verbose("Building stage: " + PipelineHelper.SYSTEM_STAGE_ID);
        SystemPreviewStage systemStage = PreviewStage.systemBuilder()
                .setName(PipelineHelper.SYSTEM_STAGE_ID)
                .setDescription("Dedicated stage for system-level changes")
                .setSourcesRoots(sourceRoots)
                .setSourcesPackage(null)
                .setResourcesRoot(resourcesRoot)
                .setResourcesDir(null)
                .setChanges(systemChanges)
                .build();
        return Optional.of(systemStage);
    }

    /**
     * Build a legacy stage from {@code @Change(legacy=true)} classes. Their members come from
     * arbitrary packages tagged as legacy, so the stage has no single sourcesPackage —
     * {@code null} is intentional here.
     */
    private Optional<PreviewStage> buildLegacyStageIfNeeded(List<CodePreviewChange> legacyChanges) {
        if (legacyChanges == null || legacyChanges.isEmpty()) {
            return Optional.empty();
        }
        logger.verbose("Building stage: " + PipelineHelper.LEGACY_STAGE_ID);
        PreviewStage legacyStage = PreviewStage.defaultBuilder(StageType.LEGACY)
                .setName(PipelineHelper.LEGACY_STAGE_ID)
                .setDescription("Flamingock legacy stage")
                .setSourcesRoots(sourceRoots)
                .setSourcesPackage(null)
                .setResourcesRoot(resourcesRoot)
                .setResourcesDir(null)
                .setChanges(legacyChanges)
                .build();
        return Optional.of(legacyStage);
    }

    private PreviewPipeline assemble(List<CodePreviewChange> systemChanges, List<PreviewStage> stages) {
        stages.sort(PipelineStructureBuilder::compareStagesByTypePriority);
        return buildSystemStageIfNeeded(systemChanges)
                .map(systemStage -> new PreviewPipeline(systemStage, stages))
                .orElseGet(() -> new PreviewPipeline(stages));
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, List<CodePreviewChange>> groupByPackage(Collection<CodePreviewChange> changes) {
        Map<String, List<CodePreviewChange>> byPackage = new HashMap<>();
        for (CodePreviewChange change : changes) {
            byPackage.computeIfAbsent(change.getSourcePackage(), k -> new ArrayList<>()).add(change);
        }
        return byPackage;
    }

    private static String requireLocation(String location, String errorMessage) {
        if (location == null || location.trim().isEmpty()) {
            throw new RuntimeException(errorMessage);
        }
        return location;
    }

    private static void validateExclusive(boolean hasFile, boolean hasStages) {
        if (hasFile && hasStages) {
            throw new RuntimeException(
                    "@EnableFlamingock annotation cannot have both configFile and stages configured. "
                            + "Choose one: either specify configFile OR stages.");
        }
    }

    private static void validateYamlStageTypes(List<Map<String, String>> stageList) {
        int system = 0;
        int legacy = 0;
        for (Map<String, String> stageMap : stageList) {
            StageType type = StageType.from(stageMap.get("type"));
            if (type == StageType.SYSTEM && ++system > 1) {
                throw new RuntimeException(
                        "Multiple SYSTEM stages are not allowed in YAML pipeline. "
                                + "Only one stage with type 'system' is permitted.");
            }
            if (type == StageType.LEGACY && ++legacy > 1) {
                throw new RuntimeException(
                        "Multiple LEGACY stages are not allowed in YAML pipeline. "
                                + "Only one stage with type 'legacy' is permitted.");
            }
        }
    }

    /** Priority: SYSTEM=0, LEGACY=1, DEFAULT=2. SYSTEM is wrapped separately so the only
     *  values that ever reach the comparator are LEGACY and DEFAULT. */
    private static int compareStagesByTypePriority(PreviewStage a, PreviewStage b) {
        return stageTypePriority(a.getType()) - stageTypePriority(b.getType());
    }

    private static int stageTypePriority(StageType type) {
        switch (type) {
            case SYSTEM:  return 0;
            case LEGACY:  return 1;
            case DEFAULT: return 2;
            default:      return 3;
        }
    }

    // ---------------------------------------------------------------- pipeline file resolution

    /**
     * Resolve a YAML pipeline path declared in {@code @EnableFlamingock(configFile = ...)}.
     * Search order (first match wins): direct path, main resources, test resources, plus
     * the same two with a leading {@code resources/} stripped.
     */
    File resolvePipelineFile(String configFilePath) {
        List<File> searched = new ArrayList<>();

        File result = tryResolveFile(new File(configFilePath), "direct file path", searched);
        if (result != null) return result;

        result = tryResolveFile(new File(resourcesRoot + "/" + configFilePath),
                "main resources", searched);
        if (result != null) return result;

        String testResourcesRoot = resourcesRoot.replace("src/main/resources", "src/test/resources");
        result = tryResolveFile(new File(testResourcesRoot + "/" + configFilePath),
                "test resources", searched);
        if (result != null) return result;

        if (configFilePath.startsWith("resources/")) {
            String stripped = configFilePath.substring("resources/".length());
            result = tryResolveFile(new File(resourcesRoot + "/" + stripped),
                    "main resources (stripped resources/ prefix)", searched);
            if (result != null) return result;

            result = tryResolveFile(new File(testResourcesRoot + "/" + stripped),
                    "test resources (stripped resources/ prefix)", searched);
            if (result != null) return result;
        }

        StringBuilder locations = new StringBuilder("Searched locations:");
        for (int i = 0; i < searched.size(); i++) {
            locations.append(String.format("\n  %d. %s", i + 1, searched.get(i).getAbsolutePath()));
        }
        throw new RuntimeException(
                "Pipeline file specified in @EnableFlamingock annotation does not exist: "
                        + configFilePath + "\n" + locations);
    }

    private File tryResolveFile(File file, String description, List<File> searched) {
        searched.add(file);
        if (file.exists()) {
            logger.info("Pipeline file resolved as " + description + ": " + file.getAbsolutePath());
            return file;
        }
        return null;
    }

}
