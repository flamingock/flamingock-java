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
import io.flamingock.api.template.AbstractSteppableTemplate;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.SteppableTemplateLoadedChange;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Executable task for steppable templates (multiple steps).
 * Handles templates extending {@link AbstractSteppableTemplate}.
 *
 * @param <CONFIG>   the configuration type for the template
 * @param <APPLY>    the apply payload type
 * @param <ROLLBACK> the rollback payload type
 */
public class SteppableTemplateExecutableTask<CONFIG, APPLY, ROLLBACK>
        extends AbstractTemplateExecutableTask<CONFIG, APPLY, ROLLBACK,
        SteppableTemplateLoadedChange<CONFIG, APPLY, ROLLBACK>> {

    private int stepIndex = -1;

    public SteppableTemplateExecutableTask(String stageName,
                                           SteppableTemplateLoadedChange<CONFIG, APPLY, ROLLBACK> descriptor,
                                           ChangeAction action,
                                           Method executionMethod,
                                           Method rollbackMethod) {
        super(stageName, descriptor, action, executionMethod, rollbackMethod);
    }

    @Override
    public void apply(ExecutionRuntime executionRuntime) {
        AbstractChangeTemplate<CONFIG, APPLY, ROLLBACK> instance = buildInstance(executionRuntime);

        try {
            List<TemplateStep<APPLY, ROLLBACK>> steps = descriptor.getSteps();
            while (stepIndex >= -1 && stepIndex + 1 < steps.size()) {
                TemplateStep<APPLY, ROLLBACK> currentSep = steps.get(stepIndex + 1);
                instance.setApplyPayload(currentSep.getApplyPayload());
                stepIndex++;
                executionRuntime.executeMethodWithInjectedDependencies(instance, executionMethod);
            }
        } catch (Throwable ex) {
            throw new ChangeExecutionException(this.getId(), ex.getMessage(), ex);
        }
    }

    @Override
    public void rollback(ExecutionRuntime executionRuntime) {
        AbstractChangeTemplate<CONFIG, APPLY, ROLLBACK> instance = buildInstance(executionRuntime);

        try {
            List<TemplateStep<APPLY, ROLLBACK>> steps = descriptor.getSteps();
            while (stepIndex >= 0 && stepIndex < steps.size()) {
                TemplateStep<APPLY, ROLLBACK> currentSep = steps.get(stepIndex);
                if(currentSep.hasRollback() && rollbackMethod != null) {
                    instance.setRollbackPayload(currentSep.getRollbackPayload());
                    executionRuntime.executeMethodWithInjectedDependencies(instance, rollbackMethod);
                } else {
                    logger.warn("Skipping rollback for change[{}], step[{}] -> payload provided[{}], rollback support in template[{}]",
                            getId(), stepIndex, currentSep.hasRollback(), rollbackMethod != null);
                }
                stepIndex--;
            }
        } catch (Throwable ex) {
            throw new ChangeExecutionException(this.getId(), ex.getMessage(), ex);
        }
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private AbstractChangeTemplate<CONFIG, APPLY, ROLLBACK> buildInstance(ExecutionRuntime executionRuntime) {
        AbstractChangeTemplate<CONFIG, APPLY, ROLLBACK> instance;
        try {
            logger.debug("Starting execution of change[{}] with template: {}", descriptor.getId(), descriptor.getTemplateClass());
            logger.debug("change[{}] transactional: {}", descriptor.getId(), descriptor.isTransactional());

            instance = (AbstractChangeTemplate<CONFIG, APPLY, ROLLBACK>)
                    executionRuntime.getInstance(descriptor.getConstructor());

            instance.setTransactional(descriptor.isTransactional());
            instance.setChangeId(descriptor.getId());
            setConfigurationData(instance);

        } catch (Throwable ex) {
            throw new ChangeExecutionException(this.getId(), ex.getMessage(), ex);
        }
        return instance;
    }


}
