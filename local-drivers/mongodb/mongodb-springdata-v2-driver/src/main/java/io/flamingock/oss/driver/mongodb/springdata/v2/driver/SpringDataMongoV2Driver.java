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

package io.flamingock.oss.driver.mongodb.springdata.v2.driver;

import io.flamingock.commons.utils.id.RunnerId;
import io.flamingock.core.builder.core.CoreConfigurable;
import io.flamingock.core.builder.local.CommunityConfigurable;
import io.flamingock.core.community.LocalEngine;
import io.flamingock.core.community.driver.OverridesDrivers;
import io.flamingock.core.context.ContextResolver;
import io.flamingock.oss.driver.mongodb.springdata.v2.config.SpringDataMongoV2Configuration;
import io.flamingock.oss.driver.mongodb.springdata.v2.internal.SpringDataMongoV2Engine;
import io.flamingock.oss.driver.mongodb.v3.driver.Mongo3Driver;
import org.springframework.data.mongodb.core.MongoTemplate;

@OverridesDrivers({Mongo3Driver.class})
public class SpringDataMongoV2Driver extends Mongo3Driver {

    private MongoTemplate mongoTemplate;
    private RunnerId runnerId;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private SpringDataMongoV2Configuration driverConfiguration;

    public SpringDataMongoV2Driver() {
    }

    @Override
    public void initialize(ContextResolver dependencyContext) {
        runnerId = dependencyContext.getRequiredDependencyValue(RunnerId.class);

        coreConfiguration = dependencyContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = dependencyContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.mongoTemplate = dependencyContext.getRequiredDependencyValue(MongoTemplate.class);

        this.driverConfiguration = dependencyContext.getDependencyValue(SpringDataMongoV2Configuration.class)
                .orElse(new SpringDataMongoV2Configuration());
        this.driverConfiguration.mergeConfig(dependencyContext);
    }

    @Override
    public LocalEngine getEngine() {
        SpringDataMongoV2Engine engine = new SpringDataMongoV2Engine(
                mongoTemplate,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration);
        engine.initialize(runnerId);
        return engine;
    }

}
