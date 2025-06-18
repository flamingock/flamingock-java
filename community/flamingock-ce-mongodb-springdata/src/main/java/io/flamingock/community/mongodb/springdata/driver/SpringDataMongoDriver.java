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

package io.flamingock.community.mongodb.springdata.driver;

import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.driver.OverridesDrivers;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.community.mongodb.springdata.config.SpringDataMongoConfiguration;
import io.flamingock.community.mongodb.springdata.internal.SpringDataMongoEngine;
import io.flamingock.community.mongodb.sync.driver.MongoSync4Driver;
import org.springframework.data.mongodb.core.MongoTemplate;

@OverridesDrivers({MongoSync4Driver.class})
public class SpringDataMongoDriver extends MongoSync4Driver {

    private MongoTemplate mongoTemplate;
    private RunnerId runnerId;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private SpringDataMongoConfiguration driverConfiguration;

    public SpringDataMongoDriver() {
    }

    @Override
    public void initialize(ContextResolver dependencyContext) {
        runnerId = dependencyContext.getRequiredDependencyValue(RunnerId.class);

        coreConfiguration = dependencyContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = dependencyContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.mongoTemplate = dependencyContext.getRequiredDependencyValue(MongoTemplate.class);

        this.driverConfiguration = dependencyContext.getDependencyValue(SpringDataMongoConfiguration.class)
                .orElse(new SpringDataMongoConfiguration());
        this.driverConfiguration.mergeConfig(dependencyContext);
    }

    @Override
    public LocalEngine getEngine() {
        SpringDataMongoEngine engine = new SpringDataMongoEngine(
                mongoTemplate,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration);
        engine.initialize(runnerId);
        return engine;
    }

}
