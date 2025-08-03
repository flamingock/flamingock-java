/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
import io.flamingock.cloud.transaction.mongodb.sync.config.MongoDBSyncConfiguration;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.driver.LocalDriver;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.community.mongodb.sync.internal.MongoSyncEngine;

public class MongoSyncDriver implements LocalDriver {

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private RunnerId runnerId;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private MongoDBSyncConfiguration driverConfiguration;

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        coreConfiguration = baseContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);
        mongoClient = baseContext.getRequiredDependencyValue(MongoClient.class);
        mongoDatabase = baseContext.getRequiredDependencyValue(MongoDatabase.class);
        driverConfiguration = baseContext.getDependencyValue(MongoDBSyncConfiguration.class).orElse(new MongoDBSyncConfiguration());
        driverConfiguration.mergeConfig(baseContext);
    }

    @Override
    public LocalEngine getEngine() {
        MongoSyncEngine engine = new MongoSyncEngine(
                mongoClient,
                mongoDatabase,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration);
        engine.initialize(runnerId);
        return engine;
    }

}
