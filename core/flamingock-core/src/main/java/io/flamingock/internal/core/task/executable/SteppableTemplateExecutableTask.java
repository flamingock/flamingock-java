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
import io.flamingock.api.template.AbstractSteppableTemplate;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.SteppableTemplateLoadedChange;
import io.flamingock.internal.util.FileUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executable task for steppable templates (multiple steps).
 * Handles templates extending {@link AbstractSteppableTemplate}.
 */
public class SteppableTemplateExecutableTask extends AbstractTemplateExecutableTask<SteppableTemplateLoadedChange> {

    public SteppableTemplateExecutableTask(String stageName,
                                           SteppableTemplateLoadedChange descriptor,
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

            AbstractSteppableTemplate<?, ?, ?> changeTemplateInstance = (AbstractSteppableTemplate<?, ?, ?>)
                    executionRuntime.getInstance(descriptor.getConstructor());

            changeTemplateInstance.setTransactional(descriptor.isTransactional());
            changeTemplateInstance.setChangeId(descriptor.getId());
            setConfigurationData(changeTemplateInstance);

            setTemplateData(executionRuntime, changeTemplateInstance);

            executionRuntime.executeMethodWithInjectedDependencies(changeTemplateInstance, method);
        } catch (Throwable ex) {
            throw new ChangeExecutionException(ex.getMessage(), this.getId(), ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void setTemplateData(ExecutionRuntime executionRuntime, AbstractSteppableTemplate<?, ?, ?> instance) {

        Object stepsData = descriptor.getSteps();

        if (stepsData != null) {
            logger.debug("Setting steps for steppable template change[{}]", descriptor.getId());

            List<TemplateStep> convertedSteps = convertToTemplateSteps(
                    stepsData,
                    instance.getApplyPayloadClass(),
                    instance.getRollbackPayloadClass()
            );

            Method setStepsMethod = getSetterMethod(instance.getClass(), "setSteps");
            executionRuntime.executeMethodWithParameters(instance, setStepsMethod, convertedSteps);
        } else {
            logger.warn("No 'steps' section provided for steppable template-based change[{}]", descriptor.getId());
        }
    }


    /**
     * Converts raw step data (List of Maps from YAML) to a list of TemplateStep objects.
     *
     * @param stepsData       the raw steps data (expected to be a List of Maps)
     * @param applyClass      the class type for apply payloads
     * @param rollbackClass   the class type for rollback payloads
     * @return list of converted TemplateStep objects
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected List<TemplateStep> convertToTemplateSteps(Object stepsData,
                                                        Class<?> applyClass,
                                                        Class<?> rollbackClass) {
        List<TemplateStep> result = new ArrayList<>();

        if (!(stepsData instanceof List)) {
            logger.warn("Steps data is not a List, ignoring");
            return result;
        }

        List<?> stepsList = (List<?>) stepsData;
        for (Object stepItem : stepsList) {
            if (stepItem instanceof Map) {
                Map<String, Object> stepMap = (Map<String, Object>) stepItem;
                TemplateStep step = new TemplateStep();

                Object applyData = stepMap.get("apply");
                if (applyData != null && Void.class != applyClass) {
                    step.setApply(FileUtil.getFromMap(applyClass, applyData));
                }

                Object rollbackData = stepMap.get("rollback");
                if (rollbackData != null && Void.class != rollbackClass) {
                    step.setRollback(FileUtil.getFromMap(rollbackClass, rollbackData));
                }

                result.add(step);
            } else if (stepItem instanceof TemplateStep) {
                result.add((TemplateStep) stepItem);
            }
        }

        return result;
    }
}
