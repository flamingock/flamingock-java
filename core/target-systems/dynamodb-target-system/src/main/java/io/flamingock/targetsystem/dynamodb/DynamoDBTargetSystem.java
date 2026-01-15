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

import io.flamingock.importer.mongock.dynamodb.MongockImporterDynamoDB;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.audit.AuditReaderType;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.external.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Objects;
import java.util.Optional;

import static io.flamingock.internal.common.core.audit.AuditReaderType.MONGOCK;
import static io.flamingock.internal.common.core.metadata.Constants.DEFAULT_MONGOCK_ORIGIN;

public class DynamoDBTargetSystem extends TransactionalTargetSystem<DynamoDBTargetSystem> implements DynamoDBExternalSystem {

    private DynamoDbClient client;

    private DynamoDBTxWrapper txWrapper;

    public DynamoDBTargetSystem(String id, DynamoDbClient dynamoDBClient) {
        super(id);
        this.client = dynamoDBClient;
    }

    @Override
    public DynamoDbClient getClient() {
        return client;
    }

    public TransactionManager<TransactWriteItemsEnhancedRequest.Builder> getTxManager() {
        return txWrapper.getTxManager();
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        this.validate();
        targetSystemContext.addDependency(client);

        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class)
                .orElse(FlamingockEdition.CLOUD);

        TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager = new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder);
        txWrapper = new DynamoDBTxWrapper(client, txManager);

        //TODO: inject marker repository based on edition(baseContext.getDependencyValue(FlamingockEdition.class))
        markerRepository = new NoOpTargetSystemAuditMarker(this.getId());
    }

    private void validate() {
        if (client == null) {
            throw new FlamingockException("The 'DynamoDbClient' instance is required.");
        }
    }

    @Override
    protected DynamoDBTargetSystem getSelf() {
        return this;
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }


    @Override
    public Optional<AuditHistoryReader> getAuditAuditReader(AuditReaderType type) {
        if (Objects.requireNonNull(type) == MONGOCK) {
            return Optional.of(new MongockImporterDynamoDB(client, DEFAULT_MONGOCK_ORIGIN));
        } else {
            return Optional.empty();
        }
    }
}
