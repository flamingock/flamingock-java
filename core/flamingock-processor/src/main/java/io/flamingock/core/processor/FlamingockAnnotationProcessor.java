/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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

import io.flamingock.api.StageType;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.core.processor.util.AnnotationFinder;
import io.flamingock.core.processor.util.PathResolver;
import io.flamingock.core.processor.util.ProjectRootDetector;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.preview.SystemPreviewStage;
import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;
import io.flamingock.internal.common.core.util.Serializer;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Annotation processor for Flamingock that generates metadata files containing information
 * about templated and annotated changes. The processor requires a mandatory {@link EnableFlamingock}
 * annotation to configure the pipeline.
 *
 * <h2>@Flamingock Annotation Configuration</h2>
 * The processor supports two mutually exclusive configuration modes:
 * <ul>
 *     <li><b>File-based configuration:</b> Uses {@code configFile} to reference a YAML pipeline definition</li>
 *     <li><b>Annotation-based configuration:</b> Uses {@code stages} array to define the pipeline inline</li>
 * </ul>
 *
 * <h3>Pipeline File Resolution</h3>
 * When using {@code configFile}, the processor provides resource resolution
 * supporting multiple file locations:
 *
 * <h4>Examples:</h4>
 * <pre>{@code
 * // Absolute file path - highest priority
 * &#64;EnableFlamingock(configFile = "/path/to/external/pipeline.yaml")
 * // Uses direct file system path
 *
 * // Relative file path - second priority (relative to working directory)
 * &#64;EnableFlamingock(configFile = "config/flamingock-pipeline.yaml")
 * // Resolves relative to current working directory, NOT as classpath resource
 *
 * // Classpath resource - fallback if file doesn't exist relative to working directory
 * &#64;EnableFlamingock(configFile = "flamingock/pipeline.yaml")
 * // If "flamingock/pipeline.yaml" doesn't exist in working directory,
 * // then tries: src/main/resources/flamingock/pipeline.yaml
 * // then tries: src/test/resources/flamingock/pipeline.yaml
 *
 * // Resource with explicit "resources/" prefix (automatically stripped)
 * &#64;EnableFlamingock(configFile = "resources/flamingock/pipeline.yaml")
 * // First tries: "resources/flamingock/pipeline.yaml" relative to working directory
 * // If not found, strips "resources/" prefix and tries classpath resolution:
 * // src/main/resources/flamingock/pipeline.yaml or src/test/resources/flamingock/pipeline.yaml
 * }</pre>
 *
 * <h4>Resolution Order (stops at first match):</h4>
 * <ol>
 *     <li><b>Direct file path:</b> {@code [configFile]} (absolute or relative to working directory)</li>
 *     <li><b>Main resources:</b> {@code src/main/resources/[configFile]}</li>
 *     <li><b>Test resources:</b> {@code src/test/resources/[configFile]}</li>
 *     <li><b>Main resources (stripped):</b> If path starts with "resources/", strips prefix and tries {@code src/main/resources/[remaining-path]}</li>
 *     <li><b>Test resources (stripped):</b> If path starts with "resources/", strips prefix and tries {@code src/test/resources/[remaining-path]}</li>
 * </ol>
 * <p>
 * <b>Important:</b> Working directory files always take precedence over classpath resources.
 * If both {@code ./config/pipeline.yaml} and {@code src/main/resources/config/pipeline.yaml} exist,
 * the working directory file is used.
 *
 * <h3>Annotation-based Configuration</h3>
 * <pre>{@code
 * &#64;EnableFlamingock(stages = {
 *     &#64;Stage(type = StageType.SYSTEM, location = "com.example.system"),
 *     &#64;Stage(type = StageType.LEGACY, location = "com.example.init"),
 *     &#64;Stage(location = "com.example.migrations")
 * })
 * }</pre>
 *
 * <h2>Processing Phases</h2>
 * <ul>
 *     <li><b>Initialization:</b> Sets up resource paths and validates @EnableFlamingock annotation</li>
 *     <li><b>Processing:</b> Generates {@code META-INF/flamingock/metadata-full.json} with complete pipeline metadata</li>
 * </ul>
 *
 * <h2>Validation Rules</h2>
 * <ul>
 *     <li>@EnableFlamingock annotation is mandatory</li>
 *     <li>Must specify either {@code configFile} OR {@code stages} (mutually exclusive)</li>
 *     <li>Maximum of 1 stage with type {@code StageType.SYSTEM} is allowed</li>
 *     <li>Maximum of 1 stage with type {@code StageType.LEGACY} is allowed</li>
 *     <li>Unlimited stages with type {@code StageType.DEFAULT} are allowed</li>
 * </ul>
 *
 * <h2>Supported Annotations</h2>
 * <ul>
 *     <li>{@link EnableFlamingock} - Mandatory pipeline configuration</li>
 *     <li>{@link Change} - Represents a change defined within the code</li>
 *     <li>io.mongock.api.annotations.ChangeUnit - Legacy change support</li>
 * </ul>
 *
 * @author Antonio
 * @version 2.0
 * @since Flamingock v1.x
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class FlamingockAnnotationProcessor extends AbstractProcessor {

    private static final String RESOURCES_PATH_ARG = "resources";

    private static final String SOURCES_PATH_ARG = "sources";

    private static final String DEFAULT_RESOURCES_PATH = "src/main/resources";

    private static final List<String> DEFAULT_SOURCE_DIRS = Arrays.asList(
            "src/main/java", "src/main/kotlin", "src/main/scala", "src/main/groovy"
    );


    private static boolean hasProcessed = false;

    private String resourcesRoot = null;
    private List<String> sourceRoots = null;
    private LoggerPreProcessor logger;

    public FlamingockAnnotationProcessor() {
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        logger = new LoggerPreProcessor(processingEnv);
        logger.verbose("Starting annotation processor initialization");
        resourcesRoot = getResourcesRoot();
        sourceRoots = getSourcesPathList();
        logger.verbose("Initialization completed");
    }

    @Override
    public Set<String> getSupportedOptions() {
        return new HashSet<>(Arrays.asList("sources", "resources", "flamingock.verbose"));
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<>(Arrays.asList(
                EnableFlamingock.class.getName(),
                Change.class.getName()
        ));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            logger.verbose("Final processing round - skipping");
            return false;
        }
        if (hasProcessed) {
            return true;
        }

        logger.info("Processing pipeline configuration");
        AnnotationFinder annotationFinder = new AnnotationFinder(roundEnv, logger);
        EnableFlamingock flamingockAnnotation = annotationFinder.getPipelineAnnotation()
                .orElseThrow(() -> new RuntimeException("@EnableFlamingock annotation is mandatory. Please annotate a class with @EnableFlamingock to configure the pipeline."));
        Collection<CodePreviewChange> allChanges = annotationFinder.findAnnotatedChanges();

        List<CodePreviewChange> systemChanges = allChanges.stream().filter(TaskDescriptor::isSystem).collect(Collectors.toList());
        List<CodePreviewChange> legacyChanges = allChanges.stream().filter(CodePreviewChange::isLegacy).collect(Collectors.toList());
        List<CodePreviewChange> standardChanges = allChanges.stream().filter(TaskDescriptor::isStandard).collect(Collectors.toList());

        Map<String, List<CodePreviewChange>> standardChangesMapByPackage = getCodeChangesMapByPackage(standardChanges);
        PreviewPipeline pipeline = getPipelineFromProcessChanges(
                systemChanges,
                legacyChanges,
                standardChangesMapByPackage,
                flamingockAnnotation
        );

        validateAllChangesAreMappedToStages(standardChangesMapByPackage, pipeline, flamingockAnnotation.strictStageMapping());

        Serializer serializer = new Serializer(processingEnv, logger);
        String configFile = flamingockAnnotation.configFile();
        FlamingockMetadata flamingockMetadata = new FlamingockMetadata(pipeline, configFile);
        serializer.serializeFullPipeline(flamingockMetadata);

        // Generate summary - count all changes from the final pipeline (code-based + template-based)
        int totalStages = pipeline.getStages().size() + (pipeline.getSystemStage() != null ? 1 : 0);
        int totalChanges = 0;
        if (pipeline.getSystemStage() != null && pipeline.getSystemStage().getTasks() != null) {
            totalChanges += pipeline.getSystemStage().getTasks().size();
        }
        for (PreviewStage stage : pipeline.getStages()) {
            if (stage.getTasks() != null) {
                totalChanges += stage.getTasks().size();
            }
        }
        logger.info("Generated metadata: " + totalStages + " stages, " + totalChanges + " changes");

        hasProcessed = true;
        return true;
    }

    private Map<String, List<CodePreviewChange>> getCodeChangesMapByPackage(Collection<CodePreviewChange> changes) {
        Map<String, List<CodePreviewChange>> mapByPackage = new HashMap<>();
        for (CodePreviewChange item : changes) {
            mapByPackage.compute(item.getSourcePackage(), (key, descriptors) -> {
                List<CodePreviewChange> newDescriptors;
                if (descriptors != null) {
                    newDescriptors = descriptors;
                } else {
                    newDescriptors = new ArrayList<>();
                }
                newDescriptors.add(item);
                return newDescriptors;
            });
        }
        return mapByPackage;
    }

    private PreviewPipeline getPipelineFromProcessChanges(List<CodePreviewChange> systemChanges,
                                                          List<CodePreviewChange> legacyCodedChanges,
                                                          Map<String, List<CodePreviewChange>> noLegacyCodedChangesByPackage,
                                                          EnableFlamingock pipelineAnnotation) {
        if (noLegacyCodedChangesByPackage == null) {
            noLegacyCodedChangesByPackage = new HashMap<>();
        }

        boolean hasFileInAnnotation = !pipelineAnnotation.configFile().isEmpty();
        boolean hasStagesInAnnotation = pipelineAnnotation.stages().length > 0;

        // Validate mutually exclusive modes
        validateConfiguration(pipelineAnnotation, hasFileInAnnotation, hasStagesInAnnotation);

        if (hasFileInAnnotation) {
            logger.info("Reading flamingock pipeline from file specified in @EnableFlamingock annotation: '" + pipelineAnnotation.configFile() + "'");
            File specifiedPipelineFile = resolvePipelineFile(pipelineAnnotation.configFile());
            return buildPipelineFromSpecifiedFile(specifiedPipelineFile, systemChanges, legacyCodedChanges, noLegacyCodedChangesByPackage);
        } else {
            logger.info("Reading flamingock pipeline from @EnableFlamingock annotation stages configuration");
            return buildPipelineFromAnnotation(pipelineAnnotation, systemChanges, legacyCodedChanges, noLegacyCodedChangesByPackage);
        }
    }

    /**
     * Compares stages by type priority for sorting.
     * Priority order: LEGACY (1) → DEFAULT (2)
     * SYSTEM stages are handled separately and not included in this comparison.
     *
     * @param stage1 the first stage to compare
     * @param stage2 the second stage to compare
     * @return negative if stage1 has higher priority, positive if stage2 has higher priority, 0 if equal
     */
    private int compareStagesByTypePriority(PreviewStage stage1, PreviewStage stage2) {
        return getStageTypePriority(stage1.getType()) - getStageTypePriority(stage2.getType());
    }

    /**
     * Gets the priority value for a stage type.
     * Lower values indicate higher priority (execute earlier).
     *
     * @param stageType the stage type
     * @return priority value (LEGACY: 1, DEFAULT: 2)
     */
    private int getStageTypePriority(StageType stageType) {
        switch (stageType) {
            case SYSTEM:
                return 0;
            case LEGACY:
                return 1;
            case DEFAULT:
                return 2;
            default:
                return 3;
        }
    }

    /**
     * Validates that stage types from YAML conform to the restrictions:
     * - Maximum 1 SYSTEM stage allowed
     * - Maximum 1 LEGACY stage allowed
     * - Unlimited DEFAULT stages allowed
     *
     * @param stageList the stages from YAML to validate
     * @throws RuntimeException if validation fails
     */
    private void validateStageTypesFromYaml(List<Map<String, String>> stageList) {
        int systemStageCount = 0;
        int legacyStageCount = 0;

        for (Map<String, String> stageMap : stageList) {
            StageType stageType = StageType.from(stageMap.get("type"));

            if (stageType == StageType.SYSTEM) {
                systemStageCount++;
                if (systemStageCount > 1) {
                    throw new RuntimeException("Multiple SYSTEM stages are not allowed in YAML pipeline. Only one stage with type 'system' is permitted.");
                }
            } else if (stageType == StageType.LEGACY) {
                legacyStageCount++;
                if (legacyStageCount > 1) {
                    throw new RuntimeException("Multiple LEGACY stages are not allowed in YAML pipeline. Only one stage with type 'legacy' is permitted.");
                }
            }
        }
    }

    private PreviewPipeline buildPipelineFromAnnotation(EnableFlamingock pipelineAnnotation,
                                                        List<CodePreviewChange> systemChanges,
                                                        List<CodePreviewChange> legacyCodedChanges,
                                                        Map<String, List<CodePreviewChange>> standardChangesByPackage) {
        List<PreviewStage> stages = new ArrayList<>();

        // Legacy Stage
        buildLegacyStageIfNeeded(legacyCodedChanges)
                .ifPresent(stages::add);

        // Standard Stage
        for (Stage stageAnnotation : pipelineAnnotation.stages()) {
            PreviewStage stage = mapAnnotationToStage(standardChangesByPackage, stageAnnotation);
            stages.add(stage);
        }

        // Sort stages by type priority: LEGACY stages first, then DEFAULT stages
        stages.sort(this::compareStagesByTypePriority);

        return buildSystemStageIfNeeded(systemChanges)
                .map(systemPreviewStage -> new PreviewPipeline(systemPreviewStage, stages))
                .orElseGet(() -> new PreviewPipeline(stages));
    }

    private Optional<SystemPreviewStage> buildSystemStageIfNeeded(List<CodePreviewChange> systemChanges) {
        if(!systemChanges.isEmpty()) {
            logger.verbose("Building stage: SystemStage");
            // For system stage, use hardcoded name, description, package and resource dir to maintain consistency
            SystemPreviewStage systemStage = PreviewStage.systemBuilder()
                    .setName("SystemStage")
                    .setDescription("Dedicated stage for system-level changes")
                    .setSourcesRoots(sourceRoots)
                    .setSourcesPackage(null)
                    .setResourcesRoot(resourcesRoot)
                    .setResourcesDir(null)
                    .setChanges(systemChanges)
                    .build();
            return Optional.of(systemStage);
        } else {
            return Optional.empty();
        }

    }

    private PreviewStage mapAnnotationToStage(Map<String, List<CodePreviewChange>> codedChangesByPackage, Stage stageAnnotation) {
        String location = stageAnnotation.location();

        if (location == null || location.trim().isEmpty()) {
            throw new RuntimeException("@Stage annotation requires a location. Please specify the location field.");
        }

        // Derive name from location if not provided
        String name = stageAnnotation.name();
        if (name.isEmpty()) {
            name = PathResolver.deriveNameFromLocation(location);
        }

        logger.verbose("Building stage: " + name);

        String sourcesPackage = null;
        String resourcesDir = null;
        Collection<CodePreviewChange> changeClasses = null;

        if (PathResolver.isPackageName(location)) {
            sourcesPackage = location;
            changeClasses = codedChangesByPackage.get(sourcesPackage);
            logger.verbose("Sources package: " + sourcesPackage);
            if (changeClasses != null) {
                logger.verbose("Found " + changeClasses.size() + " code-based changes in " + sourcesPackage);
            }
        } else {
            resourcesDir = PathResolver.processResourceLocation(location);
            logger.verbose("Resources directory: " + resourcesDir);
        }

        return PreviewStage.defaultBuilder(StageType.DEFAULT)
                .setName(name)
                .setDescription(stageAnnotation.description().isEmpty() ? null : stageAnnotation.description())
                .setSourcesRoots(sourceRoots)
                .setSourcesPackage(sourcesPackage)
                .setResourcesRoot(resourcesRoot)
                .setResourcesDir(resourcesDir)
                .setChanges(changeClasses)
                .build();
    }

    private Optional<PreviewStage> buildLegacyStageIfNeeded(List<CodePreviewChange> legacyChanges) {
        if (legacyChanges != null && !legacyChanges.isEmpty()) {
            PreviewStage flamingockLegacyStage = PreviewStage.defaultBuilder(StageType.LEGACY)
                    .setName("legacy-stage")
                    .setDescription("Flamingock legacy stage")
                    .setSourcesRoots(sourceRoots)
                    .setSourcesPackage(null) //TODO:
                    .setResourcesRoot(resourcesRoot)
                    .setResourcesDir(null) //TODO:
                    .setChanges(legacyChanges)
                    .build();
            return Optional.of(flamingockLegacyStage);
        }
        return Optional.empty();
    }


    /**
     * Resolves a pipeline file path from the @EnableFlamingock annotation, supporting both absolute file paths
     * and classpath resources. This method provides resource resolution for the Flamingock library.
     *
     * @param configFilePath the file path specified in the @EnableFlamingock annotation
     * @return a File object representing the resolved pipeline file
     * @throws RuntimeException if the file cannot be found in any of the supported locations
     */
    private File resolvePipelineFile(String configFilePath) {
        List<File> searchedFiles = new ArrayList<>();

        // Try direct file path first (absolute or relative to current working directory)
        File result = tryResolveFile(new File(configFilePath), "direct file path", searchedFiles);
        if (result != null) return result;

        // Try as classpath resource in main resources
        result = tryResolveFile(new File(resourcesRoot + "/" + configFilePath), "main resources", searchedFiles);
        if (result != null) return result;

        // Try as classpath resource in test resources (for annotation processing during tests)
        String testResourcesRoot = resourcesRoot.replace("src/main/resources", "src/test/resources");
        result = tryResolveFile(new File(testResourcesRoot + "/" + configFilePath), "test resources", searchedFiles);
        if (result != null) return result;

        // Try with "resources/" prefix stripped (handle cases like "resources/flamingock/pipeline.yaml")
        if (configFilePath.startsWith("resources/")) {
            String pathWithoutResourcesPrefix = configFilePath.substring("resources/".length());

            // Try in main resources without "resources/" prefix
            result = tryResolveFile(new File(resourcesRoot + "/" + pathWithoutResourcesPrefix), "main resources (stripped resources/ prefix)", searchedFiles);
            if (result != null) return result;

            // Try in test resources without "resources/" prefix
            result = tryResolveFile(new File(testResourcesRoot + "/" + pathWithoutResourcesPrefix), "test resources (stripped resources/ prefix)", searchedFiles);
            if (result != null) return result;
        }

        // If all resolution attempts failed, provide helpful error message
        StringBuilder searchedLocations = new StringBuilder("Searched locations:");
        for (int i = 0; i < searchedFiles.size(); i++) {
            searchedLocations.append(String.format("\n  %d. %s", i + 1, searchedFiles.get(i).getAbsolutePath()));
        }

        throw new RuntimeException(
                "Pipeline file specified in @EnableFlamingock annotation does not exist: " + configFilePath + "\n" +
                        searchedLocations
        );
    }


    private File tryResolveFile(File file, String description, List<File> searchedFiles) {
        searchedFiles.add(file);
        if (file.exists()) {
            logger.info("Pipeline file resolved as " + description + ": " + file.getAbsolutePath());
            return file;
        }
        return null;
    }

    private PreviewPipeline buildPipelineFromSpecifiedFile(File file,
                                                           List<CodePreviewChange> systemChanges,
                                                           List<CodePreviewChange> legacyCodedChanges,
                                                           Map<String, List<CodePreviewChange>> standardChangesByPackage) {

        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(inputStream);

            Map<String, Object> pipelineMap = (Map<String, Object>) config.get("pipeline");

            List<Map<String, String>> stageList = (List<Map<String, String>>) pipelineMap.get("stages");

            // Validate stage types from YAML configuration
            validateStageTypesFromYaml(stageList);

            List<PreviewStage> stages = new ArrayList<>();


            for (Map<String, String> stageMap : stageList) {
                PreviewStage stage = mapToStage(standardChangesByPackage, stageMap);
                stages.add(stage);
            }

            // Legacy Stage
            buildLegacyStageIfNeeded(legacyCodedChanges)
                    .ifPresent(stages::add);

            // Sort stages by type priority: LEGACY stages first, then DEFAULT stages
            stages.sort(this::compareStagesByTypePriority);

            return buildSystemStageIfNeeded(systemChanges)
                    .map(systemPreviewStage -> new PreviewPipeline(systemPreviewStage, stages))
                    .orElseGet(() -> new PreviewPipeline(stages));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private SystemPreviewStage mapToSystemStage(Map<String, List<CodePreviewChange>> codedChangesByPackage, Map<String, String> stageMap) {
        String location = stageMap.get("location");

        if (location == null || location.trim().isEmpty()) {
            throw new RuntimeException("System stage in YAML pipeline requires a 'location' field. Please specify the location where changes are found.");
        }

        logger.verbose("Building stage: SystemStage");

        String sourcesPackage = null;
        String resourcesDir = null;
        Collection<CodePreviewChange> changeClasses = null;

        if (PathResolver.isPackageName(location)) {
            sourcesPackage = location;
            changeClasses = codedChangesByPackage.get(sourcesPackage);
            logger.verbose("Sources package: " + sourcesPackage);
            if (changeClasses != null) {
                logger.verbose("Found " + changeClasses.size() + " code-based changes in " + sourcesPackage);
            }
        } else {
            resourcesDir = PathResolver.processResourceLocation(location);
            logger.verbose("Resources directory: " + resourcesDir);
        }

        // For system stage, use hardcoded name and description to maintain consistency
        return PreviewStage.systemBuilder()
                .setName("SystemStage")
                .setDescription("Dedicated stage for system-level changes")
                .setSourcesRoots(sourceRoots)
                .setSourcesPackage(sourcesPackage)
                .setResourcesRoot(resourcesRoot)
                .setResourcesDir(resourcesDir)
                .setChanges(changeClasses)
                .build();
    }

    private PreviewStage mapToStage(Map<String, List<CodePreviewChange>> codedChangesByPackage, Map<String, String> stageMap) {

        String location = stageMap.get("location");

        if (location == null || location.trim().isEmpty()) {
            throw new RuntimeException("Stage in YAML pipeline requires a 'location' field. Please specify the location where changes are found.");
        }

        String name = stageMap.get("name");
        if (name == null || name.trim().isEmpty()) {
            name = PathResolver.deriveNameFromLocation(location);
        }

        logger.verbose("Building stage: " + name);

        String sourcesPackage = null;
        String resourcesDir = null;
        Collection<CodePreviewChange> changeClasses = null;

        if (PathResolver.isPackageName(location)) {
            sourcesPackage = location;
            changeClasses = codedChangesByPackage.get(sourcesPackage);
            logger.verbose("Sources package: " + sourcesPackage);
            if (changeClasses != null) {
                logger.verbose("Found " + changeClasses.size() + " code-based changes in " + sourcesPackage);
            }
        } else {
            resourcesDir = PathResolver.processResourceLocation(location);
            logger.verbose("Resources directory: " + resourcesDir);
        }

        return PreviewStage.defaultBuilder(StageType.from(stageMap.get("type")))
                .setName(name)
                .setDescription(stageMap.get("description"))
                .setSourcesRoots(sourceRoots)
                .setSourcesPackage(sourcesPackage)
                .setResourcesRoot(resourcesRoot)
                .setResourcesDir(resourcesDir)
                .setChanges(changeClasses)
                .build();
    }

    @NotNull
    private List<String> getSourcesPathList() {
        // Priority 1: Use explicitly provided parameter
        if (processingEnv.getOptions().containsKey(SOURCES_PATH_ARG)) {
            String sourcesPath = processingEnv.getOptions().get(SOURCES_PATH_ARG);
            logger.verbose("Using explicitly provided sources path: " + sourcesPath);
            return Collections.singletonList(sourcesPath);
        }

        // Priority 2: Auto-detect project root and convert to absolute paths
        File projectRoot = ProjectRootDetector.detectProjectRoot(processingEnv);
        if (projectRoot != null) {
            logger.info("Auto-detected project root: " + projectRoot.getAbsolutePath());
            List<String> absolutePaths = ProjectRootDetector.toAbsoluteSourcePaths(projectRoot, DEFAULT_SOURCE_DIRS);
            logger.verbose("Source paths: " + absolutePaths);
            return absolutePaths;
        }

        // Priority 3: Fall back to relative paths
        logger.warn("Could not auto-detect project root, using relative paths (may fail in some IDEs)");
        logger.verbose("Using relative source paths: " + DEFAULT_SOURCE_DIRS);
        return DEFAULT_SOURCE_DIRS;
    }

    @NotNull
    private String getResourcesRoot() {
        final String resourcesDir;
        if (processingEnv.getOptions().containsKey(RESOURCES_PATH_ARG)) {
            resourcesDir = processingEnv.getOptions().get(RESOURCES_PATH_ARG);
            logger.verbose("Using explicitly provided resources path: " + resourcesDir);
        } else {
            resourcesDir = DEFAULT_RESOURCES_PATH;
            logger.verbose("Resources root: " + resourcesDir);
        }
        return resourcesDir;
    }


    private void validateConfiguration(EnableFlamingock pipelineAnnotation, boolean hasFileInAnnotation, boolean hasStagesInAnnotation) {
        if (hasFileInAnnotation && hasStagesInAnnotation) {
            throw new RuntimeException("@EnableFlamingock annotation cannot have both configFile and stages configured. Choose one: either specify configFile OR stages.");
        }

        if (!hasFileInAnnotation && !hasStagesInAnnotation) {
            throw new RuntimeException("@EnableFlamingock annotation must specify either configFile OR stages configuration.");
        }

    }

    /**
     * Validates that code-based preview changes (non-legacy) are attached to some stage.
     * It checks package names from the changes map against the pipeline stages' sourcesPackage
     * (including the SYSTEM stage). If any stage does not represent any package, a RuntimeException is thrown.
     *
     * @param standardChanges non-legacy changes grouped by source package
     * @param pipeline built preview pipeline
     * @param strictStageMapping if true, unmapped changes will throw an exception; if false, will log a warning
     */
    private void validateAllChangesAreMappedToStages(Map<String, List<CodePreviewChange>> standardChanges,
                                                     PreviewPipeline pipeline,
                                                     Boolean strictStageMapping) {
        if (standardChanges == null || standardChanges.isEmpty()) {
            return;
        }

        // Helper to test if a change package is covered by a stage package (exact or parent)
        BiPredicate<String, String> covers = (stagePkg, changePkg) -> {
            if (stagePkg == null || changePkg == null) return false;
            return changePkg.equals(stagePkg) || changePkg.startsWith(stagePkg + ".");
        };

        List<String> unmapped = new ArrayList<>();

        for (String pkg : standardChanges.keySet()) {
            if (pkg == null) {
                continue;
            }
            boolean matched = false;

            // Check system stage
            PreviewStage system = pipeline.getSystemStage();
            if (system != null) {
                String sysPkg = system.getSourcesPackage();
                if (covers.test(sysPkg, pkg)) {
                    matched = true;
                } else if (system.getTasks() != null) {
                    for (io.flamingock.internal.common.core.preview.AbstractPreviewTask task : system.getTasks()) {
                        if (task instanceof CodePreviewChange) {
                            String taskPkg = ((CodePreviewChange) task).getSourcePackage();
                            if (covers.test(taskPkg, pkg)) {
                                matched = true;
                                break;
                            }
                        }
                    }
                }
            }

            // Check regular stages
            if (!matched && pipeline.getStages() != null) {
                for (PreviewStage stage : pipeline.getStages()) {
                    String stagePkg = stage.getSourcesPackage();
                    if (covers.test(stagePkg, pkg)) {
                        matched = true;
                        break;
                    }
                    if (stage.getTasks() != null) {
                        for (io.flamingock.internal.common.core.preview.AbstractPreviewTask task : stage.getTasks()) {
                            if (task instanceof CodePreviewChange) {
                                String taskPkg = ((CodePreviewChange) task).getSourcePackage();
                                if (covers.test(taskPkg, pkg)) {
                                    matched = true;
                                    break;
                                }
                            }
                        }
                        if (matched) break;
                    }
                }
            }

            if (!matched) {
                unmapped.add(pkg);
            }
        }

        if (!unmapped.isEmpty()) {
            String message = "Changes are not mapped to any stage: " + String.join(", ", unmapped);
            if (Boolean.TRUE.equals(strictStageMapping)) {
                throw new RuntimeException(message);
            } else {
                logger.warn(message);
            }
        }
    }
}
