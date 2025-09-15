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
package io.flamingock.community.dynamodb.driver;

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.community.dynamodb.internal.DynamoDBLockService;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.community.dynamodb.DynamoDBConfiguration;
import io.flamingock.community.dynamodb.internal.DynamoDBAuditPersistence;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBAuditStore implements CommunityAuditStore {

    private RunnerId runnerId;
    private CommunityConfigurable communityConfiguration;
    private DynamoDBConfiguration driverConfiguration;
    private DynamoDBAuditPersistence persistence;
    private DynamoDBLockService lockService;
    private DynamoDbClient client;
    private Boolean autoCreate;

    public DynamoDBAuditStore() {}

    public DynamoDBAuditStore withDynamoDBClient(DynamoDbClient client) {
        this.client = client;
        return this;
    }

    public DynamoDBAuditStore withAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    private boolean isAutoCreateEnabled() {
        return autoCreate != null ? autoCreate : driverConfiguration.isAutoCreate();
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.driverConfiguration = baseContext.getDependencyValue(DynamoDBConfiguration.class)
                .orElse(new DynamoDBConfiguration());
        this.driverConfiguration.mergeConfig(baseContext);

        if (client == null) {
            client = baseContext.getRequiredDependencyValue(DynamoDbClient.class);
        }
    }

    @Override
    public synchronized CommunityAuditPersistence getPersistence() {
        if (persistence == null) {
            persistence = new DynamoDBAuditPersistence(
                    client,
                    driverConfiguration.getAuditRepositoryName(),
                    driverConfiguration.getReadCapacityUnits(),
                    driverConfiguration.getWriteCapacityUnits(),
                    isAutoCreateEnabled(),
                    communityConfiguration);
            persistence.initialize(runnerId);
        }
        return persistence;
    }

    @Override
    public synchronized CommunityLockService getLockService() {
        if (lockService == null) {
            lockService = new DynamoDBLockService(client, TimeService.getDefault());
            lockService.initialize(
                    isAutoCreateEnabled(),
                    driverConfiguration.getLockRepositoryName(),
                    driverConfiguration.getReadCapacityUnits(),
                    driverConfiguration.getWriteCapacityUnits());
        }
        return lockService;
    }

    @Override
    public TargetSystem getTargetSystem() {
        return null;
    }
}
