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

import java.util.Collection;

public interface DependencyInjectable {

    default void addDependencies(Collection<? extends Dependency> dependencies) {
        dependencies.forEach(this::addDependency);
    }

    default void addDependency(Object object) {
        addDependency(new Dependency(object));
    }

    void addDependency(Dependency dependency);

    /**
     * Idempotent removal by reference
     * @param dependency the dependency to me removed
     */
    void removeDependencyByRef(Dependency dependency);
}
