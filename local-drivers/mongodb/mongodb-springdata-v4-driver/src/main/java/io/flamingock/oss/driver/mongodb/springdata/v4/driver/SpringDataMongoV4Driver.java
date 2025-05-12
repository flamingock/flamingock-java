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

package io.flamingock.oss.driver.mongodb.springdata.v4.driver;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.builder.core.CoreConfigurable;
import io.flamingock.core.builder.local.CommunityConfigurable;
import io.flamingock.core.community.LocalEngine;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.community.driver.OverridesDrivers;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.oss.driver.mongodb.springdata.v4.config.SpringDataMongoV4Configuration;
import io.flamingock.oss.driver.mongodb.springdata.v4.internal.SpringDataMongoV4Engine;
import io.flamingock.oss.driver.mongodb.sync.v4.driver.MongoSync4Driver;
import org.springframework.data.mongodb.core.MongoTemplate;

@OverridesDrivers({MongoSync4Driver.class})
public class SpringDataMongoV4Driver implements LocalDriver {

    private MongoTemplate mongoTemplate;

    private SpringDataMongoV4Configuration driverConfiguration;

    public SpringDataMongoV4Driver() {
    }

    @Override
    public void initialize(DependencyContext dependencyContext) {
        this.mongoTemplate = (MongoTemplate) dependencyContext
                .getDependency(MongoTemplate.class)
                .orElseThrow(() -> new FlamingockException("MongoTemplate is needed to be added as dependency"))
                .getInstance();
        dependencyContext.getDependency(SpringDataMongoV4Configuration.class).ifPresent(dependency -> {
            this.driverConfiguration = (SpringDataMongoV4Configuration) dependency.getInstance();
        });
    }

    @Override
    public LocalEngine initializeAndGetEngine(RunnerId runnerId, CoreConfigurable coreConfiguration, CommunityConfigurable localConfiguration) {
        SpringDataMongoV4Engine engine = new SpringDataMongoV4Engine(
                mongoTemplate,
                coreConfiguration,
                localConfiguration,
                driverConfiguration != null ? driverConfiguration : SpringDataMongoV4Configuration.getDefault());
        engine.initialize(runnerId);
        return engine;
    }

}
