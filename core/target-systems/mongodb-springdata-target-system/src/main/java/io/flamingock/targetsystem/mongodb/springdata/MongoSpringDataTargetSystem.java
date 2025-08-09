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
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.NoOpOnGoingTaskStatusRepository;
import io.flamingock.internal.core.targets.OngoingTaskStatusRepository;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.function.Function;


public class MongoSpringDataTargetSystem extends TransactionalTargetSystem<MongoSpringDataTargetSystem> {

    private final WriteConcern DEFAULT_WRITE_CONCERN = WriteConcern.MAJORITY.withJournal(true);
    private final ReadConcern DEFAULT_READ_CONCERN = ReadConcern.MAJORITY;
    private final ReadPreference DEFAULT_READ_PREFERENCE = ReadPreference.primary();

    private OngoingTaskStatusRepository taskStatusRepository;

    private MongoSpringDataTxWrapper txWrapper;
    private MongoTemplate mongoTemplate;
    private WriteConcern writeConcern = null;
    private ReadConcern readConcern = null;
    private ReadPreference readPreference = null;

    public MongoSpringDataTargetSystem(String id) {
        super(id);
    }

    public MongoSpringDataTargetSystem withMongoTemplate(MongoTemplate mongoTemplate) {
        targetSystemContext.addDependency(mongoTemplate);
        return this;
    }
    
    public MongoSpringDataTargetSystem withReadConcern(ReadConcern readConcern) {
        targetSystemContext.addDependency(readConcern);
        return this;
    }

    public MongoSpringDataTargetSystem withReadPreference(ReadPreference readPreference) {
        targetSystemContext.addDependency(readPreference);
        return this;
    }

    public MongoSpringDataTargetSystem withWriteConcern(WriteConcern writeConcern) {
        targetSystemContext.addDependency(writeConcern);
        return this;
    }

    public MongoTemplate getMongoTemplate() {
        return mongoTemplate;
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

        mongoTemplate = targetSystemContext.getDependencyValue(MongoTemplate.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(MongoTemplate.class));


        readConcern = targetSystemContext.getDependencyValue(ReadConcern.class)
                .orElseGet(() -> baseContext.getDependencyValue(ReadConcern.class)
                        .orElse(DEFAULT_READ_CONCERN));

        readPreference = targetSystemContext.getDependencyValue(ReadPreference.class)
                .orElseGet(() -> baseContext.getDependencyValue(ReadPreference.class)
                        .orElse(DEFAULT_READ_PREFERENCE));

        writeConcern = targetSystemContext.getDependencyValue(WriteConcern.class)
                .orElseGet(() -> baseContext.getDependencyValue(WriteConcern.class)
                        .orElseGet(() -> DEFAULT_WRITE_CONCERN));

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

    @Override
    public boolean isSameTxResourceAs(TransactionalTargetSystem<?> other) {
        if(!(other instanceof MongoSpringDataTargetSystem)) {
            return false;
        }
        MongoTemplate otherMongoTemplate = ((MongoSpringDataTargetSystem) other).mongoTemplate;
        if(otherMongoTemplate == null) {
            return false;
        }
        return otherMongoTemplate.equals(this.mongoTemplate);
    }

}
