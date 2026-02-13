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

import io.flamingock.api.template.AbstractSteppableTemplate;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.SteppableTemplateLoadedChange;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Executable task for steppable templates (multiple steps).
 * Handles templates extending {@link AbstractSteppableTemplate}.
 */
public class SteppableTemplateExecutableTask extends AbstractTemplateExecutableTask<SteppableTemplateLoadedChange<?, ?, ?>> {

    public SteppableTemplateExecutableTask(String stageName,
                                           SteppableTemplateLoadedChange<?, ?, ?> descriptor,
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

            AbstractSteppableTemplate<?, ?, ?> instance = (AbstractSteppableTemplate<?, ?, ?>)
                    executionRuntime.getInstance(descriptor.getConstructor());

            instance.setTransactional(descriptor.isTransactional());
            instance.setChangeId(descriptor.getId());
            setConfigurationData(instance);

            setStepsData(instance, descriptor.getSteps());
            while (instance.advance()) {
                executionRuntime.executeMethodWithInjectedDependencies(instance, method);
            }

        } catch (Throwable ex) {
            throw new ChangeExecutionException(ex.getMessage(), this.getId(), ex);
        }
    }

    /**
     * Sets the steps data on the template instance.
     * Uses a helper method to handle the generic type capture properly.
     */
    @SuppressWarnings("unchecked")
    private <A, R> void setStepsData(AbstractSteppableTemplate<?, A, R> instance,
                                     List<? extends TemplateStep<?, ?>> steps) {
        // Safe cast: the steps were created using the template's type information
        instance.setSteps((List<TemplateStep<A, R>>) steps);
    }

}
