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

import io.flamingock.internal.common.core.context.ContextInjectable;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.system.SystemModule;
import io.flamingock.internal.common.core.system.SystemModuleManager;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultSystemModuleManager implements SystemModuleManager {

    private final Set<SystemModule> systemModules = new LinkedHashSet<>();

    @Override
    public void initialize(ContextResolver dependencyContext) {
        systemModules.forEach(module -> module.initialize(dependencyContext));
    }

    @Override
    public void add(SystemModule module) {
        systemModules.add(module);
    }

    @Override
    public Iterable<SystemModule> getModules() {
        return systemModules;
    }

    @Override
    public void contributeToContext(ContextInjectable contextInjectable) {
        systemModules.forEach(module -> module.contributeToContext(contextInjectable));
    }
}
