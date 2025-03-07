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

package io.flamingock.core.task.executable.template;

import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.task.descriptor.change.TemplatedLoadedChangeUnit;
import io.flamingock.core.task.executable.ExecutableTaskFactory;

import java.util.Collections;
import java.util.List;


/**
 * Factory for ChangeUnit classes
 */
public class TemplatedExecutableTaskFactory implements ExecutableTaskFactory<TemplatedLoadedChangeUnit> {

    @Override
    public List<TemplatedExecutableTask> extractTasks(String stageName, TemplatedLoadedChangeUnit descriptor, AuditEntry.Status initialState) {
        return Collections.singletonList(getTasksFromReflection(stageName, descriptor, initialState));
    }

    private TemplatedExecutableTask getTasksFromReflection(String stageName,
                                                           TemplatedLoadedChangeUnit loadedTask,
                                                           AuditEntry.Status initialState) {
        return new TemplatedExecutableTask(
                stageName,
                loadedTask,
                AuditEntry.Status.isRequiredExecution(initialState),
                loadedTask.getExecutionMethod(),
                loadedTask.getRollbackMethod().orElse(null),
                loadedTask.getConfigSetter().orElse(null),
                loadedTask.getConfigValidator().orElse(null)
        );

    }


}