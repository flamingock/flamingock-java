/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.targetsystem.mongodb.springdata.reactive;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.externalsystem.mongodb.reactive.api.MongoDBReactiveExternalSystem;
import io.flamingock.importer.mongock.mongodb.reactive.MongockImporterMongoDBReactive;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.audit.AuditReaderType;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.external.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.util.Objects;
import java.util.Optional;

import static io.flamingock.internal.common.core.audit.AuditReaderType.MONGOCK;
import static io.flamingock.internal.common.core.metadata.Constants.DEFAULT_MONGOCK_ORIGIN;
import static io.flamingock.internal.common.core.metadata.Constants.MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY;
import static io.flamingock.internal.core.builder.FlamingockEdition.COMMUNITY;

public class MongoDBSpringDataReactiveTargetSystem
        extends TransactionalTargetSystem<MongoDBSpringDataReactiveTargetSystem>
        implements MongoDBReactiveExternalSystem {

    private final ReactiveMongoTemplate mongoTemplate;
    private WriteConcern writeConcern = WriteConcern.MAJORITY.withJournal(true);
    private ReadConcern readConcern = ReadConcern.MAJORITY;
    private ReadPreference readPreference = ReadPreference.primary();

    private MongoDBSpringDataReactiveTxWrapper txWrapper;
    private ContextResolver baseContext;

    public MongoDBSpringDataReactiveTargetSystem(String id, ReactiveMongoTemplate mongoTemplate) {
        super(id);
        this.mongoTemplate = mongoTemplate;
    }

    public MongoDBSpringDataReactiveTargetSystem withReadConcern(ReadConcern readConcern) {
        this.readConcern = readConcern;
        return this;
    }

    public MongoDBSpringDataReactiveTargetSystem withReadPreference(ReadPreference readPreference) {
        this.readPreference = readPreference;
        return this;
    }

    public MongoDBSpringDataReactiveTargetSystem withWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
        return this;
    }

    public ReactiveMongoTemplate getMongoTemplate() {
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
        if (mongoTemplate == null) {
            throw new FlamingockException("TargetSystem is not initialized. The 'mongoTemplate' instance is required.");
        }
        return mongoTemplate.getMongoDatabaseFactory().getMongoDatabase().block();
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        this.baseContext = baseContext;
        this.validate();
        targetSystemContext.addDependency(mongoTemplate);

        txWrapper = MongoDBSpringDataReactiveTxWrapper.builder()
                .mongoTemplate(mongoTemplate)
                .readConcern(readConcern)
                .readPreference(readPreference)
                .writeConcern(writeConcern)
                .build();

        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class).orElse(COMMUNITY);
        auditMarker = edition == COMMUNITY
                ? new NoOpTargetSystemAuditMarker(this.getId())
                : MongoDBSpringDataReactiveAuditMarker.builder(mongoTemplate).build();
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
    protected MongoDBSpringDataReactiveTargetSystem getSelf() {
        return this;
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }

    @Override
    public Optional<AuditHistoryReader> getAuditAuditReader(AuditReaderType type) {
        if (Objects.requireNonNull(type) == MONGOCK) {
            return Optional.of(new MongockImporterMongoDBReactive(getMongoDatabase(), getMongockOrigin()));
        } else {
            return Optional.empty();
        }
    }

    private String getMongockOrigin() {
        return targetSystemContext.getProperty(MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY)
                .orElse(baseContext.getProperty(MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY)
                        .orElse(DEFAULT_MONGOCK_ORIGIN));
    }
}
