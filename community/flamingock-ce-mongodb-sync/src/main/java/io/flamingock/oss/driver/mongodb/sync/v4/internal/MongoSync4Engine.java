/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.oss.driver.mongodb.sync.v4.internal;

import com.mongodb.ReadConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.cloud.transaction.mongodb.sync.config.MongoDBSync4Configuration;
import io.flamingock.cloud.transaction.mongodb.sync.wrapper.MongoSync4TransactionWrapper;
import io.flamingock.commons.utils.id.RunnerId;
import io.flamingock.commons.utils.TimeService;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.AbstractLocalEngine;
import io.flamingock.internal.core.community.LocalAuditor;
import io.flamingock.internal.core.community.LocalExecutionPlanner;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.core.engine.audit.importer.ImporterModule;
import io.flamingock.internal.core.system.SystemModule;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.importer.mongodb.sync.v4.MongoImporterReader;
import org.bson.Document;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class MongoSync4Engine extends AbstractLocalEngine {

    private final MongoDatabase database;
    private final MongoClient mongoClient;
    private final MongoDBSync4Configuration driverConfiguration;
    private final CoreConfigurable coreConfiguration;
    private MongoSync4Auditor auditor;
    private LocalExecutionPlanner executionPlanner;
    private TransactionWrapper transactionWrapper;
    private SystemModule mongockImporter = null;


    public MongoSync4Engine(MongoClient mongoClient,
                            String databaseName,
                            CoreConfigurable coreConfiguration,
                            CommunityConfigurable localConfiguration,
                            MongoDBSync4Configuration driverConfiguration) {
        super(localConfiguration);
        this.mongoClient = mongoClient;
        this.database = mongoClient.getDatabase(databaseName);
        this.driverConfiguration = driverConfiguration;
        this.coreConfiguration = coreConfiguration;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        TransactionManager<ClientSession> sessionManager = new TransactionManager<>(mongoClient::startSession);
        transactionWrapper = localConfiguration.isTransactionDisabled()
                ? null
                : new MongoSync4TransactionWrapper(sessionManager);
        //Auditor
        auditor = new MongoSync4Auditor(
                database,
                driverConfiguration.getAuditRepositoryName(),
                new ReadWriteConfiguration(
                        driverConfiguration.getBuiltMongoDBWriteConcern(),
                        new ReadConcern(driverConfiguration.getReadConcern()),
                        driverConfiguration.getReadPreference().getValue()
                ),
                sessionManager);
        auditor.initialize(driverConfiguration.isAutoCreate());
        //Lock
        MongoSync4LockService lockService = new MongoSync4LockService(
                database,
                driverConfiguration.getLockRepositoryName(),
                new ReadWriteConfiguration(
                        driverConfiguration.getBuiltMongoDBWriteConcern(),
                        new ReadConcern(driverConfiguration.getReadConcern()),
                        driverConfiguration.getReadPreference().getValue()
                ),
                TimeService.getDefault());
        lockService.initialize(driverConfiguration.isAutoCreate());
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
        //Mongock importer
        if (coreConfiguration.isMongockImporterEnabled()) {
            MongoCollection<Document> legacyCollectionToImportFrom = database
                    .getCollection(coreConfiguration.getLegacyMongockChangelogSource());
            MongoImporterReader importerReader = new MongoImporterReader(legacyCollectionToImportFrom);
            mongockImporter = new ImporterModule(importerReader);
        }
    }

    @Override
    public LocalAuditor getAuditWriter() {
        return auditor;
    }

    @Override
    public LocalExecutionPlanner getExecutionPlanner() {
        return executionPlanner;
    }

    @Override
    public Set<Class<?>> getNonGuardedTypes() {
        return new HashSet<>(Collections.singletonList(ClientSession.class));
    }

    @Override
    public Optional<TransactionWrapper> getTransactionWrapper() {
        return Optional.ofNullable(transactionWrapper);
    }

    @Override
    protected Optional<SystemModule> getMongockLegacyImporterModule() {
        return Optional.ofNullable(mongockImporter);
    }
}
