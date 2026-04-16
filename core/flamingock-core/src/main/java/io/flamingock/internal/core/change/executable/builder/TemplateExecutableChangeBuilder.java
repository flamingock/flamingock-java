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
package io.flamingock.internal.core.change.executable.builder;

import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.executable.SimpleTemplateExecutableChange;
import io.flamingock.internal.core.change.executable.SteppableTemplateExecutableChange;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.AbstractTemplateLoadedChange;
import io.flamingock.internal.core.change.loaded.SimpleTemplateLoadedChange;
import io.flamingock.internal.core.change.loaded.MultiStepTemplateLoadedChange;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Method;


/**
 * Factory for Change classes
 */
public class TemplateExecutableChangeBuilder implements ExecutableChangeBuilder<AbstractTemplateLoadedChange<?, ?, ?>> {
    private final static Logger logger = FlamingockLoggerFactory.getLogger("TemplateBuilder");

    private static final TemplateExecutableChangeBuilder instance = new TemplateExecutableChangeBuilder();
    private String stageName;
    private ChangeAction changeAction;
    private AbstractTemplateLoadedChange<?, ?, ?> loadedChange;

    static TemplateExecutableChangeBuilder getInstance() {
        return instance;
    }

    public static boolean supports(AbstractLoadedChange loadedChange) {
        return AbstractTemplateLoadedChange.class.isAssignableFrom(loadedChange.getClass());
    }

    @Override
    public AbstractTemplateLoadedChange<?, ?, ?> cast(AbstractLoadedChange loadedChange) {
        return (AbstractTemplateLoadedChange<?, ?, ?>) loadedChange;
    }

    @Override
    public TemplateExecutableChangeBuilder setLoadedChange(AbstractTemplateLoadedChange<?, ?, ?> loadedChange) {
        this.loadedChange = loadedChange;
        return this;
    }

    @Override
    public TemplateExecutableChangeBuilder setStageName(String stageName) {
        this.stageName = stageName;
        return this;
    }

    @Override
    public TemplateExecutableChangeBuilder setChangeAction(ChangeAction action) {
        this.changeAction = action;
        return this;
    }


    @Override
    public ExecutableChange build() {
        Method rollbackMethod = loadedChange.getRollbackMethod().orElse(null);

        if (loadedChange instanceof SimpleTemplateLoadedChange) {
            SimpleTemplateLoadedChange<?, ?, ?> simple = (SimpleTemplateLoadedChange<?, ?, ?>) loadedChange;
            // Only include rollback method if rollback data is present
            if (simple.hasRollbackPayload()) {
                if (rollbackMethod != null) {
                    logger.trace("Change[{}] provides rollback in configuration", loadedChange.getId());
                } else {
                    logger.warn("Change[{}] provides rollback in configuration, but template[{}] doesn't support manual rollback",
                            loadedChange.getId(),
                            loadedChange.getSource()
                    );
                }
            } else {
                if (rollbackMethod != null) {
                    logger.warn("Change[{}] does not provide rollback, but template[{}] supports manual rollback",
                            loadedChange.getId(),
                            loadedChange.getSource()
                    );
                }
                rollbackMethod = null;
            }
            return new SimpleTemplateExecutableChange<>(
                    stageName,
                    simple,
                    changeAction,
                    loadedChange.getApplyMethod(),
                    rollbackMethod
            );
        } else if (loadedChange instanceof MultiStepTemplateLoadedChange) {
            MultiStepTemplateLoadedChange<?, ?, ?> steppable = (MultiStepTemplateLoadedChange<?, ?, ?>) loadedChange;
            if (rollbackMethod != null) {
                logger.trace("Change[{}] is a steppable template with rollback method", loadedChange.getId());
            }
            return new SteppableTemplateExecutableChange<>(
                    stageName,
                    steppable,
                    changeAction,
                    loadedChange.getApplyMethod(),
                    rollbackMethod
            );
        }

        throw new IllegalArgumentException("Unknown template type: " + loadedChange.getClass().getName());
    }
}
