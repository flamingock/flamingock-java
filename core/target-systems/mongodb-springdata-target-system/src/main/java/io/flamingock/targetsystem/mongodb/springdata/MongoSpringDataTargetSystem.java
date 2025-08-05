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
package io.flamingock.targetsystem.mongodb.springdata;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.targets.NoOpOnGoingTaskStatusRepository;
import io.flamingock.internal.core.targets.OngoingTaskStatusRepository;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;


public class MongoSpringDataTargetSystem extends TransactionalTargetSystem<MongoSpringDataTargetSystem> {

    private OngoingTaskStatusRepository taskStatusRepository;

    private MongoSpringDataTxWrapper txWrapper;

    public MongoSpringDataTargetSystem(String id) {
        super(id);
    }

    public MongoSpringDataTargetSystem withMongoTemplate(MongoTemplate mongoTemplate) {
        context.addDependency(mongoTemplate);
        return this;
    }
    
    public MongoSpringDataTargetSystem withReadConcern(ReadConcern readConcern) {
        context.addDependency(readConcern);
        return this;
    }

    public MongoSpringDataTargetSystem withReadPreference(ReadPreference readPreference) {
        context.addDependency(readPreference);
        return this;
    }

    public MongoSpringDataTargetSystem withWriteConcern(WriteConcern writeConcern) {
        context.addDependency(writeConcern);
        return this;
    }

    @Override
    public void initialize(ContextResolver baseContext) {

        MongoTemplate mongoTemplate = context.getDependencyValue(MongoTemplate.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(MongoTemplate.class));

        MongoDatabase database = context.getDependencyValue(MongoDatabase.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(MongoDatabase.class));

        ReadConcern readConcern = context.getDependencyValue(ReadConcern.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(ReadConcern.class));

        ReadPreference readPreference = context.getDependencyValue(ReadPreference.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(ReadPreference.class));

        WriteConcern writeConcern = context.getDependencyValue(WriteConcern.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(WriteConcern.class));

        txWrapper = MongoSpringDataTxWrapper.builder()
                .mongoTemplate(mongoTemplate)
                .readConcern(readConcern)
                .readPreference(readPreference)
                .writeConcern(writeConcern)
                .build();

        //TODO create MongoSpringDataOnGoingTaskStatusRepository for cloud edition
        taskStatusRepository = new NoOpOnGoingTaskStatusRepository(this.getId());
    }

    @Override
    protected MongoSpringDataTargetSystem getSelf() {
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

}
