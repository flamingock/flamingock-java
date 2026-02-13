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
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.SimpleTemplateLoadedChange;
import io.flamingock.internal.util.FileUtil;

import java.lang.reflect.Method;

/**
 * Executable task for simple templates (single apply/rollback step).
 * Handles templates extending {@link AbstractSimpleTemplate}.
 */
public class SimpleTemplateExecutableTask extends AbstractTemplateExecutableTask<SimpleTemplateLoadedChange> {

    public SimpleTemplateExecutableTask(String stageName,
                                        SimpleTemplateLoadedChange descriptor,
                                        ChangeAction action,
                                        Method executionMethod,
                                        Method rollbackMethod) {
        super(stageName, descriptor, action, executionMethod, rollbackMethod);
    }

    @Override
    protected void executeInternal(ExecutionRuntime executionRuntime, Method method) {
        try {
            logger.debug("Starting execution of change[{}] with template: {}", descriptor.getId(), descriptor.getTemplateClass());
            logger.debug("change[{}] transactional: {}", descriptor.getId(), descriptor.isTransactional());
            AbstractSimpleTemplate<?, ?, ?> changeTemplateInstance = (AbstractSimpleTemplate<?, ?, ?>)
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void setTemplateData(AbstractSimpleTemplate instance) {
        Object applyData = descriptor.getApply();

        if (applyData != null) {
            logger.debug("Setting step for simple template change[{}]", descriptor.getId());

            TemplateStep step = convertToTemplateStep(
                    applyData,
                    descriptor.getRollback(),
                    instance.getApplyPayloadClass(),
                    instance.getRollbackPayloadClass()
            );
            instance.setApplyPayload(step.getApply());
            instance.setRollbackPayload(step.getRollback());

        } else {
            logger.warn("No 'apply' section provided for simple template-based change[{}]", descriptor.getId());
        }
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    protected TemplateStep convertToTemplateStep(Object applyData,
                                                 Object rollbackData,
                                                 Class<?> applyClass,
                                                 Class<?> rollbackClass) {
        TemplateStep step = new TemplateStep();
        step.setApply(FileUtil.getFromMap(applyClass, applyData));
        if (rollbackData != null && Void.class != rollbackClass) {
            step.setRollback(FileUtil.getFromMap(rollbackClass, rollbackData));
        }

        return step;
    }

}
