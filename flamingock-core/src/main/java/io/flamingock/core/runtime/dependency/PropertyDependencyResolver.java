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
 * Interface for resolving configuration properties from a dependency-aware context.
 * <p>
 * Allows retrieving properties as raw {@code String} values or converted to a specific type.
 */
public interface PropertyDependencyResolver {

    /**
     * Retrieves the property value associated with the given key as a {@link String}.
     *
     * @param key the property key
     * @return an {@link Optional} containing the string value if present, otherwise empty
     */
    Optional<String> getProperty(String key);

    /**
     * Retrieves the property value associated with the given key and converts it to the specified type.
     *
     * @param key  the property key
     * @param type the target type to convert the value to
     * @param <T>  the expected type of the returned value
     * @return an {@link Optional} containing the converted value if present and successfully converted, otherwise empty
     */
    <T> Optional<T> getPropertyAs(String key, Class<T> type);
}
