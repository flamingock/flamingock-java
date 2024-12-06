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

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.engine.local.LocalConnectionEngine;
import io.flamingock.core.engine.local.Auditor;
import io.flamingock.community.internal.LocalExecutionPlanner;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.transaction.TransactionWrapper;
import io.flamingock.commons.utils.TimeService;
import io.flamingock.community.internal.TransactionManager;
import io.flamingock.oss.driver.mongodb.sync.v4.MongoDBSync4Configuration;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.mongock.MongockImporterModule;
import org.bson.Document;

import java.util.Optional;

public class MongoSync4Engine implements LocalConnectionEngine {

    private final MongoDatabase database;
    private final MongoClient mongoClient;
    private final LocalConfigurable localConfiguration;

    private MongoSync4Auditor auditor;
    private LocalExecutionPlanner executionPlanner;
    private TransactionWrapper transactionWrapper;
    private MongockImporterModule mongockImporter = null;
    private final MongoDBSync4Configuration driverConfiguration;
    private final CoreConfigurable coreConfiguration;


    public MongoSync4Engine(MongoClient mongoClient,
                            String databaseName,
                            CoreConfigurable coreConfiguration,
                            LocalConfigurable localConfiguration,
                            MongoDBSync4Configuration driverConfiguration) {
        this.mongoClient = mongoClient;
        this.database = mongoClient.getDatabase(databaseName);
        this.driverConfiguration = driverConfiguration;
        this.coreConfiguration = coreConfiguration;
        this.localConfiguration = localConfiguration;
    }

    @Override
    public void initialize(RunnerId runnerId) {
        TransactionManager<ClientSession> sessionManager = new TransactionManager<>(mongoClient::startSession);
        transactionWrapper = coreConfiguration.getTransactionEnabled() ? new MongoSync4TransactionWrapper(sessionManager) : null;
        //Auditor
        auditor = new MongoSync4Auditor(
                database,
                driverConfiguration.getMigrationRepositoryName(),
                driverConfiguration.getReadWriteConfiguration(),
                sessionManager);
        auditor.initialize(driverConfiguration.isIndexCreation());
        //Lock
        MongoSync4LockService lockService = new MongoSync4LockService(
                database,
                driverConfiguration.getLockRepositoryName(),
                driverConfiguration.getReadWriteConfiguration(),
                TimeService.getDefault());
        lockService.initialize(driverConfiguration.isIndexCreation());
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
        //Mongock importer
        if(coreConfiguration.isMongockImporterEnabled()) {
            MongoCollection<Document> collection = database.getCollection(coreConfiguration.getMongockImporterSource());
            mongockImporter = new MongockImporterModule(collection, auditor);

        }
    }

    @Override
    public Auditor getAuditor() {
        return auditor;
    }

    @Override
    public LocalExecutionPlanner getExecutionPlanner() {
        return executionPlanner;
    }


    @Override
    public Optional<TransactionWrapper> getTransactionWrapper() {
        return Optional.ofNullable(transactionWrapper);
    }

    @Override
    public Optional<MongockImporterModule> getMongockLegacyImporterModule() {
        return Optional.ofNullable(mongockImporter);
    }
}
