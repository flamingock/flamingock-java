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

package io.flamingock.core.task.executable.template;

import io.flamingock.core.runtime.RuntimeManager;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.task.descriptor.TemplatedTaskDescriptor;
import io.flamingock.core.task.executable.ReflectionExecutableTask;
import io.flamingock.commons.utils.FileUtil;
import io.flamingock.commons.utils.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.List;

public class TemplatedExecutableTask extends ReflectionExecutableTask<TemplatedTaskDescriptor> {
    private final Method configValidatorMethod;

    private final Method configSetterMethod;


    public TemplatedExecutableTask(String stageName,
                                   TemplatedTaskDescriptor descriptor,
                                   boolean requiredExecution,
                                   Method executionMethod,
                                   Method rollbackMethod,
                                   Method configSetterMethod,
                                   Method configValidatorMethod) {
        super(stageName, descriptor, requiredExecution, executionMethod, rollbackMethod);
        this.configSetterMethod = configSetterMethod;
        this.configValidatorMethod = configValidatorMethod;
    }

    @Override
    protected void executeInternal(RuntimeManager runtimeManager, Method method ) {
        Object instance = runtimeManager.getInstance(descriptor.getSourceClass());
        setConfiguration(runtimeManager, instance);
        runtimeManager.executeMethod(instance, method);
    }

    private void setConfiguration(RuntimeManager runtimeManager, Object instance) {
        if(configSetterMethod != null ) {
            List<Class<?>> parameters = ReflectionUtil.getParameters(configSetterMethod);
            if(!parameters.isEmpty()) {
                Dependency dependency = new Dependency(
                        parameters.get(0),
                        FileUtil.getFromMap(parameters.get(0), descriptor.getTemplateConfiguration()));
                runtimeManager.addDependency(dependency);
            }
            runtimeManager.executeMethod(instance, configSetterMethod);
        }

        validateConfiguration(runtimeManager, instance);
    }

    private void validateConfiguration(RuntimeManager runtimeManager, Object instance) {
        if(configValidatorMethod != null) {
            runtimeManager.executeMethod(instance, configValidatorMethod);
        }
    }


}
