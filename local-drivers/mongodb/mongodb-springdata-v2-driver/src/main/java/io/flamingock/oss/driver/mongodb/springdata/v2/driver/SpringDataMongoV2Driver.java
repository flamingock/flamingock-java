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

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.builder.core.CoreConfigurable;
import io.flamingock.core.builder.local.CommunityConfigurable;
import io.flamingock.core.community.LocalEngine;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.community.driver.OverridesDrivers;
import io.flamingock.core.context.DependencyContext;
import io.flamingock.oss.driver.mongodb.springdata.v2.config.SpringDataMongoV2Configuration;
import io.flamingock.oss.driver.mongodb.springdata.v2.internal.SpringDataMongoV2Engine;
import io.flamingock.oss.driver.mongodb.v3.driver.Mongo3Driver;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Duration;

@OverridesDrivers({Mongo3Driver.class})
public class SpringDataMongoV2Driver implements LocalDriver {

    private MongoTemplate mongoTemplate;

    private RunnerId runnerId;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private SpringDataMongoV2Configuration driverConfiguration;

    public SpringDataMongoV2Driver() {
    }

    @Override
    public void initialize(DependencyContext dependencyContext) {
        runnerId = dependencyContext.getRequiredDependencyValue(RunnerId.class);

        coreConfiguration = dependencyContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = dependencyContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.mongoTemplate = dependencyContext
                .getDependencyValue(MongoTemplate.class)
                .orElseThrow(() -> new FlamingockException("MongoTemplate is needed to be added as dependency"));
        this.driverConfiguration = generateConfig(dependencyContext);
    }

    public SpringDataMongoV2Configuration generateConfig(DependencyContext dependencyContext) {
        SpringDataMongoV2Configuration configuration = dependencyContext
                .getDependencyValue(SpringDataMongoV2Configuration.class)
                .orElse(SpringDataMongoV2Configuration.getDefault());
        dependencyContext.getPropertyAs("mongodb.autoCreate", boolean.class)
                .ifPresent(configuration::setIndexCreation);
        dependencyContext.getPropertyAs("mongodb.auditRepositoryName", String.class)
                .ifPresent(configuration::setMigrationRepositoryName);
        dependencyContext.getPropertyAs("mongodb.lockRepositoryName", String.class)
                .ifPresent(configuration::setLockRepositoryName);
        dependencyContext.getPropertyAs("mongodb.readConcern", String.class)
                .ifPresent(d -> {
                    //TODO
                });
        dependencyContext.getPropertyAs("mongodb.writeConcern.w", String.class)
                .ifPresent(d -> {
                    //TODO
                });
        dependencyContext.getPropertyAs("mongodb.writeConcern.journal", boolean.class)
                .ifPresent(d -> {
                    //TODO
                });
        dependencyContext.getPropertyAs("mongodb.writeConcern.wTimeout", Duration.class)
                .ifPresent(d -> {
                    //TODO
                });
        dependencyContext.getPropertyAs("mongodb.readPreference", String.class)
                .ifPresent(d -> {
                    //TODO
                });
        return configuration;
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
