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

import io.flamingock.internal.util.FileUtil;
import io.flamingock.api.template.ChangeTemplateConfig;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.task.executable.TemplateExecutableTask;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.TemplateLoadedChangeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;


/**
 * Factory for ChangeUnit classes
 */
public class TemplateExecutableTaskBuilder implements ExecutableTaskBuilder<TemplateLoadedChangeUnit> {
    private final static Logger logger = LoggerFactory.getLogger("TemplateExecutableTaskBuilder");

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
        Method rollbackMethod = null;
        if(loadedTask.getTemplateConfiguration().containsKey("rollback")) {
            rollbackMethod = loadedTask.getRollbackMethod().orElse(null);
            if(rollbackMethod != null) {
                logger.trace("ChangeUnit[{}] provides rollback in configuration", loadedTask.getId());
            } else {
                logger.warn("ChangeUnit[{}] provides rollback in configuration, but based on a template[{}] not supporting manual rollback",
                        loadedTask.getId(),
                        loadedTask.getSource()
                );
            }
        } else {
            if(loadedTask.getRollbackMethod().isPresent()) {
                logger.warn("ChangeUnit[{}] does not provide rollback, but based on a template[{}] support manual rollback",
                        loadedTask.getId(),
                        loadedTask.getSource()
                );            }
        }
        return new TemplateExecutableTask(
                stageName,
                loadedTask,
                AuditEntry.Status.isRequiredExecution(initialState),
                loadedTask.getExecutionMethod(),
                rollbackMethod
        );

    }

    private <T extends ChangeTemplateConfig<?,?>> T getConfig(Class<T> configClass, TemplateLoadedChangeUnit loadedTask) {
        return FileUtil.getFromMap(configClass, loadedTask.getTemplateConfiguration());
    }


}