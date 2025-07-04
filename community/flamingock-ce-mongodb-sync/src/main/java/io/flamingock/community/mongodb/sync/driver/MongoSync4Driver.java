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

package io.flamingock.community.mongodb.sync.driver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.cloud.transaction.mongodb.sync.config.MongoDBSync4Configuration;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.driver.LocalDriver;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.community.mongodb.sync.internal.MongoSync4Engine;

public class MongoSync4Driver implements LocalDriver {

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private RunnerId runnerId;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private MongoDBSync4Configuration driverConfiguration;

    @Override
    public void initialize(ContextResolver contextResolver) {
        runnerId = contextResolver.getRequiredDependencyValue(RunnerId.class);
        coreConfiguration = contextResolver.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = contextResolver.getRequiredDependencyValue(CommunityConfigurable.class);
        mongoClient = contextResolver.getRequiredDependencyValue(MongoClient.class);
        mongoDatabase = contextResolver.getRequiredDependencyValue(MongoDatabase.class);
        driverConfiguration = contextResolver.getDependencyValue(MongoDBSync4Configuration.class).orElse(new MongoDBSync4Configuration());
        driverConfiguration.mergeConfig(contextResolver);
    }

    @Override
    public LocalEngine getEngine() {
        MongoSync4Engine engine = new MongoSync4Engine(
                mongoClient,
                mongoDatabase,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration);
        engine.initialize(runnerId);
        return engine;
    }

}
