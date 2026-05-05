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

import io.flamingock.internal.common.core.util.Deserializer;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Runtime entry point that discovers all Flamingock-aware modules on the classpath via
 * {@link ServiceLoader}, validates each module in isolation, and produces a single composite
 * {@link FlamingockMetadata} for the runner to consume.
 *
 * <p>Each module's annotation processor generates a {@link FlamingockMetadataProvider}
 * implementation registered in {@code META-INF/services/}. {@link #loadAggregated()} reads
 * each provider's metadata file, applies per-module checks (strict-stage-mapping orphan
 * gate, single builder provider across modules), then aggregates pipelines, properties, and
 * the builder provider into one composite. Multi-module concerns live entirely here, so
 * downstream callers ({@code CoreConfiguration}, {@code SpringbootProperties}, the CLI entry
 * point) keep their single-{@code FlamingockMetadata} contract.
 */
public final class MetadataLoader {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MetadataLoader");

    private MetadataLoader() {
    }

    /**
     * Per-module metadata list. Primarily for diagnostics, tests, or callers that need
     * module-origin information. Most production callers should use {@link #loadAggregated()}.
     */
    public static List<FlamingockMetadata> loadAll() {
        List<FlamingockMetadata> result = new ArrayList<>();
        for (FlamingockMetadataProvider provider :
                ServiceLoader.load(FlamingockMetadataProvider.class, classLoader())) {
            result.add(loadOne(provider));
        }
        return result;
    }

    /**
     * Discover all modules on the classpath, run per-module validation, and aggregate into a
     * single composite {@link FlamingockMetadata}. The composite is a "runtime view" with
     * orphans/strict/pipelineFile null'd because they have already been enforced.
     *
     * @throws RuntimeException when no providers are registered (no Flamingock-aware module on
     *                          the classpath) — the typical user error of "did you add the
     *                          processor as an annotation processor?".
     */
    public static FlamingockMetadata loadAggregated() {
        List<FlamingockMetadata> perModule = loadAll();
        if (perModule.isEmpty()) {
            throw new RuntimeException("No Flamingock metadata providers found on the classpath. "
                    + "Add flamingock-processor as an annotation processor to the modules that "
                    + "declare @EnableFlamingock or @Change classes.");
        }
        new PerModuleStrictMappingValidator().validate(perModule);
        new BuilderProviderUniquenessValidator().validate(perModule);
        return new MetadataAggregator().aggregate(perModule);
    }

