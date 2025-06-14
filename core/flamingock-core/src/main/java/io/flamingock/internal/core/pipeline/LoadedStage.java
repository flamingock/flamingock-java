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


import io.flamingock.core.error.validation.Validatable;
import io.flamingock.core.error.validation.ValidationError;

import io.flamingock.core.audit.AuditEntry;
import io.flamingock.internal.core.engine.audit.domain.AuditStageStatus;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.executable.builder.ExecutableTaskBuilder;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.LoadedTaskBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * It's the result of adding the loaded task to the ProcessDefinition
 */
public class LoadedStage implements Validatable {

    public static Builder builder() {
        return new Builder();
    }

    private final String name;

    private final Collection<AbstractLoadedTask> loadedTasks;

    private final boolean parallel;


    public LoadedStage(String name,
                       Collection<AbstractLoadedTask> loadedTasks,
                       boolean parallel) {
        this.name = name;
        this.loadedTasks = loadedTasks;
        this.parallel = parallel;

    }

    public ExecutableStage applyState(AuditStageStatus state) {

        Map<String, AuditEntry.Status> statesMap = state.getEntryStatesMap();

        List<ExecutableTask> tasks = loadedTasks
                .stream()
                .map(loadedTask -> ExecutableTaskBuilder.build(loadedTask, name, statesMap.get(loadedTask.getId())))
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedList::new));

        return new ExecutableStage(name, tasks, parallel);
    }

    public String getName() {
        return name;
    }


    public Collection<AbstractLoadedTask> getLoadedTasks() {
        return loadedTasks;
    }

    public boolean isParallel() {
        return parallel;
    }


    /**
     * Returns a set of all task IDs defined within this stage.
     * <p>
     * It is assumed that task IDs within a stage are unique,
     * so the returned {@code Set} will not contain duplicates.
     */
    public Set<String> getTaskIds() {
        return getLoadedTasks().stream()
                .map(AbstractLoadedTask::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Validates the stage and returns a list of validation errors
     * Validations:
     * 1. has name
     * 2. no duplicate task IDs within stage
     * 3. all tasks in the stage are valid
     * 
     * @return list of validation errors, or empty list if the stage is valid
     */
    @Override
    public List<ValidationError> getValidationErrors() {
        List<ValidationError> errors = new ArrayList<>();

        // Validate stage name
        if (name == null || name.trim().isEmpty()) {
            errors.add(new ValidationError("Stage name cannot be null or empty", "unknown", "stage"));
            return errors; // Return early as we need the name for further error reporting
        }

        // Check if stage is empty
        if (loadedTasks == null || loadedTasks.isEmpty()) {
            String message = String.format("Stage[%s] must contain at least one task", name);
            return Collections.singletonList(new ValidationError(message, name, "stage"));
        }
        getTaskIdDuplicationError().ifPresent(errors::add);
        getLoadedTasks().stream().map(AbstractLoadedTask::getValidationErrors).forEach(errors::addAll);
        return errors;
    }

    @Override
    public String toString() {
        return "LoadedStage{" + "name='" + name + '\'' +
                ", loadedTasks=" + loadedTasks +
                ", parallel=" + parallel +
                '}';
    }

    private Optional<ValidationError> getTaskIdDuplicationError() {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();

        for (AbstractLoadedTask task : getLoadedTasks()) {
            String id = task.getId();
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }

        if (!duplicates.isEmpty()) {
            String duplicateIdsString = String.join(", ", duplicates);
            String message = String.format("Duplicate changeUnit IDs found in stage: %s", duplicateIdsString);
            return Optional.of(new ValidationError(message, name, "stage"));
        } else {
            return Optional.empty();
        }
    }

    public static class Builder {

        private PreviewStage previewStage;

        private Builder() {
        }

        public Builder setPreviewStage(PreviewStage previewStage) {
            this.previewStage = previewStage;
            return this;
        }

        public LoadedStage build() {
            List<AbstractLoadedTask> loadedTasks = previewStage.getTasks()
                    .stream()
                    .map(LoadedTaskBuilder::build)
                    .collect(Collectors.toList());
            return new LoadedStage(previewStage.getName(), loadedTasks, previewStage.isParallel());
        }
    }
}
