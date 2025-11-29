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
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.community.mongodb.sync.internal.MongoDBSyncAuditPersistence;
import io.flamingock.community.mongodb.sync.internal.MongoDBSyncLockService;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetystem.mongodb.sync.MongoDBSyncTargetSystem;

import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;

public class MongoDBSyncAuditStore implements CommunityAuditStore {


    protected RunnerId runnerId;
    private CommunityConfigurable communityConfiguration;
    private MongoDBSyncAuditPersistence persistence;
    private MongoDBSyncLockService lockService;
    private final MongoClient client;
    private final String databaseName;
    private MongoDatabase database;
    private String auditRepositoryName = DEFAULT_AUDIT_STORE_NAME;
    private String lockRepositoryName = DEFAULT_LOCK_STORE_NAME;
    private ReadConcern readConcern = ReadConcern.MAJORITY;
    private ReadPreference readPreference = ReadPreference.primary();
    private WriteConcern writeConcern = WriteConcern.MAJORITY.withJournal(true);
    private boolean autoCreate = true;


    private MongoDBSyncAuditStore(MongoClient client, String databaseName) {
        this.client = client;
        this.databaseName = databaseName;
    }

    /**
     * Generate a new MongoDBSyncAuditStore instance from the specified MongoDBSyncTargetSystem.
     * This method uses the MongoClient and database name from the target system to create the audit store.
     *
     * @param mongoDBSyncTargetSystem the MongoDBSyncTargetSystem instance to extract the client and database name from
     * @return a new MongoDBSyncAuditStore instance
     */
    public static MongoDBSyncAuditStore from(MongoDBSyncTargetSystem mongoDBSyncTargetSystem) {
        return new MongoDBSyncAuditStore(mongoDBSyncTargetSystem.getClient(), mongoDBSyncTargetSystem.getDatabaseName());
    }

    public MongoDBSyncAuditStore withAuditRepositoryName(String auditRepositoryName) {
        this.auditRepositoryName = auditRepositoryName;
        return this;
    }

    public MongoDBSyncAuditStore withLockRepositoryName(String lockRepositoryName) {
        this.lockRepositoryName = lockRepositoryName;
        return this;
    }

    public MongoDBSyncAuditStore withReadConcern(ReadConcern readConcern) {
        this.readConcern = readConcern;
        return this;
    }

    public MongoDBSyncAuditStore withReadPreference(ReadPreference readPreference) {
        this.readPreference = readPreference;
        return this;
    }

    public MongoDBSyncAuditStore withWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
        return this;
    }

    public MongoDBSyncAuditStore withAutoCreate(boolean autoCreate) {
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
            persistence = new MongoDBSyncAuditPersistence(
                    communityConfiguration,
                    database,
                    auditRepositoryName,
                    readConcern,
                    readPreference,
                    writeConcern,
                    autoCreate
            );
            persistence.initialize(runnerId);
        }
        return persistence;
    }

    @Override
    public synchronized CommunityLockService getLockService() {
        if (lockService == null) {
            lockService = new MongoDBSyncLockService(
                    database,
                    lockRepositoryName,
                    readConcern,
                    readPreference,
                    writeConcern,
                    TimeService.getDefault()
            );
            lockService.initialize(autoCreate);

        }
        return lockService;
    }

    private void validate() {

        if (client == null) {
            throw new FlamingockException("The 'client' instance is required.");
        }

        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new FlamingockException("The 'databaseName' property is required.");
        }
        database = client.getDatabase(databaseName);

        if (auditRepositoryName == null || auditRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'auditRepositoryName' property is required.");
        }

        if (lockRepositoryName == null || lockRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'lockRepositoryName' property is required.");
        }

        if (auditRepositoryName.trim().equalsIgnoreCase(lockRepositoryName.trim())) {
            throw new FlamingockException("The 'auditRepositoryName' and 'lockRepositoryName' properties must not be the same.");
        }

        if (readConcern == null) {
            throw new FlamingockException("The 'readConcern' property is required.");
        }

        if (readPreference == null) {
            throw new FlamingockException("The 'readPreference' property is required.");
        }

        if (writeConcern == null) {
            throw new FlamingockException("The 'writeConcern' property is required.");
        }
    }
}
