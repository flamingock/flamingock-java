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
package io.flamingock.internal.core.task.executable.builder;

import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.AbstractTemplateLoadedChange;
import io.flamingock.internal.core.task.loaded.CodeLoadedChange;

public interface ExecutableTaskBuilder<LOADED_TASK extends AbstractLoadedTask> {

    /**
     * Builds executable tasks based on a ChangeAction - the new action-based approach.
     * 
     * @param loadedTask the loaded task to build executable tasks from
     * @param stageName the name of the stage containing the task
     * @param action the change action to apply to the task
     * @return executable task
     */
    static ExecutableTask build(AbstractLoadedTask loadedTask, String stageName, ChangeAction action) {
        return getInstance(loadedTask)
                .setStageName(stageName)
                .setChangeAction(action)
                .build();
    }


    static  ExecutableTaskBuilder<?> getInstance(AbstractLoadedTask loadedTask) {

        if(TemplateExecutableTaskBuilder.supports(loadedTask)) {
            TemplateExecutableTaskBuilder templateBuilder = TemplateExecutableTaskBuilder.getInstance();
            AbstractTemplateLoadedChange castedTask = templateBuilder.cast(loadedTask);
            return templateBuilder.setLoadedTask(castedTask);

        } else if(CodeExecutableTaskBuilder.supports(loadedTask)) {
            CodeExecutableTaskBuilder codeBuilder = CodeExecutableTaskBuilder.getInstance();
            CodeLoadedChange castedTask = codeBuilder.cast(loadedTask);
            return codeBuilder.setLoadedTask(castedTask);

        } else {
            throw new IllegalArgumentException(String.format("ExecutableTask type not recognised[%s]", loadedTask.getClass().getName()));

        }
    }


    LOADED_TASK cast(AbstractLoadedTask loadedTask);

    ExecutableTaskBuilder<?> setLoadedTask(LOADED_TASK task);

    ExecutableTaskBuilder<?> setStageName(String stageName);

    /**
     * Sets the change action that determines how the task should be handled.
     * This is the new action-based approach.
     * 
     * @param action the change action to set
     * @return this builder instance for method chaining
     */
    ExecutableTaskBuilder<?> setChangeAction(ChangeAction action);

    ExecutableTask build();
}
