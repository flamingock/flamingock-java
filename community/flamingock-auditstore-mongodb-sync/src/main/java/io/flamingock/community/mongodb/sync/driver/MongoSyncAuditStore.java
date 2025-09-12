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
import io.flamingock.community.mongodb.sync.internal.MongoSyncAuditPersistence;
import io.flamingock.community.mongodb.sync.internal.MongoSyncLockService;
import io.flamingock.community.mongodb.sync.internal.ReadWriteConfiguration;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;

public class MongoSyncAuditStore implements CommunityAuditStore {


    protected RunnerId runnerId;
    private MongoSyncTargetSystem targetSystem;
    private CommunityConfigurable communityConfiguration;
    private MongoDBSyncConfiguration driverConfiguration;
    private MongoSyncAuditPersistence persistence;
    private MongoSyncLockService lockService;


    public static MongoSyncAuditStore fromTargetSystem(MongoSyncTargetSystem syncTargetSystem) {
        return new MongoSyncAuditStore(syncTargetSystem);
    }

    public MongoSyncAuditStore() {
        this(null);
    }

    public MongoSyncAuditStore(MongoSyncTargetSystem targetSystem) {
        this.targetSystem = targetSystem;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);
        driverConfiguration = baseContext.getDependencyValue(MongoDBSyncConfiguration.class).orElse(new MongoDBSyncConfiguration());

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
    public synchronized CommunityAuditPersistence getPersistence() {
        if (persistence == null) {
            persistence = new MongoSyncAuditPersistence(
                    targetSystem,
                    driverConfiguration.getAuditRepositoryName(),
                    communityConfiguration
            );
            persistence.initialize(runnerId);
        }
        return persistence;
    }

    @Override
    public synchronized CommunityLockService getLockService() {
        if (lockService == null) {
            lockService = new MongoSyncLockService(
                    targetSystem,
                    driverConfiguration.getLockRepositoryName(),
                    TimeService.getDefault()
            );
            lockService.initialize(targetSystem.isAutoCreate());

        }
        return lockService;
    }


    @Override
    public TargetSystem getTargetSystem() {
        return targetSystem;
    }


}
