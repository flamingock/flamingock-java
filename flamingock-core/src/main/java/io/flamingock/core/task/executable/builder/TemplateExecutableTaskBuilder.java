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

package io.flamingock.core.task.executable.builder;

import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.task.executable.TemplateExecutableTask;
import io.flamingock.core.task.loaded.AbstractLoadedTask;
import io.flamingock.core.task.loaded.TemplateLoadedChangeUnit;

import java.util.Collections;
import java.util.List;


/**
 * Factory for ChangeUnit classes
 */
public class TemplateExecutableTaskBuilder implements ExecutableTaskBuilder<TemplateLoadedChangeUnit> {

    private static final TemplateExecutableTaskBuilder instance = new TemplateExecutableTaskBuilder();
    private String stageName;
    private AuditEntry.Status initialState;
    private TemplateLoadedChangeUnit loadedTask;

    static TemplateExecutableTaskBuilder getInstance() {
        return instance;
    }

    public static boolean supports(AbstractLoadedTask loadedTask) {
        return TemplateLoadedChangeUnit.class.isAssignableFrom(loadedTask.getClass());
    }

    @Override
    public TemplateLoadedChangeUnit cast(AbstractLoadedTask loadedTask) {
        return (TemplateLoadedChangeUnit)loadedTask;
    }

    @Override
    public TemplateExecutableTaskBuilder setLoadedTask(TemplateLoadedChangeUnit loadedTask) {
        this.loadedTask = loadedTask;
        return this;
    }

    @Override
    public TemplateExecutableTaskBuilder setStageName(String stageName) {
        this.stageName = stageName;
        return this;
    }

    @Override
    public TemplateExecutableTaskBuilder setInitialState(AuditEntry.Status initialState) {
        this.initialState = initialState;
        return this;
    }

    @Override
    public List<TemplateExecutableTask> build() {
        return Collections.singletonList(getTasksFromReflection(stageName, loadedTask, initialState));
    }

    private TemplateExecutableTask getTasksFromReflection(String stageName,
                                                          TemplateLoadedChangeUnit loadedTask,
                                                          AuditEntry.Status initialState) {
        return new TemplateExecutableTask(
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