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
package io.flamingock.targetsystem.mongodb.reactive;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.externalsystem.mongodb.reactive.api.MongoDBReactiveExternalSystem;
import io.flamingock.importer.mongock.mongodb.reactive.MongockImporterMongoDBReactive;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.audit.AuditReaderType;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.external.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.reactive.util.PublisherSync;

import java.util.Objects;
import java.util.Optional;

import static io.flamingock.internal.common.core.audit.AuditReaderType.MONGOCK;
import static io.flamingock.internal.common.core.metadata.Constants.DEFAULT_MONGOCK_ORIGIN;
import static io.flamingock.internal.common.core.metadata.Constants.MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY;
import static io.flamingock.internal.core.builder.FlamingockEdition.COMMUNITY;

public class MongoDBReactiveTargetSystem extends TransactionalTargetSystem<MongoDBReactiveTargetSystem>
        implements MongoDBReactiveExternalSystem {

    private final MongoClient mongoClient;
    private final String databaseName;
    private MongoDatabase database;
    private WriteConcern writeConcern = WriteConcern.MAJORITY.withJournal(true);
    private ReadConcern readConcern = ReadConcern.MAJORITY;
    private ReadPreference readPreference = ReadPreference.primary();
    private boolean transactionsSupported = true;
    private MongoDBReactiveTxWrapper txWrapper;
    private ContextResolver baseContext;

    public MongoDBReactiveTargetSystem(String id, MongoClient mongoClient, String databaseName) {
        super(id);
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
    }

    public MongoDBReactiveTargetSystem withReadConcern(ReadConcern readConcern) {
        this.readConcern = readConcern;
        return this;
    }

    public MongoDBReactiveTargetSystem withReadPreference(ReadPreference readPreference) {
        this.readPreference = readPreference;
        return this;
    }

    public MongoDBReactiveTargetSystem withWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
        return this;
    }

    /**
     * Declares whether the concrete MongoDB deployment supports transactions.
     *
     * <p>Set this to {@code false} for standalone MongoDB. Flamingock will then use its normal
     * non-transactional execution path and will not create reactive transaction infrastructure.
     * The default is {@code true}; {@code false} explicitly disables transaction use.</p>
     *
     * @param transactionsSupported whether MongoDB transactions may be used
     * @return this target system
     */
    public MongoDBReactiveTargetSystem withTransactionsSupported(boolean transactionsSupported) {
        this.transactionsSupported = transactionsSupported;
        return this;
    }

    @Override
    public boolean supportsTransactions() {
        return transactionsSupported;
    }

    public MongoClient getClient() {
        return mongoClient;
    }

    @Override
    public MongoDatabase getMongoDatabase() {
        if (mongoClient == null || databaseName == null || databaseName.isEmpty()) {
            throw new FlamingockException("TargetSystem is not initialized. The 'mongoClient' instance and 'databaseName' property are required.");
        }
        return mongoClient.getDatabase(databaseName);
    }

    public String getDatabaseName() {
        return databaseName;
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

    public TransactionManager<ClientSession> getTxManager() {
        return getReactiveTxWrapper().getTxManager();
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        this.baseContext = baseContext;
        this.validate();
        targetSystemContext.addDependency(mongoClient);
        database = mongoClient.getDatabase(databaseName)
                .withReadConcern(readConcern)
                .withReadPreference(readPreference)
                .withWriteConcern(writeConcern);
        targetSystemContext.addDependency(database);

        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class).orElse(COMMUNITY);
        if (supportsTransactions()) {
            TransactionManager<ClientSession> txManager = getReactiveTxWrapper().getTxManager();
            auditMarker = edition == COMMUNITY
                    ? new NoOpTargetSystemAuditMarker(this.getId())
                    : MongoDBReactiveAuditMarker.builder(database, txManager).build();
        } else {
            auditMarker = new NoOpTargetSystemAuditMarker(this.getId());
        }
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
    protected MongoDBReactiveTargetSystem getSelf() {
        return this;
    }

    @Override
    public ExecutionWrapper getTxWrapper() {
        return getReactiveTxWrapper();
    }

    /**
     * Builds the transaction wrapper on first use rather than in {@link #initialize(ContextResolver)}.
     * <p>
     * It only needs the {@code mongoClient}, which is a constructor argument — the same reason
     * {@link #getMongoDatabase()} works before initialization. This matters because
     * {@code MongoDBReactiveAuditStore.from(targetSystem)} reuses only the MongoDB instance and does not
     * require the target system to be registered with the builder, so the store can be asked for a
     * transaction wrapper on a target system that is never initialized.
     */
    private synchronized MongoDBReactiveTxWrapper getReactiveTxWrapper() {
        if (!supportsTransactions()) {
            throw new FlamingockException("Transaction wrapper requested for a MongoDB target that does not support transactions.");
        }
        if (txWrapper == null) {
            if (mongoClient == null) {
                throw new FlamingockException("TargetSystem is not initialized. The 'mongoClient' instance is required.");
            }
            TransactionManager<ClientSession> txManager =
                    new TransactionManager<>(() -> PublisherSync.first(mongoClient.startSession()));
            txWrapper = new MongoDBReactiveTxWrapper(txManager);
        }
        return txWrapper;
    }

    @Override
    public Optional<AuditHistoryReader> getAuditAuditReader(AuditReaderType type) {
        if (Objects.requireNonNull(type) == MONGOCK) {
            return Optional.of(new MongockImporterMongoDBReactive(database, getMongockOrigin()));
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
