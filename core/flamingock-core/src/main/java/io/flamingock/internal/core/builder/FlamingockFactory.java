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

package io.flamingock.internal.core.builder;

import io.flamingock.internal.core.builder.cloud.CloudConfiguration;
import io.flamingock.internal.core.builder.core.CoreConfiguration;
import io.flamingock.internal.core.builder.local.CommunityConfiguration;
import io.flamingock.internal.core.cloud.CloudDriver;
import io.flamingock.internal.core.community.driver.LocalDriver;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.plugin.DefaultPluginManager;

public final class FlamingockFactory {

    private FlamingockFactory() {
    }

    public static AbstractFlamingockBuilder<?> getEditionAwareBuilder(CoreConfiguration coreConfiguration,
                                                                         CloudConfiguration cloudConfiguration,
                                                                         CommunityConfiguration communityConfiguration) {
        Driver<?> driver = Driver.getDriver();
        if (driver.isCloud()) {
            return new CloudFlamingockBuilder(
                    coreConfiguration,
                    cloudConfiguration,
                    new SimpleContext(),
                    new DefaultPluginManager(),
                    (CloudDriver) driver);
        } else {
            return new CommunityFlamingockBuilder(
                    coreConfiguration,
                    communityConfiguration,
                    new SimpleContext(),
                    new DefaultPluginManager(),
                    (LocalDriver) driver);
        }
    }

    public static CloudFlamingockBuilder getCloudBuilder() {
        return new CloudFlamingockBuilder(
                new CoreConfiguration(),
                new CloudConfiguration(),
                new SimpleContext(),
                new DefaultPluginManager(),
                CloudDriver.getDriver().orElseThrow(() -> new RuntimeException("No Cloud edition detected")));
    }

    public static CommunityFlamingockBuilder getCommunityBuilder() {
        return new CommunityFlamingockBuilder(
                new CoreConfiguration(),
                new CommunityConfiguration(),
                new SimpleContext(),
                new DefaultPluginManager(),
                LocalDriver.getDriver().orElseThrow(() -> new RuntimeException("No compatible Community edition detected")));
    }

}
