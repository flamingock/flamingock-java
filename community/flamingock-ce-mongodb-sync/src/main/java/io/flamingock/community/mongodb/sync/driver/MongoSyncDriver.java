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

import com.mongodb.ReadConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.targets.TargetSystem;
import io.flamingock.community.mongodb.sync.MongoDBSyncConfiguration;
import io.flamingock.community.mongodb.sync.internal.MongoSyncEngine;
import io.flamingock.community.mongodb.sync.internal.ReadWriteConfiguration;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.driver.LocalDriver;
import io.flamingock.internal.core.targets.DefaultTargetSystem;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;

public class MongoSyncDriver implements LocalDriver {


    protected RunnerId runnerId;
    private MongoSyncTargetSystem targetSystem;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private MongoDBSyncConfiguration driverConfiguration;
    private boolean isTransactionDisabled;


    public static MongoSyncDriver fromTargetSystem(MongoSyncTargetSystem syncTargetSystem) {
        return new MongoSyncDriver(syncTargetSystem);
    }

    public MongoSyncDriver() {
        this(null);
    }

    public MongoSyncDriver(MongoSyncTargetSystem targetSystem) {
        this.targetSystem = targetSystem;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        coreConfiguration = baseContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);
        driverConfiguration = baseContext.getDependencyValue(MongoDBSyncConfiguration.class).orElse(new MongoDBSyncConfiguration());

        isTransactionDisabled = communityConfiguration.isTransactionDisabled();

        if (targetSystem == null) {
            MongoClient mongoClient = baseContext.getRequiredDependencyValue(MongoClient.class);
            MongoDatabase mongoDatabase = baseContext.getRequiredDependencyValue(MongoDatabase.class);
            driverConfiguration.mergeConfig(baseContext);

            ReadWriteConfiguration readWriteConfiguration = new ReadWriteConfiguration(
                    driverConfiguration.getBuiltMongoDBWriteConcern(),
                    new ReadConcern(driverConfiguration.getReadConcern()),
                    driverConfiguration.getReadPreference().getValue()
            );

            targetSystem = new MongoSyncTargetSystem(DEFAULT_AUDIT_STORE_TARGET_SYSTEM)
                    .withMongoClient(mongoClient)
                    .withDatabase(mongoDatabase)
                    .withReadConcern(readWriteConfiguration.getReadConcern())
                    .withReadPreference(readWriteConfiguration.getReadPreference())
                    .withWriteConcern(readWriteConfiguration.getWriteConcern())
                    .withAutoCreate(driverConfiguration.isAutoCreate());
        }

    }

    @Override
    public LocalEngine getEngine() {
        MongoSyncEngine engine = new MongoSyncEngine(
                targetSystem,
                driverConfiguration.getAuditRepositoryName(),
                driverConfiguration.getLockRepositoryName(),
                coreConfiguration,
                communityConfiguration);
        engine.initialize(runnerId);
        return engine;
    }

    @Override
    public TargetSystem getTargetSystem() {
        //TODO this is temporal to avoid failing test for transactionDisabled
        return isTransactionDisabled
                ? new DefaultTargetSystem(DEFAULT_AUDIT_STORE_TARGET_SYSTEM)
                : targetSystem;
    }


}
