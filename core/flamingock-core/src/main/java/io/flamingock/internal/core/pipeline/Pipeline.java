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

package io.flamingock.internal.core.pipeline;

import io.flamingock.core.error.FlamingockException;
import io.flamingock.core.error.validation.ValidationError;
import io.flamingock.core.error.validation.ValidationResult;
import io.flamingock.core.context.Dependency;
import io.flamingock.core.pipeline.PipelineDescriptor;
import io.flamingock.core.preview.PreviewPipeline;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.core.task.TaskDescriptor;
import io.flamingock.core.context.ContextInjectable;
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

public class Pipeline implements PipelineDescriptor {

    private final Collection<TaskFilter> taskFilters;

    private final List<LoadedStage> loadedStages;

    public static PipelineBuilder builder() {
        return new PipelineBuilder();
    }

    private Pipeline(List<LoadedStage> loadedStages, Collection<TaskFilter> taskFilters) {
        this.loadedStages = loadedStages;
        this.taskFilters = taskFilters;
    }

    public List<LoadedStage> validateAndGetLoadedStages() {
        validate();
        return loadedStages;
    }

    @Override
    public Optional<AbstractLoadedTask> getLoadedTask(String taskId) {
        return loadedStages.stream()
                .map(LoadedStage::getLoadedTasks)
                .flatMap(Collection::stream)
                .filter(loadedTask -> loadedTask.getId().equals(taskId))
                .findFirst();
    }

    @Override
    public Optional<String> getStageByTask(String taskId) {
        for (LoadedStage loadedStage : loadedStages) {
            for (TaskDescriptor loadedTask : loadedStage.getLoadedTasks()) {
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

    /**
     * Validates the pipeline and returns a list of validation errors.
     * This includes validating that:
     * 1. at least one stage
     * 2. no empty stages
     * 3. no duplicate task IDs across all stages
     * 4. all stages in the pipeline are valid
     */
    private void validate() {
        ValidationResult errors = new ValidationResult("Pipeline validation error");

        // Validate pipeline has stages
        if (loadedStages == null || loadedStages.isEmpty()) {
            errors.add(new ValidationError("Pipeline must contain at least one stage", "pipeline", "pipeline"));

        } else {
            loadedStages.stream().map(LoadedStage::getValidationErrors).forEach(errors::addAll);
            getStagesIdDuplicationError().ifPresent(errors::add);
        }

        if (errors.hasErrors()) {
            throw new FlamingockException(errors.formatMessage());
        }
    }


    private Optional<ValidationError> getStagesIdDuplicationError() {
        Set<String> seenIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();

        for (LoadedStage stage : loadedStages) {
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


    public static class PipelineBuilder {

        private Collection<PreviewStage> beforeUserStages = new LinkedHashSet<>();
        private PreviewPipeline previewPipeline;
        private Collection<PreviewStage> afterUserStages = new LinkedHashSet<>();
        private Collection<TaskFilter> taskFilters = new LinkedHashSet<>();

        private PipelineBuilder() {
        }

        public PipelineBuilder addBeforeUserStages(Collection<PreviewStage> stages) {
            this.beforeUserStages = stages;
            return this;
        }

        public PipelineBuilder addPreviewPipeline(PreviewPipeline previewPipeline) {
            this.previewPipeline = previewPipeline;
            return this;
        }

        public PipelineBuilder addAfterUserStages(Collection<PreviewStage> stages) {
            this.afterUserStages = stages;
            return this;
        }

        public PipelineBuilder addFilters(Collection<TaskFilter> taskFilters) {
            this.taskFilters.addAll(taskFilters);
            return this;
        }


        public Pipeline build() {
            List<LoadedStage> allSortedStages = new LinkedList<>(transformToLoadedStages(beforeUserStages));
            allSortedStages.addAll(transformToLoadedStages(previewPipeline.getStages()));
            allSortedStages.addAll(transformToLoadedStages(afterUserStages));

            return new Pipeline(allSortedStages, taskFilters);
        }

        @NotNull
        private static List<LoadedStage> transformToLoadedStages(Collection<PreviewStage> stages) {
            if (stages == null) {
                return Collections.emptyList();
            }
            return stages
                    .stream()
                    .map(LoadedStage.builder()::setPreviewStage)
                    .map(LoadedStage.Builder::build)
                    .collect(Collectors.toList());
        }


    }
}
