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

package io.flamingock.internal.core.task.executable.builder;

import io.flamingock.internal.core.engine.audit.writer.AuditEntry;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.CodeLoadedChangeUnit;
import io.flamingock.internal.core.task.loaded.TemplateLoadedChangeUnit;

import java.util.List;

public interface ExecutableTaskBuilder<LOADED_TASK extends AbstractLoadedTask> {


    static List<? extends ExecutableTask> build(AbstractLoadedTask loadedTask, String stageName, AuditEntry.Status initialState) {
        return getInstance(loadedTask)
                .setStageName(stageName)
                .setInitialState(initialState)
                .build();

    }

    static  ExecutableTaskBuilder<?> getInstance(AbstractLoadedTask loadedTask) {

        if(TemplateExecutableTaskBuilder.supports(loadedTask)) {
            TemplateExecutableTaskBuilder templateBuilder = TemplateExecutableTaskBuilder.getInstance();
            TemplateLoadedChangeUnit castedTask = templateBuilder.cast(loadedTask);
            return templateBuilder.setLoadedTask(castedTask);

        } else if(CodeExecutableTaskBuilder.supports(loadedTask)) {
            CodeExecutableTaskBuilder codeBuilder = CodeExecutableTaskBuilder.getInstance();
            CodeLoadedChangeUnit castedTask = codeBuilder.cast(loadedTask);
            return codeBuilder.setLoadedTask(castedTask);

        } else {
            throw new IllegalArgumentException(String.format("ExecutableTask type not recognised[%s]", loadedTask.getClass().getName()));

        }
    }


    LOADED_TASK cast(AbstractLoadedTask loadedTask);

    ExecutableTaskBuilder<?> setLoadedTask(LOADED_TASK task);

    ExecutableTaskBuilder<?> setStageName(String stageName);

    ExecutableTaskBuilder<?> setInitialState(AuditEntry.Status initialState);

    /**
     * It returns a list of classes because legacy ChangeUnits are potentially translated to more than one
     * changeUnit(beforeExecution, etc)
     */
    List<? extends ExecutableTask> build();
}
