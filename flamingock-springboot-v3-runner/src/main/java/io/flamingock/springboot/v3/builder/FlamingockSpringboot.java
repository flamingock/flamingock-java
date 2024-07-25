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

package io.flamingock.springboot.v3.builder;

import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.cloud.CloudConfiguration;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.configurator.local.LocalConfiguration;
import io.flamingock.springboot.v3.configurator.SpringbootConfiguration;

public final class FlamingockSpringboot {

    private FlamingockSpringboot() {
    }

    public static SpringbootLocalBuilder local() {
        return localBuilder(new CoreConfiguration(), new SpringbootConfiguration(), new LocalConfiguration());
    }

    public static SpringbootCloudBuilder cloud() {
        return cloudBuilder(new CoreConfiguration(), new SpringbootConfiguration(), new CloudConfiguration());
    }

    public static SpringbootLocalBuilder localBuilder(CoreConfiguration coreConfiguration,
                                               SpringbootConfiguration springbootConfiguration,
                                               LocalConfigurable localConfiguration) {
        return new SpringbootLocalBuilder(coreConfiguration, springbootConfiguration, localConfiguration);
    }

    public static SpringbootCloudBuilder cloudBuilder(CoreConfiguration coreConfiguration,
                                               SpringbootConfiguration springbootConfiguration,
                                               CloudConfiguration cloudConfiguration) {
        return new SpringbootCloudBuilder(coreConfiguration, springbootConfiguration, cloudConfiguration);
    }
}
