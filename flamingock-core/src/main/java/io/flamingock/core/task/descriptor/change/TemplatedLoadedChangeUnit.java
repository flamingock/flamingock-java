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

package io.flamingock.core.task.descriptor.change;

import io.flamingock.commons.utils.ReflectionUtil;
import io.flamingock.template.annotations.TemplateConfigSetter;
import io.flamingock.template.annotations.TemplateConfigValidator;
import io.flamingock.template.annotations.TemplateExecution;
import io.flamingock.template.annotations.TemplateRollbackExecution;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;


public class TemplatedLoadedChangeUnit extends AbstractLoadedChangeUnit {


    private final Map<String, Object> templateConfiguration;

    public TemplatedLoadedChangeUnit(String id,
                                     String order,
                                     Class<?> templateClass,
                                     boolean transactional,
                                     boolean runAlways,
                                     Map<String, Object> templateConfiguration) {
        super(id, order, templateClass, runAlways, transactional, true);
        this.templateConfiguration = templateConfiguration;
    }

    public Map<String, Object> getTemplateConfiguration() {
        return templateConfiguration;
    }

    @Override
    public Method getExecutionMethod() {
        return ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), TemplateExecution.class)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Templated[%s] without %s method",
                        getSourceClass().getName(),
                        TemplateExecution.class.getSimpleName())));
    }

    public Optional<Method> getConfigSetter() {
        return ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), TemplateConfigSetter.class);
    }

    public Optional<Method> getConfigValidator() {
        return ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), TemplateConfigValidator.class);
    }

    @Override
    public Optional<Method> getRollbackMethod() {
        Optional<Method> rollbackMethodOpt = ReflectionUtil
                .findFirstAnnotatedMethod(getSourceClass(), TemplateRollbackExecution.class);
        Optional<Method> rollbackMethod;
        if (rollbackMethodOpt.isPresent()) {
            Method potentialRollbackMethod = rollbackMethodOpt.get();
            TemplateRollbackExecution rollbackExecutionAnnotation = potentialRollbackMethod.getAnnotation(TemplateRollbackExecution.class);
            String[] conditionalOnAllConfigurationPropertiesNotNull = rollbackExecutionAnnotation.conditionalOnAllConfigurationPropertiesNotNull();
            if (conditionalOnAllConfigurationPropertiesNotNull == null || conditionalOnAllConfigurationPropertiesNotNull.length == 0) {
                rollbackMethod = Optional.of(potentialRollbackMethod);
            } else {
                Map<String, Object> configMap = getTemplateConfiguration();
                if (Arrays.stream(conditionalOnAllConfigurationPropertiesNotNull).allMatch(configMap::containsKey)) {
                    rollbackMethod = Optional.of(potentialRollbackMethod);
                } else {
                    rollbackMethod = Optional.empty();
                }
            }

        } else {
            rollbackMethod = Optional.empty();
        }
        return rollbackMethod;
    }
}
