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

import java.util.Optional;

/**
 * Represents a container for runtime dependencies.
 * Allows retrieval of registered {@link Dependency} instances by type or name.
 * <p>
 * Also provides typed access to the instance values directly via {@code getDependencyValue}.
 */
public interface DependencyContext extends PropertyDependencyResolver {

    /**
     * Retrieves an optional dependency by its type.
     *
     * @param type the class type of the dependency
     * @return an {@link Optional} containing the dependency if found, otherwise empty
     */
    Optional<Dependency> getDependency(Class<?> type);

    /**
     * Retrieves an optional dependency by its registered name.
     *
     * @param name the name under which the dependency was registered
     * @return an {@link Optional} containing the dependency if found, otherwise empty
     */
    Optional<Dependency> getDependency(String name);

    /**
     * Retrieves the value of a dependency by type, automatically casting it.
     *
     * @param type the class type of the dependency
     * @param <T>  the type of the dependency value
     * @return an {@link Optional} containing the instance if found and castable, otherwise empty
     */
    default <T> Optional<T> getDependencyValue(Class<T> type) {
        return getDependency(type)
                .map(Dependency::getInstance)
                .map(type::cast);
    }

    /**
     * Retrieves the value of a dependency by name, automatically casting it.
     *
     * @param name the name of the dependency
     * @param type the expected type of the instance
     * @param <T>  the type of the dependency value
     * @return an {@link Optional} containing the instance if found and castable, otherwise empty
     */
    default <T> Optional<T> getDependencyValue(String name, Class<T> type) {
        return getDependency(name)
                .map(Dependency::getInstance)
                .map(type::cast);
    }
}
