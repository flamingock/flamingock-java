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

package io.flamingock.core.task.loaded;

import io.flamingock.commons.utils.ReflectionUtil;
import io.flamingock.core.api.template.annotations.Config;
import io.flamingock.core.api.template.annotations.ChangeTemplateConfigValidator;
import io.flamingock.core.api.template.annotations.ChangeTemplateExecution;
import io.flamingock.core.api.template.annotations.ChangeTemplateRollbackExecution;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;


public class TemplateLoadedChangeUnit extends AbstractLoadedChangeUnit {

    private final Map<String, Object> templateConfiguration;

    TemplateLoadedChangeUnit(String id,
                             String order,
                             Class<?> templateClass,
                             boolean transactional,
                             boolean runAlways,
                             boolean systemTask,
                             Map<String, Object> templateConfiguration) {
        super(id, order, templateClass, runAlways, transactional, true, systemTask);
        this.templateConfiguration = templateConfiguration;
    }

    public Map<String, Object> getTemplateConfiguration() {
        return templateConfiguration;
    }

    @Override
    public Method getExecutionMethod() {
        return ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), ChangeTemplateExecution.class)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Templated[%s] without %s method",
                        getSource(),
                        ChangeTemplateExecution.class.getSimpleName())));
    }

    public Optional<Method> getConfigSetter() {
        return ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), Config.class);
    }

    public Optional<Method> getConfigValidator() {
        return ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), ChangeTemplateConfigValidator.class);
    }

    @Override
    public Optional<Method> getRollbackMethod() {
        Optional<Method> rollbackMethodOpt = ReflectionUtil
                .findFirstAnnotatedMethod(getSourceClass(), ChangeTemplateRollbackExecution.class);
        Optional<Method> rollbackMethod;
        if (rollbackMethodOpt.isPresent()) {
            Method potentialRollbackMethod = rollbackMethodOpt.get();
            ChangeTemplateRollbackExecution rollbackExecutionAnnotation = potentialRollbackMethod.getAnnotation(ChangeTemplateRollbackExecution.class);
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
