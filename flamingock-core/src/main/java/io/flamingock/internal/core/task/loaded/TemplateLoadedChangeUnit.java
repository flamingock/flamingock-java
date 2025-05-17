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

package io.flamingock.internal.core.task.loaded;

import io.flamingock.commons.utils.ReflectionUtil;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.RollbackExecution;
import io.flamingock.core.api.template.ChangeTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class TemplateLoadedChangeUnit extends AbstractLoadedChangeUnit {

    private final Map<String, Object> templateConfiguration;
    private final List<String> profiles;

    TemplateLoadedChangeUnit(String id,
                             String order,
                             Class<? extends ChangeTemplate<?>> templateClass,
                             List<String> profiles,
                             boolean transactional,
                             boolean runAlways,
                             boolean systemTask,
                             Map<String, Object> templateConfiguration) {
        super(id, order, templateClass, runAlways, transactional, true, systemTask);
        this.profiles = profiles;
        this.templateConfiguration = templateConfiguration;
    }

    public Map<String, Object> getTemplateConfiguration() {
        return templateConfiguration;
    }

    public List<String> getProfiles() {
        return profiles;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends ChangeTemplate<?>> getTemplateClass() {
        return (Class<? extends ChangeTemplate<?>>) this.getSourceClass();
    }

    @Override
    public Method getExecutionMethod() {
        return ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), Execution.class)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Templated[%s] without %s method",
                        getSource(),
                        Execution.class.getSimpleName())));
    }

    @Override
    public Optional<Method> getRollbackMethod() {
        return ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), RollbackExecution.class);
    }
}
