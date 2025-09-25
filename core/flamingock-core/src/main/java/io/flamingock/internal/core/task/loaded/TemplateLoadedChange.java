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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.internal.util.ReflectionUtil;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;


public class TemplateLoadedChange extends AbstractLoadedChange {

    private final List<String> profiles;
    private final Object configuration;
    private final Object apply;
    private final Object rollback;

    TemplateLoadedChange(String changeFileName,
                         String id,
                         String order,
                         String author,
                         Class<? extends ChangeTemplate<?, ?, ?>> templateClass,
                         List<String> profiles,
                         boolean transactional,
                         boolean runAlways,
                         boolean systemTask,
                         Object configuration,
                         Object apply,
                         Object rollback,
                         TargetSystemDescriptor targetSystem,
                         RecoveryDescriptor recovery) {
        super(changeFileName, id, order, author, templateClass, runAlways, transactional, systemTask, targetSystem, recovery);
        this.profiles = profiles;
        this.transactional = transactional;
        this.configuration = configuration;
        this.apply = apply;
        this.rollback = rollback;
    }

    public Object getConfiguration() {
        return configuration;
    }

    public Object getApply() {
        return apply;
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
        return ReflectionUtil.findFirstAnnotatedMethod(getImplementationClass(), Apply.class)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Templated[%s] without %s method",
                        getSource(),
                        Apply.class.getSimpleName())));
    }

    @Override
    public Optional<Method> getRollbackMethod() {
        return ReflectionUtil.findFirstAnnotatedMethod(getImplementationClass(), Rollback.class);
    }

}
