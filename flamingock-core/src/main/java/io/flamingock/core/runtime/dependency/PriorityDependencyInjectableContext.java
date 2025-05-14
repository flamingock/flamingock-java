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

/**
 * A {@link DependencyInjectableContext} that combines a writable priority context
 * with a read-only fallback context.
 * <p>
 * Dependency resolution prioritizes the {@code priorityInjectableContext}, but dependency
 * injection operations (add/remove) only affect the priority context.
 */
public class PriorityDependencyInjectableContext extends PriorityDependencyContext implements DependencyInjectableContext {

    private final DependencyInjectableContext priorityInjectableContext;

    /**
     * Creates a context that combines a writable {@link SimpleDependencyInjectableContext}
     * with a read-only fallback base context.
     *
     * @param baseContext the fallback context to be used when the dependency is not found in the priority context
     */
    public PriorityDependencyInjectableContext(DependencyContext baseContext) {
        this(new SimpleDependencyInjectableContext(), baseContext);
    }

    /**
     * Creates a context that combines the given writable {@code priorityInjectableContext}
     * with the given read-only {@code baseContext}.
     *
     * @param priorityInjectableContext the context where dependencies are added or removed
     * @param baseContext               the fallback context used only for resolution
     */
    public PriorityDependencyInjectableContext(DependencyInjectableContext priorityInjectableContext, DependencyContext baseContext) {
        super(priorityInjectableContext, baseContext);
        this.priorityInjectableContext = priorityInjectableContext;
    }

    /**
     * Adds a dependency to the priority (writable) context.
     *
     * @param dependency the dependency to add
     */
    @Override
    public void addDependency(Dependency dependency) {
        priorityInjectableContext.addDependency(dependency);
    }

    /**
     * Removes the specified dependency from the priority (writable) context only.
     * This operation is idempotent and does not affect the base context.
     *
     * @param dependency the dependency to be removed
     */
    @Override
    public void removeDependencyByRef(Dependency dependency) {
        priorityInjectableContext.removeDependencyByRef(dependency);
    }
}
