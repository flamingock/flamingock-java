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

package io.flamingock.oss.driver.mongodb.springdata.v4.internal;

import com.mongodb.client.MongoCollection;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.transaction.TransactionWrapper;
import io.flamingock.oss.driver.mongodb.springdata.v4.config.SpringDataMongoV4Configuration;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.mongock.MongockImporterModule;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.mongodb.ReadWriteConfiguration;
import io.flamingock.core.engine.local.LocalConnectionEngine;
import io.flamingock.core.engine.local.Auditor;
import io.flamingock.community.internal.LocalExecutionPlanner;

import java.util.Optional;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.ReadConcern;

public class SpringDataMongoV4Engine implements LocalConnectionEngine {

    private final MongoTemplate mongoTemplate;
    private final LocalConfigurable localConfiguration;

    private SpringDataMongoV4Auditor auditor;
    private LocalExecutionPlanner executionPlanner;
    private TransactionWrapper transactionWrapper;
    private MongockImporterModule mongockImporter = null;
    private final SpringDataMongoV4Configuration driverConfiguration;
    private final CoreConfigurable coreConfiguration;


    public SpringDataMongoV4Engine(MongoTemplate mongoTemplate,
                                   CoreConfigurable coreConfiguration,
                                   LocalConfigurable localConfiguration,
                            SpringDataMongoV4Configuration driverConfiguration) {
        this.mongoTemplate = mongoTemplate;
        this.driverConfiguration = driverConfiguration;
        this.coreConfiguration = coreConfiguration;
        this.localConfiguration = localConfiguration;
    }

    @Override
    public void initialize(RunnerId runnerId) {
        ReadWriteConfiguration readWriteConfiguration = new ReadWriteConfiguration(driverConfiguration.getBuiltMongoDBWriteConcern(),
                    new ReadConcern(driverConfiguration.getReadConcern()),
                    driverConfiguration.getReadPreference().getValue());
        transactionWrapper = coreConfiguration.getTransactionEnabled() ? new SpringDataMongoV4TransactionWrapper(mongoTemplate, readWriteConfiguration) : null;
        auditor = new SpringDataMongoV4Auditor(
                mongoTemplate,
                driverConfiguration.getMigrationRepositoryName(),
                readWriteConfiguration);
        auditor.initialize(driverConfiguration.isIndexCreation());
        SpringDataMongoV4LockService lockService = new SpringDataMongoV4LockService(
                mongoTemplate.getDb(),
                driverConfiguration.getLockRepositoryName(),
                readWriteConfiguration);
        lockService.initialize(driverConfiguration.isIndexCreation());
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
        //Mongock importer
        if(coreConfiguration.isMongockImporterEnabled()) {
            MongoCollection<Document> collection = mongoTemplate.getCollection(coreConfiguration.getMongockImporterSource());
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
