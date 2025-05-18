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

package io.flamingock.internal.core.system;

import io.flamingock.internal.core.context.ContextContributor;
import io.flamingock.internal.core.context.ContextInitializable;
import io.flamingock.internal.core.context.ContextResolver;
import io.flamingock.core.preview.PreviewStage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface SystemModuleManager extends ContextInitializable, ContextContributor {

    void add(SystemModule module);

    Iterable<SystemModule> getModules();

    void initialize(ContextResolver dependencyContext);

    default List<PreviewStage> getSortedSystemStagesBefore() {
        return Helper.getSortedSystemStages(getModules(), true);
    }

    default List<PreviewStage> getSortedSystemStagesAfter() {
        return Helper.getSortedSystemStages(getModules(), false);
    }

    final class Helper {

        private Helper() {
        }

        private static List<PreviewStage> getSortedSystemStages(Iterable<SystemModule> modulesIterable, boolean isBefore) {

            Collection<SystemModule> modules = Collection.class.isAssignableFrom(modulesIterable.getClass())
                    ? (Collection<SystemModule>) modulesIterable
                    : fromIterableToCollection(modulesIterable);

            List<SystemModule> sortedModules = new ArrayList<>(modules);
            Collections.sort(sortedModules);
            Stream<SystemModule> stream = sortedModules.stream();
            stream = isBefore ? stream.filter(SystemModule::isBeforeUserStages) : stream.filter(m -> !m.isBeforeUserStages());

            return stream.map(SystemModule::getStage).collect(Collectors.toList());
        }

        private static  ArrayList<SystemModule> fromIterableToCollection(Iterable<SystemModule> modulesIterable) {
            return StreamSupport.stream(modulesIterable.spliterator(), false).collect(Collectors.toCollection(ArrayList::new));
        }
    }

}
