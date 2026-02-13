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
import io.flamingock.internal.core.task.executable.SimpleTemplateExecutableTask;
import io.flamingock.internal.core.task.executable.SteppableTemplateExecutableTask;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.AbstractTemplateLoadedChange;
import io.flamingock.internal.core.task.loaded.SimpleTemplateLoadedChange;
import io.flamingock.internal.core.task.loaded.SteppableTemplateLoadedChange;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Method;


/**
 * Factory for Change classes
 */
public class TemplateExecutableTaskBuilder implements ExecutableTaskBuilder<AbstractTemplateLoadedChange> {
    private final static Logger logger = FlamingockLoggerFactory.getLogger("TemplateBuilder");

    private static final TemplateExecutableTaskBuilder instance = new TemplateExecutableTaskBuilder();
    private String stageName;
    private ChangeAction changeAction;
    private AbstractTemplateLoadedChange loadedTask;

    static TemplateExecutableTaskBuilder getInstance() {
        return instance;
    }

    public static boolean supports(AbstractLoadedTask loadedTask) {
        return AbstractTemplateLoadedChange.class.isAssignableFrom(loadedTask.getClass());
    }

    @Override
    public AbstractTemplateLoadedChange cast(AbstractLoadedTask loadedTask) {
        return (AbstractTemplateLoadedChange) loadedTask;
    }

    @Override
    public TemplateExecutableTaskBuilder setLoadedTask(AbstractTemplateLoadedChange loadedTask) {
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
        Method rollbackMethod = loadedTask.getRollbackMethod().orElse(null);

        if (loadedTask instanceof SimpleTemplateLoadedChange) {
            SimpleTemplateLoadedChange simple = (SimpleTemplateLoadedChange) loadedTask;
            // Only include rollback method if rollback data is present
            if (simple.hasRollback()) {
                if (rollbackMethod != null) {
                    logger.trace("Change[{}] provides rollback in configuration", loadedTask.getId());
                } else {
                    logger.warn("Change[{}] provides rollback in configuration, but template[{}] doesn't support manual rollback",
                            loadedTask.getId(),
                            loadedTask.getSource()
                    );
                }
            } else {
                if (rollbackMethod != null) {
                    logger.warn("Change[{}] does not provide rollback, but template[{}] supports manual rollback",
                            loadedTask.getId(),
                            loadedTask.getSource()
                    );
                }
                rollbackMethod = null;
            }
            return new SimpleTemplateExecutableTask(
                    stageName,
                    simple,
                    changeAction,
                    loadedTask.getApplyMethod(),
                    rollbackMethod
            );
        } else if (loadedTask instanceof SteppableTemplateLoadedChange) {
            SteppableTemplateLoadedChange steppable = (SteppableTemplateLoadedChange) loadedTask;
            if (rollbackMethod != null) {
                logger.trace("Change[{}] is a steppable template with rollback method", loadedTask.getId());
            }
            return new SteppableTemplateExecutableTask(
                    stageName,
                    steppable,
                    changeAction,
                    loadedTask.getApplyMethod(),
                    rollbackMethod
            );
        }

        throw new IllegalArgumentException("Unknown template type: " + loadedTask.getClass().getName());
    }
}
