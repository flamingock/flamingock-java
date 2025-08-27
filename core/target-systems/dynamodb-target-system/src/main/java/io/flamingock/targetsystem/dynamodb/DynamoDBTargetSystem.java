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
package io.flamingock.targetsystem.dynamodb;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;


public class DynamoDBTargetSystem extends TransactionalTargetSystem<DynamoDBTargetSystem> {
    private static final String FLAMINGOCK_ON_GOING_TASKS = "flamingockOnGoingTasks";

    private TargetSystemAuditMarker taskStatusRepository;

    private DynamoDBTxWrapper txWrapper;
    private DynamoDbClient client;

    public DynamoDBTargetSystem(String id) {
        super(id);
    }

    public DynamoDBTargetSystem withDynamoDBClient(DynamoDbClient dynamoDbClient) {
        targetSystemContext.addDependency(dynamoDbClient);
        return this;
    }

    public DynamoDbClient getClient() {
        return client;
    }

    public TransactionManager<TransactWriteItemsEnhancedRequest.Builder> getTxManager() {
        return txWrapper.getTxManager();
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class)
                .orElse(FlamingockEdition.CLOUD);

        client = targetSystemContext.getDependencyValue(DynamoDbClient.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(DynamoDbClient.class));

        TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager = new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder);
        txWrapper = new DynamoDBTxWrapper(client, txManager);


        taskStatusRepository = edition == FlamingockEdition.COMMUNITY
                ? new NoOpTargetSystemAuditMarker(this.getId())
                : DynamoDbTargetSystemAuditMarker.builder(client, txManager)
                .setTableName(FLAMINGOCK_ON_GOING_TASKS)
                .withAutoCreate(autoCreate)
                .build();
    }

    @Override
    protected DynamoDBTargetSystem getSelf() {
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

    @Override
    public boolean isSameTxResourceAs(TransactionalTargetSystem<?> other) {
        if(!(other instanceof DynamoDBTargetSystem)) {
            return false;
        }
        DynamoDbClient otherClient = ((DynamoDBTargetSystem) other).client;
        if(otherClient == null) {
            return false;
        }
        return otherClient.equals(this.client);
    }

}
