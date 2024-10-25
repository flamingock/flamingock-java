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

import io.flamingock.commons.utils.Result;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.engine.audit.writer.AuditStageStatus;
import io.flamingock.core.engine.local.Auditor;
import io.flamingock.oss.driver.dynamodb.internal.entities.AuditEntryEntity;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoClients;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoDBConstants;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoDBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class DynamoDBAuditor implements Auditor {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBAuditor.class);

    protected final DynamoClients client;
    protected final DynamoDBTransactionWrapper transactionWrapper;
    private final DynamoDBUtil dynamoDBUtil = new DynamoDBUtil();
    protected DynamoDbTable<AuditEntryEntity> table;

    protected DynamoDBAuditor(DynamoClients client, DynamoDBTransactionWrapper transactionWrapper) {
        this.client = client;
        this.transactionWrapper = transactionWrapper;
    }

    protected void initialize(Boolean indexCreation) {
        if (indexCreation) {
            dynamoDBUtil.createTable(
                    client.getDynamoDbClient(),
                    dynamoDBUtil.getAttributeDefinitions(DynamoDBConstants.AUDIT_LOG_PK, null),
                    dynamoDBUtil.getKeySchemas(DynamoDBConstants.AUDIT_LOG_PK, null),
                    dynamoDBUtil.getProvisionedThroughput(5L, 5L),
                    DynamoDBConstants.AUDIT_LOG_TABLE_NAME,
                    emptyList(),
                    emptyList()
            );
        }
        table = client.getEnhancedClient().table(DynamoDBConstants.AUDIT_LOG_TABLE_NAME, TableSchema.fromBean(AuditEntryEntity.class));
    }

    /**
     * Only for testing
     */
    public void deleteAll() {
        table.deleteTable();
        initialize(true);
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        AuditEntryEntity entity = new AuditEntryEntity(auditEntry);
        logger.debug("Saving audit entry with key {}", entity.getPartitionKey());

        if (transactionWrapper == null) {
            try {
                table.putItem(
                        PutItemEnhancedRequest.builder(AuditEntryEntity.class)
                                .item(entity)
                                .build()
                );
            } catch (ConditionalCheckFailedException ex) {
                logger.warn("Error saving audit entry with key {}", entity.getPartitionKey(), ex);
                throw ex;
            }
        } else {
            transactionWrapper.getWriteRequestBuilder()
                    .addPutItem(table, entity);
        }

        return Result.OK();
    }

    @Override
    public AuditStageStatus getAuditStageStatus() {
        AuditStageStatus.EntryBuilder response = AuditStageStatus.entryBuilder();
        table
                .scan(ScanEnhancedRequest.builder()
                        .consistentRead(true)
                        .build()
                )
                .items()
                .stream()
                .map(AuditEntryEntity::toAuditEntry)
                .collect(Collectors.toList())
                .forEach(response::addEntry);
        return response.build();
    }

}
