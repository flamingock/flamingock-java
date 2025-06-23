/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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
import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.task.filter.TaskFilter;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
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


    private final Collection<TaskFilter> taskFilters;

    private final AbstractLoadedStage systemStage;
    private final List<AbstractLoadedStage> loadedStages;

    public static LoadedPipelineBuilder builder() {
        return new LoadedPipelineBuilder();
    }

    private LoadedPipeline(List<AbstractLoadedStage> loadedStages,
                           Collection<TaskFilter> taskFilters) {
        this(null, loadedStages, taskFilters);
    }

    private LoadedPipeline(AbstractLoadedStage systemStage,
                           List<AbstractLoadedStage> loadedStages,
                           Collection<TaskFilter> taskFilters) {
        this.systemStage = systemStage;
        this.loadedStages = loadedStages;
        this.taskFilters = taskFilters;
    }


    /**
     * Validates the entire pipeline configuration and throws an exception if validation fails.
     * This method performs comprehensive validation including:
     * <ul>
     *   <li>Ensures the pipeline contains at least one stage</li>
     *   <li>Validates each individual stage within the pipeline</li>
     *   <li>Checks for duplicate task IDs across all stages</li>
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
        if (loadedStages == null || loadedStages.isEmpty()) {
            errors.add(new ValidationError("Pipeline must contain at least one stage", "pipeline", "pipeline"));

        } else {
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

    @Override
    public Optional<AbstractLoadedTask> getLoadedTask(String taskId) {
        return loadedStages.stream()
                .map(AbstractLoadedStage::getTasks)
                .flatMap(Collection::stream)
                .filter(loadedTask -> loadedTask.getId().equals(taskId))
                .findFirst();
    }

    @Override
    public Optional<String> getStageByTask(String taskId) {
        for (AbstractLoadedStage loadedStage : loadedStages) {
            for (TaskDescriptor loadedTask : loadedStage.getTasks()) {
                if (loadedTask.getId().equals(taskId)) {
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
            for (String id : stage.getTaskIds()) {
                if (!seenIds.add(id)) {
                    duplicateIds.add(id);
                }
            }
        }

        if (!duplicateIds.isEmpty()) {
            String duplicateIdsString = String.join(", ", duplicateIds);
            return Optional.of(new ValidationError(
                    "Duplicate changeUnit IDs found across stages: " + duplicateIdsString,
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
        private Collection<TaskFilter> taskFilters = new LinkedHashSet<>();

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

        public LoadedPipelineBuilder addFilters(Collection<TaskFilter> taskFilters) {
            this.taskFilters.addAll(taskFilters);
            return this;
        }


        public LoadedPipeline build() {
            List<AbstractLoadedStage> allSortedStages = new LinkedList<>(transformListToLoadedStages(beforeUserStages));
            allSortedStages.addAll(transformListToLoadedStages(previewPipeline.getStages()));
            allSortedStages.addAll(transformListToLoadedStages(afterUserStages));

            return transformToLoadedStage(previewPipeline.getSystemStage())
                    .map(abstractLoadedStage -> new LoadedPipeline(
                            abstractLoadedStage,
                            allSortedStages,
                            taskFilters
                    )).orElseGet(() -> new LoadedPipeline(allSortedStages, taskFilters));
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
