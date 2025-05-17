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

package io.flamingock.internal.core.context;

import io.flamingock.core.context.Dependency;

import java.util.Collection;

/**
 * Interface for components capable of accepting and managing dependencies dynamically.
 */
public interface DependencyInjectable {

    /**
     * Adds a collection of dependencies to the target.
     *
     * @param dependencies the dependencies to add
     */
    default void addDependencies(Collection<? extends Dependency> dependencies) {
        dependencies.forEach(this::addDependency);
    }

    /**
     * Adds a dependency by wrapping the given object in a {@link Dependency}.
     *
     * @param object the object to register as a dependency
     */
    default void addDependency(Object object) {
        if (object instanceof Dependency) {
            addDependency((Dependency) object);
        } else {
            addDependency(new Dependency(object));
        }

    }

    /**
     * Adds a fully constructed {@link Dependency} instance to the target.
     *
     * @param dependency the dependency to add
     */
    void addDependency(Dependency dependency);

    /**
     * Removes the specified dependency by reference. This operation is idempotent.
     *
     * @param dependency the exact dependency instance to be removed
     */
    void removeDependencyByRef(Dependency dependency);
}
