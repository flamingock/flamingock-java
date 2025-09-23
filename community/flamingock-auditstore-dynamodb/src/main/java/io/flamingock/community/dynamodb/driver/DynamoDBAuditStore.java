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

import io.flamingock.community.dynamodb.internal.DynamoDBLockService;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.community.dynamodb.internal.DynamoDBAuditPersistence;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBAuditStore implements CommunityAuditStore {

    private RunnerId runnerId;
    private CommunityConfigurable communityConfiguration;
    private DynamoDBAuditPersistence persistence;
    private DynamoDBLockService lockService;
    private final DynamoDbClient client;
    private String auditRepositoryName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
    private String lockRepositoryName = CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;
    private long readCapacityUnits = 5L;
    private long writeCapacityUnits = 5L;
    private boolean autoCreate = true;

    public DynamoDBAuditStore(DynamoDbClient client) {
        this.client = client;
    }

    public DynamoDBAuditStore withAuditRepositoryName(String auditRepositoryName) {
        this.auditRepositoryName = auditRepositoryName;
        return this;
    }

    public DynamoDBAuditStore withLockRepositoryName(String lockRepositoryName) {
        this.lockRepositoryName = lockRepositoryName;
        return this;
    }

    public DynamoDBAuditStore withReadCapacityUnits(long readCapacityUnits) {
        this.readCapacityUnits = readCapacityUnits;
        return this;
    }

    public DynamoDBAuditStore withWriteCapacityUnits(long writeCapacityUnits) {
        this.writeCapacityUnits = writeCapacityUnits;
        return this;
    }

    public DynamoDBAuditStore withAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);
        this.validate();
    }

    @Override
    public synchronized CommunityAuditPersistence getPersistence() {
        if (persistence == null) {
            persistence = new DynamoDBAuditPersistence(
                    client,
                    auditRepositoryName,
                    readCapacityUnits,
                    writeCapacityUnits,
                    autoCreate,
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
                    autoCreate,
                    lockRepositoryName,
                    readCapacityUnits,
                    writeCapacityUnits);
        }
        return lockService;
    }

    private void validate() {

        if (client == null) {
            throw new FlamingockException("The 'client' instance is required.");
        }

        if (auditRepositoryName == null || auditRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'auditRepositoryName' property is required.");
        }

        if (lockRepositoryName == null || lockRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'lockRepositoryName' property is required.");
        }

        if (auditRepositoryName.trim().equalsIgnoreCase(lockRepositoryName.trim())) {
            throw new FlamingockException("The 'auditRepositoryName' and 'lockRepositoryName' properties must not be the same.");
        }
    }
}
