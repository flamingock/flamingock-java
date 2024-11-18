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

package io.flamingock.oss.driver.dynamodb.internal;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.TimeService;
import io.flamingock.community.internal.LocalExecutionPlanner;
import io.flamingock.community.internal.TransactionManager;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.engine.local.LocalConnectionEngine;
import io.flamingock.core.transaction.TransactionWrapper;
import io.flamingock.oss.driver.dynamodb.DynamoDBConfiguration;
import io.flamingock.oss.driver.dynamodb.internal.entities.AuditEntryEntity;
import io.flamingock.oss.driver.dynamodb.internal.mongock.ChangeEntryDynamoDB;
import io.flamingock.oss.driver.dynamodb.internal.mongock.MongockImporterModule;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoClients;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.util.Optional;
import java.util.function.Supplier;

public class DynamoDBEngine implements LocalConnectionEngine {

    private final DynamoClients client;
    private final LocalConfigurable localConfiguration;
    private final DynamoDBConfiguration driverConfiguration;
    private final CoreConfigurable coreConfiguration;
    private DynamoDBAuditor auditor;
    private LocalExecutionPlanner executionPlanner;
    private TransactionWrapper transactionWrapper;
    private MongockImporterModule mongockImporter = null;


    public DynamoDBEngine(DynamoClients client,
                          CoreConfigurable coreConfiguration,
                          LocalConfigurable localConfiguration,
                          DynamoDBConfiguration driverConfiguration) {
        this.client = client;
        this.driverConfiguration = driverConfiguration;
        this.coreConfiguration = coreConfiguration;
        this.localConfiguration = localConfiguration;
    }

    @Override
    public void initialize(RunnerId runnerId) {
        TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager = new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder);
        transactionWrapper = coreConfiguration.getTransactionEnabled() ? new DynamoDBTransactionWrapper(client, transactionManager) : null;
        auditor = new DynamoDBAuditor(client, transactionManager);
        auditor.initialize(driverConfiguration.isIndexCreation());
        DynamoDBLockService lockService = new DynamoDBLockService(client, TimeService.getDefault());
        lockService.initialize(driverConfiguration.isIndexCreation());
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
        //Mongock importer
        if(coreConfiguration.getMongockImporterConfiguration().isEnabled()) {
            DynamoDbTable<ChangeEntryDynamoDB> sourceTable = client.getEnhancedClient()
                    .table(coreConfiguration.getMongockImporterConfiguration().getSourceName(), TableSchema.fromBean(ChangeEntryDynamoDB.class));
            mongockImporter = new MongockImporterModule(sourceTable, auditor);
        }
    }

    @Override
    public DynamoDBAuditor getAuditor() {
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
