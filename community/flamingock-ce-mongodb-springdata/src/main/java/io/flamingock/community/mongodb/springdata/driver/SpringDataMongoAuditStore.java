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
package io.flamingock.community.mongodb.springdata.driver;

import com.mongodb.ReadConcern;
import io.flamingock.api.targets.TargetSystem;
import io.flamingock.community.mongodb.springdata.config.SpringDataMongoConfiguration;
import io.flamingock.community.mongodb.springdata.internal.SpringDataMongoAuditPersistence;
import io.flamingock.community.mongodb.springdata.internal.SpringDataMongoLockService;
import io.flamingock.community.mongodb.sync.MongoDBSyncConfiguration;
import io.flamingock.community.mongodb.sync.driver.MongoSyncAuditStore;
import io.flamingock.community.mongodb.sync.internal.ReadWriteConfiguration;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.persistence.community.CommunityAuditPersistence;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.mongodb.springdata.MongoSpringDataTargetSystem;
import org.springframework.data.mongodb.core.MongoTemplate;


public class SpringDataMongoAuditStore extends MongoSyncAuditStore {

    private MongoSpringDataTargetSystem targetSystem;
    private CommunityConfigurable communityConfiguration;
    private MongoDBSyncConfiguration driverConfiguration;
    private SpringDataMongoAuditPersistence persistence;
    private SpringDataMongoLockService lockService;

    public static SpringDataMongoAuditStore fromTargetSystem(MongoSpringDataTargetSystem syncTargetSystem) {
        return new SpringDataMongoAuditStore(syncTargetSystem);
    }


    public SpringDataMongoAuditStore() {
        this(null);
    }

    public SpringDataMongoAuditStore(MongoSpringDataTargetSystem targetSystem) {
        this.targetSystem = targetSystem;
    }


    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);
        driverConfiguration = baseContext.getDependencyValue(SpringDataMongoConfiguration.class)
                .orElse(new SpringDataMongoConfiguration());
        
        if (targetSystem == null) {

            driverConfiguration.mergeConfig(baseContext);

            MongoTemplate mongoTemplate = baseContext.getRequiredDependencyValue(MongoTemplate.class);

            ReadWriteConfiguration readWriteConfiguration = new ReadWriteConfiguration(
                    driverConfiguration.getBuiltMongoDBWriteConcern(),
                    new ReadConcern(driverConfiguration.getReadConcern()),
                    driverConfiguration.getReadPreference().getValue()
            );

            targetSystem = new MongoSpringDataTargetSystem(DEFAULT_AUDIT_STORE_TARGET_SYSTEM)
                    .withMongoTemplate(mongoTemplate)
                    .withReadConcern(readWriteConfiguration.getReadConcern())
                    .withReadPreference(readWriteConfiguration.getReadPreference())
                    .withWriteConcern(readWriteConfiguration.getWriteConcern())
                    .withAutoCreate(driverConfiguration.isAutoCreate());
        }

    }

    @Override
    public synchronized CommunityAuditPersistence getPersistence() {
        if (persistence == null) {
            persistence = new SpringDataMongoAuditPersistence(
                    targetSystem,
                    driverConfiguration.getAuditRepositoryName(),
                    communityConfiguration);
            persistence.initialize(runnerId);
        }
        return persistence;
    }

    @Override
    public synchronized CommunityLockService getLockService() {
        if (lockService == null) {
            lockService = new SpringDataMongoLockService(targetSystem, driverConfiguration.getLockRepositoryName());
            lockService.initialize(targetSystem.isAutoCreate());
        }
        return lockService;
    }

    @Override
    public TargetSystem getTargetSystem() {
        return targetSystem;
    }

}
