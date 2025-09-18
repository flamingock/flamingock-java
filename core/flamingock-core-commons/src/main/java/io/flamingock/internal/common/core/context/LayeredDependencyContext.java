/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
 * Represents a context that supports layered dependency resolution.
 * <p>
 * This interface allows building a hierarchy of dependency contexts where
 * dependencies are resolved in priority order through multiple layers.
 * Each new layer added takes precedence over existing layers during
 * dependency resolution.
 * <p>
 * This is particularly useful for the {@code ExecutionRuntime} to support
 * session-scoped and transaction-scoped dependency injection, where
 * dependencies added later (e.g., during transaction initialization)
 * take precedence over base dependencies.
 */
public interface LayeredDependencyContext {

    /**
     * Adds a new context layer with higher priority than existing layers.
     * <p>
     * Dependencies in the new layer will be resolved first before
     * searching in lower layers. This enables overriding or supplementing
     * existing dependencies without modifying the base context.
     *
     * @param contextOnTop the new context layer to add with highest priority
     */
    void addContextLayer(ContextResolver contextOnTop);
}
