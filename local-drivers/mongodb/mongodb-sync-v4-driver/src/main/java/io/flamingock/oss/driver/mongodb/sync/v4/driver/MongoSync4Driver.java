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
import io.flamingock.cloud.transaction.mongodb.sync.v4.config.MongoDBSync4Configuration;
import io.flamingock.commons.utils.id.RunnerId;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.driver.LocalDriver;
import io.flamingock.internal.core.context.ContextResolver;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.MongoSync4Engine;

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
    public void initialize(ContextResolver dependencyContext) {
        runnerId = dependencyContext.getRequiredDependencyValue(RunnerId.class);

        coreConfiguration = dependencyContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = dependencyContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.mongoClient = dependencyContext.getRequiredDependencyValue(MongoClient.class);
        this.databaseName = dependencyContext.getRequiredPropertyAs("mongodb.databaseName", String.class);

        this.driverConfiguration = dependencyContext.getDependencyValue(MongoDBSync4Configuration.class)
                .orElse(new MongoDBSync4Configuration());
        this.driverConfiguration.mergeConfig(dependencyContext);
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
