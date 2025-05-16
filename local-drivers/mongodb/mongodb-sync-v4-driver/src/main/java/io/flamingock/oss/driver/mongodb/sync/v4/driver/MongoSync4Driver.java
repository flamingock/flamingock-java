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

package io.flamingock.oss.driver.mongodb.sync.v4.driver;

import com.mongodb.client.MongoClient;
import io.flamingock.cloud.transaction.mongodb.sync.v4.cofig.MongoDBSync4Configuration;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.builder.core.CoreConfigurable;
import io.flamingock.core.builder.local.CommunityConfigurable;
import io.flamingock.core.community.LocalEngine;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.context.DependencyContext;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.MongoSync4Engine;

import java.time.Duration;

public class MongoSync4Driver implements LocalDriver {

    private MongoClient mongoClient;

    private String databaseName;
    private RunnerId runnerId;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private MongoDBSync4Configuration driverConfiguration;

    public MongoSync4Driver() {
    }

    @Override
    public void initialize(DependencyContext dependencyContext) {
        runnerId = dependencyContext.getRequiredDependencyValue(RunnerId.class);

        coreConfiguration = dependencyContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = dependencyContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.mongoClient = dependencyContext
                .getDependencyValue(MongoClient.class)
                .orElseThrow(() -> new FlamingockException("MongoClient is needed to be added as dependency"));
        this.databaseName = dependencyContext
                .getPropertyAs("databaseName", String.class)
                .orElseThrow(() -> new FlamingockException("databaseName is needed to be added as property"));
        this.driverConfiguration = generateConfig(dependencyContext);
    }

    public MongoDBSync4Configuration generateConfig(DependencyContext dependencyContext) {
        MongoDBSync4Configuration configuration = dependencyContext
                .getDependencyValue(MongoDBSync4Configuration.class)
                .orElse(MongoDBSync4Configuration.getDefault());
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
        MongoSync4Engine engine = new MongoSync4Engine(
                mongoClient,
                databaseName,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration);
        engine.initialize(runnerId);
        return engine;
    }

}
