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
package io.flamingock.internal.core.task.executable;

import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.TemplatePayload;
import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.SimpleTemplateLoadedChange;

import java.lang.reflect.Method;

/**
 * Executable task for simple templates (single apply/rollback step).
 * Handles templates annotated with {@code @ChangeTemplate(steppable = false)} or without annotation.
 *
 * @param <CONFIG>   the configuration type for the template
 * @param <APPLY>    the apply payload type
 * @param <ROLLBACK> the rollback payload type
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SimpleTemplateExecutableTask<CONFIG, APPLY extends TemplatePayload, ROLLBACK extends TemplatePayload>
        extends AbstractTemplateExecutableTask<CONFIG, APPLY, ROLLBACK,
                SimpleTemplateLoadedChange<CONFIG, APPLY, ROLLBACK>> {

    public SimpleTemplateExecutableTask(String stageName,
                                        SimpleTemplateLoadedChange<CONFIG, APPLY, ROLLBACK> descriptor,
                                        ChangeAction action,
                                        Method executionMethod,
                                        Method rollbackMethod) {
        super(stageName, descriptor, action, executionMethod, rollbackMethod);
    }

    @Override
    public void apply(ExecutionRuntime executionRuntime) {
        logger.debug("Applying change[{}] with template: {}", descriptor.getId(), descriptor.getTemplateClass());
        logger.debug("change[{}] transactional: {}", descriptor.getId(), descriptor.isTransactional());
        executeInternal(executionRuntime, executionMethod);
    }

    @Override
    public void rollback(ExecutionRuntime executionRuntime) {
        logger.debug("Rolling back change[{}] with template: {}", descriptor.getId(), descriptor.getTemplateClass());
        executeInternal(executionRuntime, rollbackMethod);
    }

    protected void executeInternal(ExecutionRuntime executionRuntime, Method method) {
        try {
            AbstractChangeTemplate instance = (AbstractChangeTemplate)
                            executionRuntime.getInstance(descriptor.getConstructor());

            instance.setTransactional(descriptor.isTransactional());
            instance.setChangeId(descriptor.getId());
            logger.trace("Setting payloads for simple template change[{}]", descriptor.getId());
            setConfigurationData(instance);
            instance.setApplyPayload(descriptor.getApplyPayload());
            instance.setRollbackPayload(descriptor.getRollbackPayload());

            executionRuntime.executeMethodWithInjectedDependencies(instance, method);
        } catch (Throwable ex) {
            throw new ChangeExecutionException(this.getId(), ex.getMessage(), ex);
        }
    }


}
