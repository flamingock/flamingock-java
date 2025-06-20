/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.core.task.loaded.TemplateLoadedChangeUnit;
import io.flamingock.internal.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

public class TemplateExecutableTask extends ReflectionExecutableTask<TemplateLoadedChangeUnit> {
    private final Logger logger = LoggerFactory.getLogger("Flamingock-TemplateExecutableTask");

    public TemplateExecutableTask(String stageName,
                                  TemplateLoadedChangeUnit descriptor,
                                  boolean requiredExecution,
                                  Method executionMethod,
                                  Method rollbackMethod) {
        super(stageName, descriptor, requiredExecution, executionMethod, rollbackMethod);
    }

    @Override
    protected void executeInternal(RuntimeManager runtimeManager, Method method ) {
        logger.debug("Starting execution of changeUnit[{}] with template: {}", descriptor.getId(), descriptor.getTemplateClass());
        logger.debug("changeUnit[{}] transactional: {}", descriptor.getId(), descriptor.isTransactional());
        Object instance = runtimeManager.getInstance(descriptor.getConstructor());
        ChangeTemplate<?,?,?> changeTemplateInstance = (ChangeTemplate<?,?,?>) instance;
        changeTemplateInstance.setTransactional(descriptor.isTransactional());
        setExecutionData(runtimeManager, changeTemplateInstance, "Configuration");
        setExecutionData(runtimeManager, changeTemplateInstance, "Execution");
        setExecutionData(runtimeManager, changeTemplateInstance, "Rollback");
        runtimeManager.executeMethodWithInjectedDependencies(instance, method);
    }


    private void setExecutionData(RuntimeManager runtimeManager,
                                  ChangeTemplate<?, ?, ?> instance,
                                  String setterName) {
        Class<?> parameterClass;
        Object data;
        switch (setterName) {
            case "Configuration":
                parameterClass = instance.getConfigurationClass();
                data = descriptor.getConfiguration();
                break;
            case "Execution":
                parameterClass = instance.getExecutionClass();
                data = descriptor.getExecution();
                break;
            case "Rollback":
                parameterClass = instance.getRollbackClass();
                data = descriptor.getRollback();
                break;
            default:
                throw new RuntimeException("Not found config setter for template: " + instance.getClass().getSimpleName());
        }
        Method setConfigurationMethod = getSetterMethod(instance.getClass(), "set" + setterName);

        if(data != null && Void.class != parameterClass) {
            runtimeManager.executeMethodWithParameters(
                    instance,
                    setConfigurationMethod,
                    FileUtil.getFromMap(parameterClass, data));
        } else if(Void.class != parameterClass ) {
            logger.warn("No '{}' section provided for template-based changeUnit[{}] of type[{}]", setterName, descriptor.getId(), descriptor.getTemplateClass().getName());
        }

    }


    private Method getSetterMethod(Class<?> changeTemplateClass, String methodName) {

        return Arrays.stream(changeTemplateClass.getMethods())
                .filter(m-> methodName.equals(m.getName()))
                .findFirst()
                .orElseThrow(()-> new RuntimeException("Not found config setter for template: " + changeTemplateClass.getSimpleName()));

    }




}
