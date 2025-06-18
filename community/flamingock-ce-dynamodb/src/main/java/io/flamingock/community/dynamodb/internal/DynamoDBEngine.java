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

package io.flamingock.community.dynamodb.internal;

import io.flamingock.cloud.transaction.dynamodb.wrapper.DynamoDBTransactionWrapper;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.AbstractLocalEngine;
import io.flamingock.internal.core.community.LocalExecutionPlanner;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.community.dynamodb.DynamoDBConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Optional;

public class DynamoDBEngine extends AbstractLocalEngine {

    private final DynamoDbClient client;
    private final DynamoDBUtil dynamoDBUtil;
    private final DynamoDBConfiguration driverConfiguration;
    private final CoreConfigurable coreConfiguration;
    private DynamoDBAuditor auditor;
    private LocalExecutionPlanner executionPlanner;
    private TransactionWrapper transactionWrapper;


    public DynamoDBEngine(DynamoDbClient client,
                          CoreConfigurable coreConfiguration,
                          CommunityConfigurable localConfiguration,
                          DynamoDBConfiguration driverConfiguration) {
        super(localConfiguration);
        this.client = client;
        this.dynamoDBUtil = new DynamoDBUtil(client);
        this.driverConfiguration = driverConfiguration;
        this.coreConfiguration = coreConfiguration;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager = new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder);
        transactionWrapper = localConfiguration.isTransactionDisabled() ? null : new DynamoDBTransactionWrapper(client, transactionManager);
        auditor = new DynamoDBAuditor(client, transactionManager);
        auditor.initialize(
                driverConfiguration.isAutoCreate(),
                driverConfiguration.getAuditRepositoryName(),
                driverConfiguration.getReadCapacityUnits(),
                driverConfiguration.getWriteCapacityUnits());
        DynamoDBLockService lockService = new DynamoDBLockService(client, TimeService.getDefault());
        lockService.initialize(
                driverConfiguration.isAutoCreate(),
                driverConfiguration.getLockRepositoryName(),
                driverConfiguration.getReadCapacityUnits(),
                driverConfiguration.getWriteCapacityUnits());
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
        //Mongock importer

    }

    @Override
    public DynamoDBAuditor getAuditWriter() {
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

}
