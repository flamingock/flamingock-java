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
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.NoOpOnGoingTaskStatusRepository;
import io.flamingock.internal.core.targets.OngoingTaskStatusRepository;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.function.Function;


public class MongoSyncTargetSystem extends TransactionalTargetSystem<MongoSyncTargetSystem> {
    private static final String FLAMINGOCK_ON_GOING_TASKS = "flamingockOnGoingTasks";

    private final WriteConcern DEFAULT_WRITE_CONCERN = WriteConcern.MAJORITY.withJournal(true);
    private final ReadConcern DEFAULT_READ_CONCERN = ReadConcern.MAJORITY;
    private final ReadPreference DEFAULT_READ_PREFERENCE = ReadPreference.primary();


    private OngoingTaskStatusRepository taskStatusRepository;

    private MongoSyncTxWrapper txWrapper;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private WriteConcern writeConcern = null;
    private ReadConcern readConcern = null;
    private ReadPreference readPreference = null;

    public MongoSyncTargetSystem(String id) {
        super(id);
    }

    public MongoSyncTargetSystem withMongoClient(MongoClient mongoClient) {
        targetSystemContext.addDependency(mongoClient);
        return this;
    }

    public MongoSyncTargetSystem withDatabase(MongoDatabase database) {
        targetSystemContext.addDependency(database);
        return this;
    }

    public MongoSyncTargetSystem withReadConcern(ReadConcern readConcern) {
        targetSystemContext.addDependency(readConcern);
        return this;
    }

    public MongoSyncTargetSystem withReadPreference(ReadPreference readPreference) {
        targetSystemContext.addDependency(readPreference);
        return this;
    }

    public MongoSyncTargetSystem withWriteConcern(WriteConcern writeConcern) {
        targetSystemContext.addDependency(writeConcern);
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
        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class)
                .orElse(FlamingockEdition.CLOUD);

        mongoClient = targetSystemContext.getDependencyValue(MongoClient.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(MongoClient.class));

        database = targetSystemContext.getDependencyValue(MongoDatabase.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(MongoDatabase.class));

        readConcern = targetSystemContext.getDependencyValue(ReadConcern.class)
                .orElseGet(() -> baseContext.getDependencyValue(ReadConcern.class)
                        .orElse(DEFAULT_READ_CONCERN));

        readPreference = targetSystemContext.getDependencyValue(ReadPreference.class)
                .orElseGet(() -> baseContext.getDependencyValue(ReadPreference.class)
                        .orElse(DEFAULT_READ_PREFERENCE));

        writeConcern = targetSystemContext.getDependencyValue(WriteConcern.class)
                .orElseGet(() -> baseContext.getDependencyValue(WriteConcern.class)
                        .orElseGet(() -> DEFAULT_WRITE_CONCERN));

        txWrapper = new MongoSyncTxWrapper(mongoClient);


        taskStatusRepository = edition == FlamingockEdition.COMMUNITY
                ? new NoOpOnGoingTaskStatusRepository(this.getId())
                : MongoSyncOnGoingTaskStatusRepository.builder(database)
                .setCollectionName(FLAMINGOCK_ON_GOING_TASKS)
                .withAutoCreate(autoCreate)
                .withReadConcern(readConcern)
                .withReadPreference(readPreference)
                .withWriteConcern(writeConcern)
                .build();
    }

    @Override
    protected MongoSyncTargetSystem getSelf() {
        return this;
    }

    @Override
    public OngoingTaskStatusRepository getOnGoingTaskStatusRepository() {
        return taskStatusRepository;
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }

    @Override
    public boolean isSameTxResourceAs(TransactionalTargetSystem<?> other) {
        if(!(other instanceof MongoSyncTargetSystem)) {
            return false;
        }
        MongoClient otherClient = ((MongoSyncTargetSystem) other).mongoClient;
        if(otherClient == null) {
            return false;
        }
        return otherClient.equals(this.mongoClient);
    }

}
