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
package io.flamingock.cloud.transaction.mongodb.sync;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.cloud.transaction.OngoingTaskStatusRepository;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.Optional;


public class MongoSyncTargetSystem extends TransactionalTargetSystem<MongoSyncTargetSystem> {
    private static final String FLAMINGOCK_ON_GOING_TASKS = "flamingockOnGoingTasks";

    private MongoSyncOnGoingTaskStatusRepository taskStatusRepository;

    private MongoSyncTxWrapper txWrapper;

    public MongoSyncTargetSystem(String id) {
        super(id);
    }

    public MongoSyncTargetSystem withMongoClient(MongoClient mongoClient) {
        context.addDependency(mongoClient);
        return this;
    }

    public MongoSyncTargetSystem withDatabase(MongoDatabase database) {
        context.addDependency(database);
        return this;
    }

    public MongoSyncTargetSystem withReadConcern(ReadConcern readConcern) {
        context.addDependency(readConcern);
        return this;
    }

    public MongoSyncTargetSystem withReadPreference(ReadPreference readPreference) {
        context.addDependency(readPreference);
        return this;
    }

    public MongoSyncTargetSystem withWriteConcern(WriteConcern writeConcern) {
        context.addDependency(writeConcern);
        return this;
    }

    @Override
    public void initialize(ContextResolver baseContext) {

        MongoClient mongoClient = context.getDependencyValue(MongoClient.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(MongoClient.class));

        MongoDatabase database = context.getDependencyValue(MongoDatabase.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(MongoDatabase.class));

        ReadConcern readConcern = context.getDependencyValue(ReadConcern.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(ReadConcern.class));

        ReadPreference readPreference = context.getDependencyValue(ReadPreference.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(ReadPreference.class));

        WriteConcern writeConcern = context.getDependencyValue(WriteConcern.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(WriteConcern.class));

        txWrapper = new MongoSyncTxWrapper(mongoClient);

        taskStatusRepository = MongoSyncOnGoingTaskStatusRepository.builder(database)
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
    public Optional<OngoingTaskStatusRepository> getOnGoingTaskStatusRepository() {
        return Optional.ofNullable(taskStatusRepository);
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }

}
