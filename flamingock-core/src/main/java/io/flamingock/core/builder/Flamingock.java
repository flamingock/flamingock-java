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

package io.flamingock.core.builder;

import io.flamingock.core.builder.cloud.CloudConfiguration;
import io.flamingock.core.builder.cloud.CloudSystemModuleManager;
import io.flamingock.core.builder.core.CoreConfiguration;
import io.flamingock.core.builder.local.CommunityConfiguration;
import io.flamingock.core.builder.local.LocalSystemModuleManager;
import io.flamingock.core.cloud.CloudDriver;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.runtime.dependency.SimpleDependencyInjectableContext;

public class Flamingock {

    protected Flamingock() {
    }

    public static AbstractFlamingockBuilder<?> builder() {
        Driver driver = Driver.getDriver();
        if(driver.isCloud()) {
            return new CloudFlamingockBuilder(
                    new CoreConfiguration(),
                    new CloudConfiguration(),
                    new SimpleDependencyInjectableContext(),
                    new CloudSystemModuleManager(),
                    (CloudDriver) driver);
        } else {
            return new CommunityFlamingockBuilder(
                    new CoreConfiguration(),
                    new CommunityConfiguration(),
                    new SimpleDependencyInjectableContext(),
                    new LocalSystemModuleManager(),
                    (LocalDriver) driver);
        }
    }

    protected static AbstractFlamingockBuilder<?> builder(CoreConfiguration coreConfiguration,
                                                          CloudConfiguration cloudConfiguration,
                                                          CommunityConfiguration communityConfiguration) {
        Driver driver = Driver.getDriver();
        if(driver.isCloud()) {
            return new CloudFlamingockBuilder(
                    coreConfiguration,
                    cloudConfiguration,
                    new SimpleDependencyInjectableContext(),
                    new CloudSystemModuleManager(),
                    (CloudDriver) driver);
        } else {
            return new CommunityFlamingockBuilder(
                    coreConfiguration,
                    communityConfiguration,
                    new SimpleDependencyInjectableContext(),
                    new LocalSystemModuleManager(),
                    (LocalDriver) driver);
        }
    }

    @Deprecated
    public static CloudFlamingockBuilder cloud() {
        return new CloudFlamingockBuilder(
                new CoreConfiguration(),
                new CloudConfiguration(),
                new SimpleDependencyInjectableContext(),
                new CloudSystemModuleManager(),
                CloudDriver.getDriver().orElseThrow(()-> new RuntimeException("No Cloud edition detected")));
    }

    @Deprecated
    public static CommunityFlamingockBuilder local() {
        return new CommunityFlamingockBuilder(
                new CoreConfiguration(),
                new CommunityConfiguration(),
                new SimpleDependencyInjectableContext(),
                new LocalSystemModuleManager(),
                LocalDriver.getDriver().orElseThrow(()-> new RuntimeException("No compatible Community edition detected")));
    }


}
