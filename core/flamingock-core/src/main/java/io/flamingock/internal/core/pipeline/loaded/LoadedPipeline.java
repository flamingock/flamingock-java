/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.pipeline.loaded;

import io.flamingock.internal.common.core.context.ContextInjectable;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.common.core.error.validation.ValidationResult;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.change.ChangeDescriptor;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.change.filter.ChangeFilter;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LoadedPipeline implements PipelineDescriptor {

    private static final PipelineValidationContext DEFAULT_CONTEXT = new PipelineValidationContext();


    private final Collection<ChangeFilter> changeFilters;

    private final AbstractLoadedStage systemStage;
    private final List<AbstractLoadedStage> loadedStages;

    public static LoadedPipelineBuilder builder() {
        return new LoadedPipelineBuilder();
    }

    private LoadedPipeline(List<AbstractLoadedStage> loadedStages,
                           Collection<ChangeFilter> changeFilters) {
        this(null, loadedStages, changeFilters);
    }

    private LoadedPipeline(AbstractLoadedStage systemStage,
                           List<AbstractLoadedStage> loadedStages,
                           Collection<ChangeFilter> changeFilters) {
        this.systemStage = systemStage;
        this.loadedStages = loadedStages;
        this.changeFilters = changeFilters;
    }


    /**
     * Validates the entire pipeline configuration and throws an exception if validation fails.
     * This method performs comprehensive validation including:
     * <ul>
     *   <li>Ensures the pipeline contains at least one stage</li>
     *   <li>Validates each individual stage within the pipeline</li>
     *   <li>Checks for duplicate change IDs across all stages</li>
     * </ul>
     *
     * @throws FlamingockException if any validation errors are found, containing a formatted
     *         message with all validation issues discovered
     */
    public void validate() throws FlamingockException{
        ValidationResult errors = new ValidationResult("Pipeline validation error");


        if(systemStage != null) {
            errors.addAll(systemStage.getValidationErrors(DEFAULT_CONTEXT));
        }

        // Validate pipeline has stages
        if (loadedStages != null) {
            loadedStages.stream()
                    .map(stage -> stage.getValidationErrors(DEFAULT_CONTEXT))
                    .forEach(errors::addAll);
            getStagesIdDuplicationError().ifPresent(errors::add);
        }

        if (errors.hasErrors()) {
            throw new FlamingockException(errors.formatMessage());
        }
    }

    public Optional<AbstractLoadedStage> getSystemStage() {
        return Optional.ofNullable(systemStage);
    }

    public List<AbstractLoadedStage> getStages() {
        return loadedStages;
    }

    /**
     * Returns the change filters contributed by plugins and applied at runtime construction
     * time. A change is included in the runtime pipeline only if every filter returns
     * {@code true} for it; any filter returning {@code false} excludes the change. May be
     * empty when no plugin contributed a filter.
     */
    public Collection<ChangeFilter> getChangeFilters() {
        return changeFilters == null ? Collections.emptyList() : changeFilters;
    }

    @Override
    public Optional<AbstractLoadedChange> getLoadedChange(String changeId) {
        return loadedStages.stream()
                .map(AbstractLoadedStage::getChanges)
                .flatMap(Collection::stream)
                .filter(loadedChange -> loadedChange.getId().equals(changeId))
                .findFirst();
    }

    @Override
    public Optional<String> getStageByChange(String changeId) {
        for (AbstractLoadedStage loadedStage : loadedStages) {
            for (ChangeDescriptor loadedChange : loadedStage.getChanges()) {
                if (loadedChange.getId().equals(changeId)) {
                    return Optional.of(loadedStage.getName());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void contributeToContext(ContextInjectable contextInjectable) {
        contextInjectable.addDependency(new Dependency(PipelineDescriptor.class, this));
    }



    private Optional<ValidationError> getStagesIdDuplicationError() {
        Set<String> seenIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();

        for (AbstractLoadedStage stage : loadedStages) {
            for (String id : stage.getChangeIds()) {
                if (!seenIds.add(id)) {
                    duplicateIds.add(id);
                }
            }
        }

        if (!duplicateIds.isEmpty()) {
            String duplicateIdsString = String.join(", ", duplicateIds);
            return Optional.of(new ValidationError(
                    "Duplicate change IDs found across stages: " + duplicateIdsString,
                    "pipeline",
                    "pipeline"
            ));

        } else {
            return Optional.empty();
        }
    }


    public static class LoadedPipelineBuilder {

        private Collection<PreviewStage> beforeUserStages = new LinkedHashSet<>();
        private PreviewPipeline previewPipeline;
        private Collection<PreviewStage> afterUserStages = new LinkedHashSet<>();
        private Collection<ChangeFilter> changeFilters = new LinkedHashSet<>();

        private LoadedPipelineBuilder() {
        }

        public LoadedPipelineBuilder addBeforeUserStages(Collection<PreviewStage> stages) {
            this.beforeUserStages = stages;
            return this;
        }

        public LoadedPipelineBuilder addPreviewPipeline(PreviewPipeline previewPipeline) {
            this.previewPipeline = previewPipeline;
            return this;
        }

        public LoadedPipelineBuilder addAfterUserStages(Collection<PreviewStage> stages) {
            this.afterUserStages = stages;
            return this;
        }

        public LoadedPipelineBuilder addFilters(Collection<ChangeFilter> changeFilters) {
            this.changeFilters.addAll(changeFilters);
            return this;
        }


        public LoadedPipeline build() {
            List<AbstractLoadedStage> allSortedStages = new LinkedList<>(transformListToLoadedStages(beforeUserStages));
            Collection<PreviewStage> userStages = previewPipeline != null ? previewPipeline.getStages() : null;
            allSortedStages.addAll(transformListToLoadedStages(userStages));
            allSortedStages.addAll(transformListToLoadedStages(afterUserStages));

            PreviewStage systemStage = previewPipeline != null ? previewPipeline.getSystemStage() : null;
            return transformToLoadedStage(systemStage)
                    .map(abstractLoadedStage -> new LoadedPipeline(
                            abstractLoadedStage,
                            allSortedStages,
                            changeFilters
                    )).orElseGet(() -> new LoadedPipeline(allSortedStages, changeFilters));
        }

        @NotNull
        private static List<AbstractLoadedStage> transformListToLoadedStages(Collection<PreviewStage> stages) {
            if (stages == null) {
                return Collections.emptyList();
            }
            return stages
                    .stream()
                    .map(LoadedPipelineBuilder::transformToLoadedStage)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        }

        public static Optional<AbstractLoadedStage> transformToLoadedStage(PreviewStage previewStage) {
            return previewStage != null
                    ? Optional.of(AbstractLoadedStage.builder().setPreviewStage(previewStage).build())
                    : Optional.empty();
        }


    }
}
