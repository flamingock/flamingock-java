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

import io.flamingock.api.annotations.Execution;
import io.flamingock.api.annotations.RollbackExecution;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.internal.util.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;


public class TemplateLoadedChangeUnit extends AbstractLoadedChangeUnit {

    private final List<String> profiles;
    private final Object configuration;
    private final Object execution;
    private final Object rollback;

    TemplateLoadedChangeUnit(String changeUnitFileName,
                             String id,
                             String order,
                             Class<? extends ChangeTemplate<?, ?, ?>> templateClass,
                             List<String> profiles,
                             boolean transactional,
                             boolean runAlways,
                             boolean systemTask,
                             Object configuration,
                             Object execution,
                             Object rollback) {
        super(changeUnitFileName, id, order, templateClass, runAlways, transactional, systemTask);
        this.profiles = profiles;
        this.transactional = transactional;
        this.configuration = configuration;
        this.execution = execution;
        this.rollback = rollback;
    }

    public Object getConfiguration() {
        return configuration;
    }

    public Object getExecution() {
        return execution;
    }

    public Object getRollback() {
        return rollback;
    }

    public List<String> getProfiles() {
        return profiles;
    }


    @SuppressWarnings("unchecked")
    public Class<? extends ChangeTemplate<?, ?, ?>> getTemplateClass() {
        return (Class<? extends ChangeTemplate<?, ?, ?>>) this.getImplementationClass();
    }

    @Override
    public Method getExecutionMethod() {
        return ReflectionUtil.findFirstAnnotatedMethod(getImplementationClass(), Execution.class)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Templated[%s] without %s method",
                        getSource(),
                        Execution.class.getSimpleName())));
    }

    @Override
    public Optional<Method> getRollbackMethod() {
        return ReflectionUtil.findFirstAnnotatedMethod(getImplementationClass(), RollbackExecution.class);
    }

}
