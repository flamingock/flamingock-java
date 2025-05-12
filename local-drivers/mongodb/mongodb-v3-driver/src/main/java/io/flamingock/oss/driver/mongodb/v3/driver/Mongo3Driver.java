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

package io.flamingock.oss.driver.mongodb.v3.driver;

import com.mongodb.client.MongoClient;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.builder.core.CoreConfigurable;
import io.flamingock.core.builder.local.CommunityConfigurable;
import io.flamingock.core.community.LocalEngine;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.oss.driver.mongodb.v3.MongoDB3Configuration;
import io.flamingock.oss.driver.mongodb.v3.internal.Mongo3Engine;

public class Mongo3Driver implements LocalDriver {

    private MongoClient mongoClient;

    private String databaseName;

    private MongoDB3Configuration driverConfiguration;

    public Mongo3Driver() {
    }

    @Override
    public void initialize(DependencyContext dependencyContext) {
        this.mongoClient = (MongoClient) dependencyContext
                .getDependency(MongoClient.class)
                .orElseThrow(() -> new FlamingockException("MongoClient is needed to be added as dependency"))
                .getInstance();
        this.databaseName = (String) dependencyContext
                .getDependency("databaseName")
                .orElseThrow(() -> new FlamingockException("databaseName is needed to be added as property"))
                .getInstance();
        dependencyContext.getDependency(MongoDB3Configuration.class).ifPresent(dependency -> {
            this.driverConfiguration = (MongoDB3Configuration) dependency.getInstance();
        });
    }

    @Override
    public LocalEngine initializeAndGetEngine(RunnerId runnerId, CoreConfigurable coreConfiguration, CommunityConfigurable localConfiguration) {
        Mongo3Engine engine = new Mongo3Engine(
                mongoClient,
                databaseName,
                coreConfiguration,
                localConfiguration,
                driverConfiguration != null ? driverConfiguration : MongoDB3Configuration.getDefault());

        engine.initialize(runnerId);
        return engine;
    }

}
