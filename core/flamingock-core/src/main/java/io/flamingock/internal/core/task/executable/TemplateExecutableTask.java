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
import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.TemplateLoadedChange;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.util.FileUtil;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;

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
            setStepsPayloadIfPresent(executionRuntime, changeTemplateInstance);
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
     * Sets the steps payload on the template if the template supports it and steps data is present.
     * This method uses reflection to call setStepsPayload if the template has such a method.
     * Templates that don't support steps will simply not have this method called.
     */
    private void setStepsPayloadIfPresent(ExecutionRuntime executionRuntime,
                                          ChangeTemplate<?, ?, ?> instance) {
        Object stepsData = descriptor.getSteps();
        if (stepsData == null) {
            return;
        }

        Method setStepsMethod = Arrays.stream(instance.getClass().getMethods())
                .filter(m -> "setStepsPayload".equals(m.getName()))
                .filter(m -> m.getParameterCount() == 1)
                .findFirst()
                .orElse(null);

        if (setStepsMethod != null) {
            logger.debug("Setting steps payload for change[{}]", descriptor.getId());
            executionRuntime.executeMethodWithParameters(instance, setStepsMethod, stepsData);
        } else {
            logger.warn("Template[{}] has steps defined but doesn't support setStepsPayload method",
                    instance.getClass().getSimpleName());
        }
    }




}
