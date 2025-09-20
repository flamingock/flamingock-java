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
package io.flamingock.targetystem.mongodb.sync;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;

public class MongoDBSyncTargetSystem extends TransactionalTargetSystem<MongoDBSyncTargetSystem> {
    private static final String FLAMINGOCK_ON_GOING_TASKS = "flamingockOnGoingTasks";

    private final WriteConcern DEFAULT_WRITE_CONCERN = WriteConcern.MAJORITY.withJournal(true);
    private final ReadConcern DEFAULT_READ_CONCERN = ReadConcern.MAJORITY;
    private final ReadPreference DEFAULT_READ_PREFERENCE = ReadPreference.primary();

    private final MongoClient mongoClient;
    private final String databaseName;
    private MongoDatabase database;
    private WriteConcern writeConcern = DEFAULT_WRITE_CONCERN;
    private ReadConcern readConcern = DEFAULT_READ_CONCERN;
    private ReadPreference readPreference = DEFAULT_READ_PREFERENCE;
    private boolean autoCreate = true;

    private TargetSystemAuditMarker taskStatusRepository;
    private MongoDBSyncTxWrapper txWrapper;

    public MongoDBSyncTargetSystem(String id, MongoClient mongoClient, String databaseName) {
        super(id);
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
        this.validate();
        this.database = mongoClient.getDatabase(databaseName);
        targetSystemContext.addDependency(mongoClient);
        targetSystemContext.addDependency(database);
        targetSystemContext.setProperty("autoCreate", true);
    }

    public MongoDBSyncTargetSystem withReadConcern(ReadConcern readConcern) {
        this.readConcern = readConcern;
        targetSystemContext.addDependency(readConcern);
        return this;
    }

    public MongoDBSyncTargetSystem withReadPreference(ReadPreference readPreference) {
        this.readPreference = readPreference;
        targetSystemContext.addDependency(readPreference);
        return this;
    }

    public MongoDBSyncTargetSystem withWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
        targetSystemContext.addDependency(writeConcern);
        return this;
    }

    public MongoDBSyncTargetSystem withAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    public TransactionManager<ClientSession> getTxManager() {
        return txWrapper.getTxManager();
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    public ReadConcern getReadConcern() {
        return readConcern;
    }

    public ReadPreference getReadPreference() {
        return readPreference;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        this.validate();
        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class)
                .orElse(FlamingockEdition.CLOUD);

        TransactionManager<ClientSession> txManager = new TransactionManager<>(mongoClient::startSession);
        txWrapper = new MongoDBSyncTxWrapper(txManager);

        taskStatusRepository = edition == FlamingockEdition.COMMUNITY
                ? new NoOpTargetSystemAuditMarker(this.getId())
                : MongoDBSyncTargetSystemAuditMarker.builder(database, txManager)
                .setCollectionName(FLAMINGOCK_ON_GOING_TASKS)
                .withAutoCreate(autoCreate)
                .withReadConcern(readConcern)
                .withReadPreference(readPreference)
                .withWriteConcern(writeConcern)
                .build();
    }

    private void validate() {
        if (mongoClient == null) {
            throw new FlamingockException("The 'mongoClient' instance is required.");
        }
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new FlamingockException("The 'databaseName' property is required.");
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

    @Override
    protected MongoDBSyncTargetSystem getSelf() {
        return this;
    }

    @Override
    public TargetSystemAuditMarker getOnGoingTaskStatusRepository() {
        return taskStatusRepository;
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }
}
