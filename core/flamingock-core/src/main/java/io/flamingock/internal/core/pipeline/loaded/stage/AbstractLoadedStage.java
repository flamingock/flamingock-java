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
package io.flamingock.internal.core.pipeline.loaded.stage;


import io.flamingock.internal.common.core.error.validation.Validatable;
import io.flamingock.internal.common.core.error.validation.ValidationError;

import io.flamingock.api.StageType;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.common.core.recovery.action.ChangeActionMap;
import io.flamingock.internal.core.pipeline.loaded.PipelineValidationContext;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.executable.builder.ExecutableChangeBuilder;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.LoadedChangeBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * It's the result of adding the loaded change to the ProcessDefinition
 */
public abstract class AbstractLoadedStage implements Validatable<PipelineValidationContext> {

    public static Builder builder() {
        return new Builder();
    }


    private final StageValidationContext validationContext;

    private final String name;

    private final StageType type;

    private final Collection<AbstractLoadedChange> changes;

    public AbstractLoadedStage(String name,
                               StageType type,
                               Collection<AbstractLoadedChange> changes,
                               StageValidationContext validationContext) {
        this.name = name;
        this.type = type;
        this.changes = changes;
        this.validationContext = validationContext;
    }



    /**
     * Applies the specified action plan to this stage, creating an ExecutableStage
     * with changes configured according to their assigned actions.
     * This method provides a unified interface for both community (audit-based)
     * and cloud (orchestrator-based) execution planning.
     * 
     * @param actionPlan the action plan specifying what action to take for each change
     * @return an ExecutableStage ready for execution
     */
    public ExecutableStage applyActions(ChangeActionMap actionPlan) {
        List<ExecutableChange> changes = this.changes
                .stream()
                .map(loadedChange -> {
                    ChangeAction action = actionPlan.getActionFor(loadedChange.getId());
                    return ExecutableChangeBuilder.build(loadedChange, name, action);
                })
                .collect(Collectors.toCollection(LinkedList::new));

        return new ExecutableStage(name, changes);
    }

    public String getName() {
        return name;
    }

    public StageType getType() {
        return type;
    }

    public Collection<AbstractLoadedChange> getChanges() {
        return changes;
    }

    /**
     * Returns a new instance of the same concrete stage type carrying the provided changes
     * instead of this stage's current changes. Name, type, and the per-subclass validation
     * context are preserved.
     *
     * <p>Used at runtime construction time to materialize a stage with a filtered subset
     * of changes without mutating the original {@code AbstractLoadedStage} (whose
     * {@code changes} collection is final and immutable post-construction). The copy is
     * produced by reflecting on the concrete subclass to find its public
     * {@code (String, StageType, Collection)} constructor — every concrete subclass
     * ({@link DefaultLoadedStage}, {@link LegacyLoadedStage}, {@link SystemLoadedStage})
     * exposes one.
     */
    public AbstractLoadedStage withChanges(Collection<AbstractLoadedChange> newChanges) {
        try {
            return getClass()
                    .getConstructor(String.class, StageType.class, Collection.class)
                    .newInstance(getName(), getType(), newChanges);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot copy stage [" + getName() + "] of type " + getClass().getName()
                            + " with filtered changes", e);
        }
    }



    /**
     * Returns a set of all change IDs defined within this stage.
     * <p>
     * It is assumed that change IDs within a stage are unique,
     * so the returned {@code Set} will not contain duplicates.
     * </p>
     * 
     * @return a set of change IDs in this stage
     */
    public Set<String> getChangeIds() {
        return getChanges().stream()
                .map(AbstractLoadedChange::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Validates the stage and returns a list of validation errors
     * Validations:
     * 1. has name
     * 2. no duplicate change IDs within stage
     * 3. all changes in the stage are valid
     * 
     * @return list of validation errors, or empty list if the stage is valid
     */
    @Override
    public List<ValidationError> getValidationErrors(PipelineValidationContext context) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate stage name
        if (name == null || name.trim().isEmpty()) {
            errors.add(new ValidationError("Stage name cannot be null or empty", "unknown", "stage"));
            return errors; // Return early as we need the name for further error reporting
        }

        // Check if stage is empty
        if (changes == null || changes.isEmpty()) {
            String message = String.format("Stage[%s] must contain at least one change", name);
            return Collections.singletonList(new ValidationError(message, name, "stage"));
        }
        getChangeIdDuplicationError().ifPresent(errors::add);
        getChanges().stream().map(change -> change.getValidationErrors(validationContext)).forEach(errors::addAll);
        return errors;
    }

    @Override
    public String toString() {
        return "LoadedStage{" + "name='" + name + '\'' +
                ", loadedChanges=" + changes +
                '}';
    }

    protected Optional<ValidationError> getChangeIdDuplicationError() {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();

        for (AbstractLoadedChange change : getChanges()) {
            String id = change.getId();
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }

        if (!duplicates.isEmpty()) {
            String duplicateIdsString = String.join(", ", duplicates);
            String message = String.format("Duplicate change IDs found in stage: %s", duplicateIdsString);
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

        public AbstractLoadedStage build() {
            List<AbstractLoadedChange> loadedChanges = previewStage.getChanges()
                    .stream()
                    .map(LoadedChangeBuilder::build)
                    .sorted()
                    .collect(Collectors.toList());
            switch(previewStage.getType()) {
                case LEGACY:
                    return new LegacyLoadedStage(previewStage.getName(), previewStage.getType(), loadedChanges);
                case SYSTEM:
                    return new SystemLoadedStage(previewStage.getName(), previewStage.getType(), loadedChanges);
                case DEFAULT:
                default:
                    return new DefaultLoadedStage(previewStage.getName(), previewStage.getType(), loadedChanges);
            }

        }
    }
}
