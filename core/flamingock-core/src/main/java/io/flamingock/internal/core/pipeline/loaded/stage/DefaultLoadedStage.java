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

package io.flamingock.internal.core.pipeline.loaded.stage;


import io.flamingock.api.task.ChangeCategory;
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.api.StageType;
import io.flamingock.internal.core.pipeline.loaded.PipelineValidationContext;
import io.flamingock.internal.core.task.loaded.AbstractLoadedChangeUnit;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;

import java.util.Collection;
import java.util.List;

import static io.flamingock.internal.core.pipeline.loaded.stage.StageValidationContext.SortType.SEQUENTIAL_FORMATTED;

/**
 * It's the result of adding the loaded task to the ProcessDefinition
 */
public class DefaultLoadedStage extends AbstractLoadedStage {

    private static final StageValidationContext validationContext = StageValidationContext.builder()
            .setSorted(SEQUENTIAL_FORMATTED)
            .build();

    public DefaultLoadedStage(String name,
                              StageType type,
                              Collection<AbstractLoadedTask> loadedTasks,
                              boolean parallel) {
        super(name, type, loadedTasks, parallel, validationContext);
    }

    @Override
    public List<ValidationError> getValidationErrors(PipelineValidationContext context) {
        List<ValidationError> errors = super.getValidationErrors(context);
        String changeCategoryErrorMsg = String.format(
                "ChangeUnit[{}] in default stage cannot have categories %s or %s",
                ChangeCategory.SYSTEM, ChangeCategory.IMPORT);

        for(AbstractLoadedTask task : getTasks()) {
            if(task instanceof AbstractLoadedChangeUnit) {
                AbstractLoadedChangeUnit changeUnit = (AbstractLoadedChangeUnit) task;
                if(changeUnit.hasAnyCategory(ChangeCategory.SYSTEM, ChangeCategory.IMPORT)) {
                    errors.add(new ValidationError(changeCategoryErrorMsg, task.getId(), "changeUnit"));
                }
            } else {
                errors.add(new ValidationError("Task in default stage must be a ChangeUnit", task.getId(), "changeUnit"));
            }

        }

        return errors;
    }




}
