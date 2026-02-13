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

import io.flamingock.api.template.AbstractSimpleTemplate;
import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.SimpleTemplateLoadedChange;

import java.lang.reflect.Method;

/**
 * Executable task for simple templates (single apply/rollback step).
 * Handles templates extending {@link AbstractSimpleTemplate}.
 *
 * @param <CONFIG>   the configuration type for the template
 * @param <APPLY>    the apply payload type
 * @param <ROLLBACK> the rollback payload type
 */
public class SimpleTemplateExecutableTask<CONFIG, APPLY, ROLLBACK>
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
    @SuppressWarnings("unchecked")
    protected void executeInternal(ExecutionRuntime executionRuntime, Method method) {
        try {
            logger.debug("Starting execution of change[{}] with template: {}", descriptor.getId(), descriptor.getTemplateClass());
            logger.debug("change[{}] transactional: {}", descriptor.getId(), descriptor.isTransactional());
            AbstractSimpleTemplate<CONFIG, APPLY, ROLLBACK> changeTemplateInstance =
                    (AbstractSimpleTemplate<CONFIG, APPLY, ROLLBACK>)
                            executionRuntime.getInstance(descriptor.getConstructor());

            changeTemplateInstance.setTransactional(descriptor.isTransactional());
            changeTemplateInstance.setChangeId(descriptor.getId());
            setConfigurationData(changeTemplateInstance);
            setTemplateData(changeTemplateInstance);

            executionRuntime.executeMethodWithInjectedDependencies(changeTemplateInstance, method);
        } catch (Throwable ex) {
            throw new ChangeExecutionException(ex.getMessage(), this.getId(), ex);
        }
    }

    protected void setTemplateData(AbstractSimpleTemplate<CONFIG, APPLY, ROLLBACK> instance) {
        APPLY applyPayload = descriptor.getApplyPayload();
        ROLLBACK rollbackPayload = descriptor.getRollbackPayload();

        if (applyPayload != null) {
            logger.debug("Setting payloads for simple template change[{}]", descriptor.getId());
            instance.setApplyPayload(applyPayload);
            instance.setRollbackPayload(rollbackPayload);
        } else {
            logger.warn("No apply payload provided for simple template-based change[{}]", descriptor.getId());
        }
    }

}
