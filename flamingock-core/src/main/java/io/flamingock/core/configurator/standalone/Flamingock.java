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

package io.flamingock.core.configurator.standalone;

import io.flamingock.core.configurator.cloud.CloudConfiguration;
import io.flamingock.core.configurator.cloud.CloudSystemModuleManager;
import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.local.LocalConfiguration;
import io.flamingock.core.configurator.local.LocalSystemModuleManager;
import io.flamingock.core.runner.RunnerBuilder;
import io.flamingock.core.runtime.dependency.SimpleDependencyInjectableContext;

public final class Flamingock {

    private Flamingock() {
    }

    public static AbstractFlamingockBuilder<?> builder() {
        return new FlamingockCloudBuilder(
                new CoreConfiguration(),
                new CloudConfiguration(),
                new SimpleDependencyInjectableContext(),
                new CloudSystemModuleManager());
    }


    public static FlamingockCloudBuilder cloud() {
        return new FlamingockCloudBuilder(
                new CoreConfiguration(),
                new CloudConfiguration(),
                new SimpleDependencyInjectableContext(),
                new CloudSystemModuleManager());
    }

    public static FlamingockLocalBuilder local() {
        return new FlamingockLocalBuilder(
                new CoreConfiguration(),
                new LocalConfiguration(),
                new SimpleDependencyInjectableContext(),
                new LocalSystemModuleManager());
    }

    private static boolean isCommunityEdition() {
        return false;
    }

}
