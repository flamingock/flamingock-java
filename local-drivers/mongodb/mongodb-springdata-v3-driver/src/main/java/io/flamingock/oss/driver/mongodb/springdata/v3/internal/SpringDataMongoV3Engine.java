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

package io.flamingock.oss.driver.mongodb.springdata.v3.internal;

import com.mongodb.ReadConcern;
import com.mongodb.client.MongoCollection;
import io.flamingock.cloud.transaction.mongodb.sync.v4.cofig.ReadWriteConfiguration;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.system.LocalSystemModule;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.local.AbstractLocalEngine;
import io.flamingock.core.local.LocalAuditor;
import io.flamingock.core.local.LocalExecutionPlanner;
import io.flamingock.core.transaction.TransactionWrapper;
import io.flamingock.core.engine.audit.importer.ImporterModule;
import io.flamingock.importer.mongodb.sync.v4.MongoImporterReader;
import io.flamingock.oss.driver.mongodb.springdata.v3.config.SpringDataMongoV3Configuration;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

public class SpringDataMongoV3Engine extends AbstractLocalEngine {

    private final MongoTemplate mongoTemplate;
    private final SpringDataMongoV3Configuration driverConfiguration;
    private final CoreConfigurable coreConfiguration;
    private SpringDataMongoV3Auditor auditor;
    private LocalExecutionPlanner executionPlanner;
    private TransactionWrapper transactionWrapper;
    private LocalSystemModule mongockImporter = null;


    public SpringDataMongoV3Engine(MongoTemplate mongoTemplate,
                                   CoreConfigurable coreConfiguration,
                                   LocalConfigurable localConfiguration,
                                   SpringDataMongoV3Configuration driverConfiguration) {
        super(localConfiguration);
        this.mongoTemplate = mongoTemplate;
        this.driverConfiguration = driverConfiguration;
        this.coreConfiguration = coreConfiguration;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        ReadWriteConfiguration readWriteConfiguration = new ReadWriteConfiguration(driverConfiguration.getBuiltMongoDBWriteConcern(),
                new ReadConcern(driverConfiguration.getReadConcern()),
                driverConfiguration.getReadPreference().getValue());
        transactionWrapper = localConfiguration.isTransactionDisabled()
                ? null
                : new SpringDataMongoV3TransactionWrapper(mongoTemplate, readWriteConfiguration);

        auditor = new SpringDataMongoV3Auditor(
                mongoTemplate,
                driverConfiguration.getMigrationRepositoryName(),
                readWriteConfiguration);
        auditor.initialize(driverConfiguration.isIndexCreation());
        SpringDataMongoV3LockService lockService = new SpringDataMongoV3LockService(
                mongoTemplate.getDb(),
                driverConfiguration.getLockRepositoryName(),
                readWriteConfiguration);
        lockService.initialize(driverConfiguration.isIndexCreation());
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
        //Mongock importer
        if (coreConfiguration.isMongockImporterEnabled()) {
            MongoCollection<Document> legacyCollectionToImportFrom = mongoTemplate
                    .getDb()
                    .getCollection(coreConfiguration.getLegacyMongockChangelogSource());
            MongoImporterReader importerReader = new MongoImporterReader(legacyCollectionToImportFrom);
            mongockImporter = new ImporterModule(importerReader);
        }
    }

    @Override
    public LocalAuditor getAuditor() {
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
    public Optional<LocalSystemModule> getMongockLegacyImporterModule() {
        return Optional.ofNullable(mongockImporter);
    }
}
