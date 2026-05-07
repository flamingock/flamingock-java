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

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.FlamingockCliBuilder;
import io.flamingock.core.processor.util.FlamingockMetadataMerger;
import io.flamingock.core.processor.util.FlamingockMetadataStore;
import io.flamingock.core.processor.util.MetadataModuleIdentity;
import io.flamingock.core.processor.util.MetadataProviderWriter;
import io.flamingock.core.processor.util.PipelineStructureBuilder;
import io.flamingock.core.processor.util.ProjectRootDetector;
import io.flamingock.core.processor.util.RoundDiscovery;
import io.flamingock.core.processor.util.RoundInputs;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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


    private boolean hasProcessed = false;

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
                Change.class.getName(),
                FlamingockCliBuilder.class.getName()
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
        RoundInputs inputs = new RoundDiscovery(processingEnv, logger).discover(roundEnv);
        if (inputs.isEmpty()) {
            logger.verbose("No Flamingock-relevant elements in this round; skipping.");
            return false;
        }

        // Resolve this module's persistent identity (provider class FQN + metadata file path).
        // First build → fresh suffix; subsequent builds → reuse the suffix encoded in the
        // existing META-INF/services SPI registration.
        MetadataModuleIdentity identity = MetadataModuleIdentity.resolve(processingEnv, logger);
        if (!identity.isPersisted()) {
            MetadataProviderWriter.write(processingEnv, identity, logger);
        }

        FlamingockMetadataStore store = new FlamingockMetadataStore(processingEnv, logger,
                identity.getMetadataResourcePath(), identity.getReflectClassesResourcePath());

        applyStructureAndChanges(store, inputs);
        applyBuilderProvider(store, inputs);
        applyProperties(store, inputs);
        applyDeletionPrune(store);

        store.commit();
        logSummary(store);

        hasProcessed = true;
        return true;
    }

    /**
     * Phase: build a fresh pipeline structure when {@code @EnableFlamingock} is in the round
     * (otherwise leave the existing structure — possibly null), then route every code-change
     * candidate via the unified merger entry point.
     */
    private void applyStructureAndChanges(FlamingockMetadataStore store, RoundInputs inputs) {
        if (!inputs.getEnableAnnotation().isPresent() && inputs.getRoundChanges().isEmpty()) {
            // Nothing structural and nothing to place — skip to avoid spurious dirty marking.
            return;
        }

        PreviewPipeline freshStructure = null;
        boolean strict;
        String configFile = null;
        if (inputs.getEnableAnnotation().isPresent()) {
            EnableFlamingock anno = inputs.getEnableAnnotation().get();
            freshStructure = new PipelineStructureBuilder(sourceRoots, resourcesRoot, logger)
                    .build(anno, inputs.getRoundChanges());
            strict = anno.strictStageMapping();
            configFile = anno.configFile();
        } else {
            // Inherit strict flag from existing metadata; defaults to false on first round.
            strict = store.peek()
                    .map(FlamingockMetadata::isStrictStageMapping)
                    .orElse(false);
        }

        PreviewPipeline finalStructure = freshStructure;
        boolean finalStrict = strict;
        String finalConfigFile = configFile;
        List<CodePreviewChange> roundCodeChanges = new ArrayList<>(inputs.getRoundChanges());
        store.update(metadata -> {
            FlamingockMetadataMerger.applyRound(metadata, finalStructure, roundCodeChanges, finalStrict);
            if (finalConfigFile != null) {
                metadata.setPipelineFile(finalConfigFile);
            }
        });
    }

    /** Phase: replace builder provider when {@code @FlamingockCliBuilder} is in the round. */
    private void applyBuilderProvider(FlamingockMetadataStore store, RoundInputs inputs) {
        inputs.getBuilderProvider().ifPresent(bp ->
                store.update(metadata -> FlamingockMetadataMerger.setBuilderProvider(metadata, bp)));
    }

    /** Phase: merge plugin-provided configuration properties into the metadata. */
    private void applyProperties(FlamingockMetadataStore store, RoundInputs inputs) {
        Map<String, String> pluginProperties = inputs.getPluginProperties();
        if (!pluginProperties.isEmpty()) {
            store.update(metadata -> FlamingockMetadataMerger.mergeProperties(metadata, pluginProperties));
        }
    }

    /**
     * Phase: prune entries whose source class no longer exists in this compilation. Runs before
     * commit so all writes go through a single Filer.createResource (the Filer doesn't allow
     * reopening a resource within the same javac invocation). Single-module-safe.
     *
     * <p>{@code Elements.getTypeElement} requires the canonical name (dot-separated) but
     * {@code CodePreviewChange.getSource()} stores the binary name (dollar-separated for nested
     * classes), so convert before lookup.
     */
    private void applyDeletionPrune(FlamingockMetadataStore store) {
        store.peek().ifPresent(metadata -> {
            boolean pruned = FlamingockMetadataMerger.pruneDeletedClasses(metadata,
                    fqcn -> processingEnv.getElementUtils()
                            .getTypeElement(fqcn.replace('$', '.')) != null);
            if (pruned) {
                store.markDirty();
                logger.info("Pruned deleted @Change classes from metadata");
            }
        });
    }

    private void logSummary(FlamingockMetadataStore store) {
        store.peek().ifPresent(metadata -> {
            PreviewPipeline pipeline = metadata.getPipeline();
            if (pipeline == null) return;
            int totalStages = (pipeline.getStages() != null ? pipeline.getStages().size() : 0)
                    + (pipeline.getSystemStage() != null ? 1 : 0);
            int totalChanges = 0;
            if (pipeline.getSystemStage() != null && pipeline.getSystemStage().getChanges() != null) {
                totalChanges += pipeline.getSystemStage().getChanges().size();
            }
            if (pipeline.getStages() != null) {
                for (PreviewStage stage : pipeline.getStages()) {
                    if (stage.getChanges() != null) {
                        totalChanges += stage.getChanges().size();
                    }
                }
            }
            logger.info("Generated metadata: " + totalStages + " stages, " + totalChanges + " changes");
        });
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

}
