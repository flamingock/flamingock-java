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
package io.flamingock.internal.common.core.context;

/**
 * Interface for components capable of providing a {@link ContextResolver}.
 * <p>
 * This abstraction allows decoupling the creation or resolution of context objects
 * from their consumers, enabling better modularity and testability.
 */
public interface ContextProvider {

    /**
     * Returns the {@link ContextResolver} instance associated with this provider.
     *
     * @return a {@link ContextResolver}, never {@code null}
     */
    ContextResolver getContext();
}
