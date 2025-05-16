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

package io.flamingock.core.context;



import java.util.Optional;

/**
 * A {@link DependencyContext} that prioritizes resolution from one context over another.
 * <p>
 * It delegates to a "priority" context first, and falls back to a "base" context if the dependency or property
 * is not found in the first.
 */
public class PriorityDependencyContext implements DependencyContext {

    private final DependencyContext priorityContext;
    private final DependencyContext baseContext;

    /**
     * Creates a composite dependency context where the given priority context takes precedence
     * over the base context during resolution.
     *
     * @param priorityContext the preferred context to check first
     * @param baseContext     the fallback context used if the dependency is not found in the priority context
     */
    public PriorityDependencyContext(DependencyContext priorityContext, DependencyContext baseContext) {
        this.priorityContext = priorityContext;
        this.baseContext = baseContext;
    }

    /**
     * Retrieves a dependency by type, prioritizing the {@code priorityContext} over the {@code baseContext}.
     *
     * @param type the class type of the dependency
     * @return an {@link Optional} containing the dependency if found, otherwise empty
     */
    @Override
    public Optional<Dependency> getDependency(Class<?> type) {
        Optional<Dependency> priorityDependency = priorityContext.getDependency(type);
        return priorityDependency.isPresent() ? priorityDependency : baseContext.getDependency(type);
    }

    /**
     * Retrieves a dependency by name, prioritizing the {@code priorityContext} over the {@code baseContext}.
     *
     * @param name the name of the dependency
     * @return an {@link Optional} containing the dependency if found, otherwise empty
     */
    @Override
    public Optional<Dependency> getDependency(String name) {
        Optional<Dependency> priorityDependency = priorityContext.getDependency(name);
        return priorityDependency.isPresent() ? priorityDependency : baseContext.getDependency(name);
    }

    /**
     * Retrieves a string property by key, resolving it from the merged dependency contexts.
     * Internally delegates to {@code getDependencyValue(key, String.class)}.
     *
     * @param key the property key
     * @return an {@link Optional} containing the property value if present, otherwise empty
     */
    @Override
    public Optional<String> getProperty(String key) {
        return getDependencyValue(key, String.class);
    }

    /**
     * Retrieves a typed property by key, resolving it from the merged dependency contexts.
     * Internally delegates to {@code getDependencyValue(key, type)}.
     *
     * @param key  the property key
     * @param type the target type
     * @param <T>  the type of the returned value
     * @return an {@link Optional} containing the converted value if present, otherwise empty
     */
    @Override
    public <T> Optional<T> getPropertyAs(String key, Class<T> type) {
        return getDependencyValue(key, type);
    }
}
