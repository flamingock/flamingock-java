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

package io.flamingock.importer.cloud.common;

import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.core.api.CloudSystemModule;
import io.flamingock.core.runtime.dependency.Dependency;

import java.util.Collection;
import java.util.Collections;
import java.util.List;


public interface MongockLegacyImporter extends CloudSystemModule {
    List<Class<?>> TASK_CLASSES = Collections.singletonList(
            MongockLegacyImporterChangeUnit.class
    );

    @Override
    public void initialise(EnvironmentId environmentId, ServiceId serviceId, String jwt, String serverHost);

    @Override
    public default int getOrder() {
        return 0;
    }

    @Override
    public default String getName() {
        return "mongodb-legacy-importer";
    }

    @Override
    public default Collection<Class<?>> getTaskClasses() {
        return TASK_CLASSES;
    }

    @Override
    public List<Dependency> getDependencies();

    @Override
    public default boolean isBeforeUserStages() {
        return true;
    }
}
