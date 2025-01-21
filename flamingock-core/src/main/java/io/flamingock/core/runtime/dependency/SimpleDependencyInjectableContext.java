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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SimpleDependencyInjectableContext extends AbstractDependencyContext implements DependencyInjectableContext {

    private final LinkedHashSet<Dependency> dependencyStore;

    public SimpleDependencyInjectableContext() {
        dependencyStore = new LinkedHashSet<>();
    }

    @Override
    protected Optional<Dependency> getFromStorage(Predicate<Dependency> filter) {
        return  dependencyStore.stream().filter(filter)
                .reduce((dependency1, dependency2) -> !dependency1.isDefaultNamed() && dependency2.isDefaultNamed() ? dependency2 : dependency1);
    }

    @Override
    public void addDependency(Dependency dependency) {
        if (!dependencyStore.add(dependency)) {
            dependencyStore.remove(dependency);
            dependencyStore.add(dependency);
        }
    }

    @Override
    public void removeDependencyByRef(Dependency dependency) {
        if(dependencyStore.contains(dependency)) {
            boolean isSafeToRemove = dependencyStore.stream()
                    .filter(dependency::equals)//it only can return one at max
                    .map(Dependency::getInstance)
                    .anyMatch(storedRef -> storedRef == dependency.instance);//if it's also the same reference

            if(isSafeToRemove) {
                dependencyStore.remove(dependency);
            }

        }

    }


}
