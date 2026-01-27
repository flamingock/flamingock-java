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

import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.TemplateLoadedChange;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.util.FileUtil;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TemplateExecutableTask extends ReflectionExecutableTask<TemplateLoadedChange> {
    private final Logger logger = FlamingockLoggerFactory.getLogger("TemplateTask");

    public TemplateExecutableTask(String stageName,
                                  TemplateLoadedChange descriptor,
                                  ChangeAction action,
                                  Method executionMethod,
                                  Method rollbackMethod) {
        super(stageName, descriptor, action, executionMethod, rollbackMethod);
    }

    @Override
    protected void executeInternal(ExecutionRuntime executionRuntime, Method method ) {
        try {
            logger.debug("Starting execution of change[{}] with template: {}", descriptor.getId(), descriptor.getTemplateClass());
            logger.debug("change[{}] transactional: {}", descriptor.getId(), descriptor.isTransactional());
            Object instance = executionRuntime.getInstance(descriptor.getConstructor());
            ChangeTemplate<?,?,?> changeTemplateInstance = (ChangeTemplate<?,?,?>) instance;
            changeTemplateInstance.setTransactional(descriptor.isTransactional());
            changeTemplateInstance.setChangeId(descriptor.getId());
            setExecutionData(executionRuntime, changeTemplateInstance, "Configuration");
            setExecutionData(executionRuntime, changeTemplateInstance, "ApplyPayload");
            setExecutionData(executionRuntime, changeTemplateInstance, "RollbackPayload");
            setStepsIfPresent(executionRuntime, changeTemplateInstance);
            executionRuntime.executeMethodWithInjectedDependencies(instance, method);
        } catch (Throwable ex) {
            throw new ChangeExecutionException(ex.getMessage(), this.getId(), ex);
        }
    }


    private void setExecutionData(ExecutionRuntime executionRuntime,
                                  ChangeTemplate<?, ?, ?> instance,
                                  String setterName) {
        Class<?> parameterClass;
        Object data;
        switch (setterName) {
            case "Configuration":
                parameterClass = instance.getConfigurationClass();
                data = descriptor.getConfiguration();
                break;
            case "ApplyPayload":
                parameterClass = instance.getApplyPayloadClass();
                data = descriptor.getApply();
                break;
            case "RollbackPayload":
                parameterClass = instance.getRollbackPayloadClass();
                data = descriptor.getRollback();
                break;
            default:
                throw new RuntimeException("Not found config setter for template: " + instance.getClass().getSimpleName());
        }
        Method setConfigurationMethod = getSetterMethod(instance.getClass(), "set" + setterName);

        if(data != null && Void.class != parameterClass) {
            executionRuntime.executeMethodWithParameters(
                    instance,
                    setConfigurationMethod,
                    FileUtil.getFromMap(parameterClass, data));
        } else if(Void.class != parameterClass ) {
            logger.warn("No '{}' section provided for template-based change[{}] of type[{}]", setterName, descriptor.getId(), descriptor.getTemplateClass().getName());
        }

    }


    private Method getSetterMethod(Class<?> changeTemplateClass, String methodName) {

        return Arrays.stream(changeTemplateClass.getMethods())
                .filter(m-> methodName.equals(m.getName()))
                .findFirst()
                .orElseThrow(()-> new RuntimeException("Not found config setter for template: " + changeTemplateClass.getSimpleName()));

    }

    /**
     * Sets the steps on the template if steps data is present.
     * Converts raw step data (List of Maps) to List of TemplateStep using the template's payload classes.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setStepsIfPresent(ExecutionRuntime executionRuntime,
                                   ChangeTemplate<?, ?, ?> instance) {
        Object stepsData = descriptor.getSteps();
        if (stepsData == null) {
            return;
        }

        logger.debug("Setting steps for change[{}]", descriptor.getId());

        List<TemplateStep> convertedSteps = convertToTemplateSteps(
                stepsData,
                instance.getApplyPayloadClass(),
                instance.getRollbackPayloadClass()
        );

        Method setStepsMethod = getSetterMethod(instance.getClass(), "setStepsPayload");
        executionRuntime.executeMethodWithParameters(instance, setStepsMethod, convertedSteps);
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
    private List<TemplateStep> convertToTemplateSteps(Object stepsData,
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