    private static FlamingockMetadata loadOne(FlamingockMetadataProvider provider) {
        String resourcePath = provider.getMetadataResourcePath();
        try (InputStream stream = classLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new RuntimeException("Flamingock metadata resource not found at '"
                        + resourcePath + "' (advertised by " + provider.getClass().getName()
                        + "). Inconsistent build artifacts.");
            }
            return Deserializer.readFromStream(stream);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed reading Flamingock metadata at '"
                    + resourcePath + "'", e);
        }
    }

    private static ClassLoader classLoader() {
        // Prefer the thread-context loader so frameworks (Spring Boot, etc.) can scope what
        // is visible. Fall back to the metadata classloader for plain bootstrap.
        ClassLoader tcl = Thread.currentThread().getContextClassLoader();
        return tcl != null ? tcl : FlamingockMetadata.class.getClassLoader();
    }

    // ----------------------------------------------------------------------
    // Helpers — split per single-responsibility so each concern is independently testable.
    // ----------------------------------------------------------------------

    /** Runs the existing per-module {@code OrphanChangeValidator} contract on each module. */
    static final class PerModuleStrictMappingValidator {
        void validate(List<FlamingockMetadata> modules) {
            for (FlamingockMetadata md : modules) {
                if (md.isStrictStageMapping()) {
                    List<CodePreviewChangeIdentity> orphanIds = collectOrphanIds(md);
                    if (!orphanIds.isEmpty()) {
                        String ids = orphanIds.stream().map(o -> o.id).collect(Collectors.joining(", "));
                        throw new RuntimeException(
                                "Strict stage mapping is enabled but the following changes are not "
                                + "mapped to any stage: [" + ids + "]. Add a stage in @EnableFlamingock "
                                + "whose location covers their package, or set strictStageMapping=false.");
                    }
                }
            }
        }

        private static List<CodePreviewChangeIdentity> collectOrphanIds(FlamingockMetadata md) {
            List<CodePreviewChangeIdentity> result = new ArrayList<>();
            if (md.getOrphanChanges() != null) {
                md.getOrphanChanges().forEach(c ->
                        result.add(new CodePreviewChangeIdentity(c.getId() == null ? "<null>" : c.getId())));
            }
            return result;
        }
    }

    /** ≤1 builder provider across all modules. */
    static final class BuilderProviderUniquenessValidator {
        void validate(List<FlamingockMetadata> modules) {
            List<BuilderProviderInfo> nonNull = modules.stream()
                    .map(FlamingockMetadata::getBuilderProvider)
                    .filter(b -> b != null && b.isValid())
                    .collect(Collectors.toList());
            if (nonNull.size() > 1) {
                String classes = nonNull.stream()
                        .map(BuilderProviderInfo::getClassName)
                        .collect(Collectors.joining(", "));
                throw new RuntimeException(
                        "Multiple @FlamingockCliBuilder methods detected across modules: ["
                        + classes + "]. Only one is allowed.");
            }
        }
    }

    /** Combines per-module pipelines, properties, and builder provider into one composite. */
    static final class MetadataAggregator {
        FlamingockMetadata aggregate(List<FlamingockMetadata> modules) {
            FlamingockMetadata composite = new FlamingockMetadata();
            composite.setPipeline(new CompositePipelineBuilder().buildFrom(modules));
            composite.setProperties(new PropertiesMerger().merge(modules));
            composite.setBuilderProvider(modules.stream()
                    .map(FlamingockMetadata::getBuilderProvider)
                    .filter(b -> b != null && b.isValid())
                    .findFirst().orElse(null));
            // orphans / strictStageMapping / pipelineFile are intentionally left at default
            // null/false/null on the composite — they have served their purpose at validation
            // time and are irrelevant to the runner.
            return composite;
        }
    }

    /** Concatenates default + legacy stages, collapses system stages and legacy stages. */
    static final class CompositePipelineBuilder {
        io.flamingock.internal.common.core.preview.PreviewPipeline buildFrom(
                List<FlamingockMetadata> modules) {
            // Stage assembly preserves stage instances by reference — no defensive copies — but
            // routes through helpers that deduplicate stage names with a warning so we don't
            // silently overlap two modules' stages.
            List<io.flamingock.internal.common.core.preview.PreviewStage> defaultStages = new ArrayList<>();
            List<io.flamingock.internal.common.core.preview.PreviewStage> legacyStages = new ArrayList<>();
            io.flamingock.internal.common.core.preview.SystemPreviewStage systemStage = null;
            java.util.Set<String> seenStageNames = new java.util.HashSet<>();

            for (FlamingockMetadata md : modules) {
                io.flamingock.internal.common.core.preview.PreviewPipeline pipeline = md.getPipeline();
                if (pipeline == null) continue;

                if (pipeline.getSystemStage() != null) {
                    systemStage = mergeSystem(systemStage, pipeline.getSystemStage());
                }

                if (pipeline.getStages() != null) {
                    for (io.flamingock.internal.common.core.preview.PreviewStage s : pipeline.getStages()) {
                        if (!seenStageNames.add(s.getName())) {
                            logger.warn("Duplicate stage name '{}' across modules — proceeding with both.",
                                    s.getName());
                        }
                        if (s.getType() == io.flamingock.api.StageType.LEGACY) {
                            legacyStages.add(s);
                        } else {
                            defaultStages.add(s);
                        }
                    }
                }
            }

            io.flamingock.internal.common.core.preview.PreviewStage collapsedLegacy =
                    collapseLegacyStages(legacyStages);

            List<io.flamingock.internal.common.core.preview.PreviewStage> allStages = new ArrayList<>();
            if (collapsedLegacy != null) allStages.add(collapsedLegacy);
            allStages.addAll(defaultStages);

            return systemStage != null
                    ? new io.flamingock.internal.common.core.preview.PreviewPipeline(systemStage, allStages)
                    : new io.flamingock.internal.common.core.preview.PreviewPipeline(allStages);
        }

        private static io.flamingock.internal.common.core.preview.SystemPreviewStage mergeSystem(
                io.flamingock.internal.common.core.preview.SystemPreviewStage soFar,
                io.flamingock.internal.common.core.preview.PreviewStage incoming) {
            // PreviewPipeline.getSystemStage returns PreviewStage (declared) but the field is
            // SystemPreviewStage; safe to cast.
            io.flamingock.internal.common.core.preview.SystemPreviewStage in =
                    (io.flamingock.internal.common.core.preview.SystemPreviewStage) incoming;
            if (soFar == null) return in;
            List<io.flamingock.internal.common.core.preview.AbstractPreviewChange> merged = new ArrayList<>();
            if (soFar.getChanges() != null) merged.addAll(soFar.getChanges());
            if (in.getChanges() != null) {
                java.util.Set<String> existingIds = new java.util.HashSet<>();
                if (soFar.getChanges() != null) {
                    soFar.getChanges().forEach(c -> existingIds.add(c.getId()));
                }
                in.getChanges().stream()
                        .filter(c -> !existingIds.contains(c.getId()))
                        .forEach(merged::add);
            }
            io.flamingock.internal.common.core.preview.SystemPreviewStage out =
                    new io.flamingock.internal.common.core.preview.SystemPreviewStage(
                            soFar.getName(), soFar.getDescription(),
                            soFar.getSourcesPackage(), soFar.getResourcesDir(), merged);
            return out;
        }

        private static io.flamingock.internal.common.core.preview.PreviewStage collapseLegacyStages(
                List<io.flamingock.internal.common.core.preview.PreviewStage> legacyStages) {
            if (legacyStages.isEmpty()) return null;
            if (legacyStages.size() == 1) return legacyStages.get(0);
            // Multiple legacy stages: collapse changes into the first (id-deduped).
            io.flamingock.internal.common.core.preview.PreviewStage first = legacyStages.get(0);
            List<io.flamingock.internal.common.core.preview.AbstractPreviewChange> merged = new ArrayList<>();
            java.util.Set<String> seenIds = new java.util.HashSet<>();
            if (first.getChanges() != null) {
                first.getChanges().forEach(c -> { merged.add(c); seenIds.add(c.getId()); });
            }
            for (int i = 1; i < legacyStages.size(); i++) {
                io.flamingock.internal.common.core.preview.PreviewStage extra = legacyStages.get(i);
                if (extra.getChanges() == null) continue;
                extra.getChanges().stream()
                        .filter(c -> seenIds.add(c.getId()))
                        .forEach(merged::add);
            }
            return new io.flamingock.internal.common.core.preview.PreviewStage(
                    first.getName(), first.getType(), first.getDescription(),
                    first.getSourcesPackage(), first.getResourcesDir(), merged);
        }
    }

    /** Union map; later modules win on key clash. */
    static final class PropertiesMerger {
        java.util.Map<String, String> merge(List<FlamingockMetadata> modules) {
            java.util.Map<String, String> result = new java.util.HashMap<>();
            for (FlamingockMetadata md : modules) {
                if (md.getProperties() != null) result.putAll(md.getProperties());
            }
            return result;
        }
    }

    /** Lightweight identity for orphan reporting (stable across releases). */
    private static final class CodePreviewChangeIdentity {
        final String id;
        CodePreviewChangeIdentity(String id) { this.id = id; }
    }
}
