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
import io.flamingock.internal.core.task.executable.TemplateExecutableTask;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.TemplateLoadedChange;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Method;


/**
 * Factory for Change classes
 */
public class TemplateExecutableTaskBuilder implements ExecutableTaskBuilder<TemplateLoadedChange> {
    private final static Logger logger = FlamingockLoggerFactory.getLogger("TemplateBuilder");

    private static final TemplateExecutableTaskBuilder instance = new TemplateExecutableTaskBuilder();
    private String stageName;
    private ChangeAction changeAction;
    private TemplateLoadedChange loadedTask;

    static TemplateExecutableTaskBuilder getInstance() {
        return instance;
    }

    public static boolean supports(AbstractLoadedTask loadedTask) {
        return TemplateLoadedChange.class.isAssignableFrom(loadedTask.getClass());
    }

    @Override
    public TemplateLoadedChange cast(AbstractLoadedTask loadedTask) {
        return (TemplateLoadedChange) loadedTask;
    }

    @Override
    public TemplateExecutableTaskBuilder setLoadedTask(TemplateLoadedChange loadedTask) {
        this.loadedTask = loadedTask;
        return this;
    }

    @Override
    public TemplateExecutableTaskBuilder setStageName(String stageName) {
        this.stageName = stageName;
        return this;
    }

    @Override
    public TemplateExecutableTaskBuilder setChangeAction(ChangeAction action) {
        this.changeAction = action;
        return this;
    }


    @Override
    public ExecutableTask build() {
        return getTasksFromReflection(stageName, loadedTask, changeAction);
    }


    /**
     * New ChangeAction-based method for building tasks.
     */
    private TemplateExecutableTask getTasksFromReflection(String stageName,
                                                          TemplateLoadedChange loadedTask,
                                                          ChangeAction action) {
        return buildTask(stageName, loadedTask, action);
    }

    private TemplateExecutableTask buildTask(String stageName,
                                           TemplateLoadedChange loadedTask,
                                           ChangeAction action) {
        Method rollbackMethod = null;
        if (loadedTask.getRollback() != null) {
            rollbackMethod = loadedTask.getRollbackMethod().orElse(null);
            if (rollbackMethod != null) {
                logger.trace("Change[{}] provides rollback in configuration", loadedTask.getId());
            } else {
                logger.warn("Change[{}] provides rollback in configuration, but based on a template[{}] not supporting manual rollback",
                        loadedTask.getId(),
                        loadedTask.getSource()
                );
            }
        } else {
            if (loadedTask.getRollbackMethod().isPresent()) {
                logger.warn("Change[{}] does not provide rollback, but based on a template[{}] support manual rollback",
                        loadedTask.getId(),
                        loadedTask.getSource()
                );
            }
        }
        return new TemplateExecutableTask(
                stageName,
                loadedTask,
                action,
                loadedTask.getApplyMethod(),
                rollbackMethod
        );

    }
}