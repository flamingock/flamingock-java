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
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.springframework.data.mongodb.core.MongoTemplate;


public class MongoDBSpringDataTargetSystem extends TransactionalTargetSystem<MongoDBSpringDataTargetSystem> {

    private final WriteConcern DEFAULT_WRITE_CONCERN = WriteConcern.MAJORITY.withJournal(true);
    private final ReadConcern DEFAULT_READ_CONCERN = ReadConcern.MAJORITY;
    private final ReadPreference DEFAULT_READ_PREFERENCE = ReadPreference.primary();

    private TargetSystemAuditMarker taskStatusRepository;

    private MongoDBSpringDataTxWrapper txWrapper;
    private MongoTemplate mongoTemplate;
    private WriteConcern writeConcern = DEFAULT_WRITE_CONCERN;
    private ReadConcern readConcern = DEFAULT_READ_CONCERN;
    private ReadPreference readPreference = DEFAULT_READ_PREFERENCE;
//    private boolean autoCreate = true;

    public MongoDBSpringDataTargetSystem(String id, MongoTemplate mongoTemplate) {
        super(id);
        this.mongoTemplate = mongoTemplate;
        targetSystemContext.addDependency(mongoTemplate);
    }

    public MongoDBSpringDataTargetSystem withReadConcern(ReadConcern readConcern) {
        this.readConcern = readConcern;
        targetSystemContext.addDependency(readConcern);
        return this;
    }

    public MongoDBSpringDataTargetSystem withReadPreference(ReadPreference readPreference) {
        this.readPreference = readPreference;
        targetSystemContext.addDependency(readPreference);
        return this;
    }

    public MongoDBSpringDataTargetSystem withWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
        targetSystemContext.addDependency(writeConcern);
        return this;
    }

//    public MongoDBSpringDataTargetSystem withAutoCreate(boolean autoCreate) {
//        this.autoCreate = autoCreate;
//        targetSystemContext.setProperty("autoCreate", autoCreate);
//        return this;
//    }

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
        this.validate();

        txWrapper = MongoDBSpringDataTxWrapper.builder()
                .mongoTemplate(mongoTemplate)
                .readConcern(readConcern)
                .readPreference(readPreference)
                .writeConcern(writeConcern)
                .build();

        //TODO create MongoDBSpringDataOnGoingTaskStatusRepository for cloud edition
        taskStatusRepository = new NoOpTargetSystemAuditMarker(this.getId());
    }

    private void validate() {
        if (mongoTemplate == null) {
            throw new FlamingockException("The 'mongoTemplate' instance is required.");
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
    protected MongoDBSpringDataTargetSystem getSelf() {
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
