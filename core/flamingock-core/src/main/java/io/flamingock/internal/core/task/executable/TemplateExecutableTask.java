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
    private final Logger logger = LoggerFactory.getLogger("TemplateExecutableTask");

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
        ChangeTemplate<?> changeTemplateInstance = (ChangeTemplate<?>) instance;
        changeTemplateInstance.setTransactional(descriptor.isTransactional());
        setConfiguration(runtimeManager, changeTemplateInstance);
        runtimeManager.executeMethodWithInjectedDependencies(instance, method);
    }

    private void setConfiguration(RuntimeManager runtimeManager, ChangeTemplate<?> instance) {
        Class<?> configClass = instance.getConfigClass();
        Method setConfigurationMethod = getConfigMethod(instance.getClass());
        runtimeManager.executeMethodWithParameters(
                instance,
                setConfigurationMethod,
                FileUtil.getFromMap(configClass, descriptor.getTemplateConfiguration()));
    }

    private Method getConfigMethod(Class<?> changeTemplateClass) {

        return Arrays.stream(changeTemplateClass.getMethods())
                .filter(m-> "setConfiguration".equals(m.getName()))
                .findFirst()
                .orElseThrow(()-> new RuntimeException("Not found config setter for template: " + changeTemplateClass.getSimpleName()));

    }




}
