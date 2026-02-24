/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A test helper {@link ContextResolver} that keeps dependencies and properties in separate stacks,
 * simulating the pattern used by framework integrations (Spring Boot, Quarkus, etc.) where beans
 * come from one source and configuration properties from another.
 * <p>
 * This is intentionally NOT backed by {@link AbstractSimpleContextResolver} or {@link SimpleContext},
 * so that properties stored via {@link #addProperty} are NOT resolvable through {@code getDependency()}.
 */
class SplitStackContextResolver implements ContextResolver {

    private final Map<String, Dependency> dependenciesByName = new HashMap<>();
    private final Map<Class<?>, Dependency> dependenciesByType = new HashMap<>();
    private final Map<String, String> properties = new HashMap<>();

    void addDependency(String name, Class<?> type, Object instance) {
        Dependency dep = new Dependency(name, type, instance);
        dependenciesByName.put(name, dep);
        dependenciesByType.put(type, dep);
    }

    void addProperty(String key, String value) {
        properties.put(key, value);
    }

    @Override
    public Optional<Dependency> getDependency(Class<?> type) {
        return Optional.ofNullable(dependenciesByType.get(type));
    }

    @Override
    public Optional<Dependency> getDependency(String name) {
        return Optional.ofNullable(dependenciesByName.get(name));
    }

    @Override
    public Optional<String> getProperty(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    @Override
    public <T> Optional<T> getPropertyAs(String key, Class<T> type) {
        String value = properties.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }
}
