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
package io.flamingock.internal.core.context;


import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.context.DependencyBuildable;

import java.util.Optional;
import java.util.function.Supplier;

public abstract class AbstractSimpleContextResolver implements ContextResolver {

    @Override
    public Optional<Dependency> getDependency(Class<?> type) {
        return getDependency(()-> getByType(type));
    }

    @Override
    public Optional<Dependency> getDependency(String name) {
        if (name == null || name.isEmpty() || Dependency.DEFAULT_NAME.equals(name)) {
            throw new IllegalArgumentException("name cannot be null/empty  when retrieving dependency by name");
        }
        return  getDependency(()-> getByName(name));
    }

    private Optional<Dependency> getDependency(Supplier<Optional<Dependency>> supplier) {

        Optional<Dependency> dependencyOptional = supplier.get();
        if (!dependencyOptional.isPresent()) {
            return Optional.empty();

        }
        Dependency dependency = dependencyOptional.get();
        return DependencyBuildable.class.isAssignableFrom(dependency.getClass())
                ? getDependency(((DependencyBuildable) dependency).getImplType())
                : dependencyOptional;
    }

    @Override
    public Optional<String> getProperty(String key) {
        return getDependencyValue(key, String.class);
    }

    @Override
    public <T> Optional<T> getPropertyAs(String key, Class<T> type) {
        return getDependencyValue(key, type);
    }


    abstract protected Optional<Dependency> getByName(String name);


    abstract protected Optional<Dependency> getByType(Class<?> type);

}
