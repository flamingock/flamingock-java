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

package io.flamingock.core.runtime.dependency;

import io.flamingock.core.runtime.dependency.exception.ForbiddenParameterException;

import java.util.Optional;

public class PriorityDependencyInjectableContext implements DependencyInjectableContext {

    private final DependencyContext baseContext;

    private final DependencyInjectableContext priorityInjectableContext;

    public PriorityDependencyInjectableContext(DependencyContext baseContext) {
        this(new SimpleDependencyInjectableContext(), baseContext);
    }

    public PriorityDependencyInjectableContext(DependencyInjectableContext priorityInjectableContext, DependencyContext baseContext) {
        this.priorityInjectableContext = priorityInjectableContext;
        this.baseContext = baseContext;
    }

    @Override
    public Optional<Dependency> getDependency(Class<?> type) throws ForbiddenParameterException {
        Optional<Dependency> priorityDependency = priorityInjectableContext.getDependency(type);
        return priorityDependency.isPresent() ? priorityDependency : baseContext.getDependency(type);

    }

    @Override
    public Optional<Dependency> getDependency(String name) throws ForbiddenParameterException {
        Optional<Dependency> priorityDependency = priorityInjectableContext.getDependency(name);
        return priorityDependency.isPresent() ? priorityDependency : baseContext.getDependency(name);
    }

    @Override
    public void addDependency(Dependency dependency) {
        priorityInjectableContext.addDependency(dependency);
    }
}
