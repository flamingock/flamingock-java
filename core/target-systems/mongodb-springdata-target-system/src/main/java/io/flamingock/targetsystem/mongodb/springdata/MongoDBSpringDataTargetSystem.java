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
import com.mongodb.client.MongoDatabase;
import io.flamingock.importer.mongock.mongodb.MongockImporterMongoDB;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.audit.AuditReaderType;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.targetystem.mongodb.api.MongoDBTargetSystem;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Objects;
import java.util.Optional;

import static io.flamingock.internal.common.core.audit.AuditReaderType.MONGOCK;
import static io.flamingock.internal.common.core.metadata.Constants.DEFAULT_MONGOCK_ORIGIN;


public class MongoDBSpringDataTargetSystem extends TransactionalTargetSystem<MongoDBSpringDataTargetSystem>
        implements MongoDBTargetSystem {

    private final MongoDBSpringDataDatabaseAccesor databaseAccesor;
    private final MongoTemplate mongoTemplate;
    private WriteConcern writeConcern = WriteConcern.MAJORITY.withJournal(true);
    private ReadConcern readConcern = ReadConcern.MAJORITY;
    private ReadPreference readPreference = ReadPreference.primary();

    private MongoDBSpringDataTxWrapper txWrapper;

    public MongoDBSpringDataTargetSystem(String id, MongoTemplate mongoTemplate) {
        super(id);
        this.mongoTemplate = mongoTemplate;
        this.databaseAccesor = new MongoDBSpringDataDatabaseAccesor(mongoTemplate);
    }

    public MongoDBSpringDataTargetSystem withReadConcern(ReadConcern readConcern) {
        this.readConcern = readConcern;
        return this;
    }

    public MongoDBSpringDataTargetSystem withReadPreference(ReadPreference readPreference) {
        this.readPreference = readPreference;
        return this;
    }

    public MongoDBSpringDataTargetSystem withWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
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
    public MongoDatabase getMongoDatabase() {
        return databaseAccesor.getDatabase();
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        this.validate();
        targetSystemContext.addDependency(mongoTemplate);

        txWrapper = MongoDBSpringDataTxWrapper.builder()
                .mongoTemplate(mongoTemplate)
                .readConcern(readConcern)
                .readPreference(readPreference)
                .writeConcern(writeConcern)
                .build();

        //TODO: inject marker repository based on edition(baseContext.getDependencyValue(FlamingockEdition.class))
        markerRepository = new NoOpTargetSystemAuditMarker(this.getId());
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
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }

    @Override
    public Optional<AuditHistoryReader> getAuditAuditReader(AuditReaderType type) {
        if (Objects.requireNonNull(type) == MONGOCK) {
            return Optional.of(new MongockImporterMongoDB(mongoTemplate.getDb(), DEFAULT_MONGOCK_ORIGIN));
        } else {
            return Optional.empty();
        }
    }
}
