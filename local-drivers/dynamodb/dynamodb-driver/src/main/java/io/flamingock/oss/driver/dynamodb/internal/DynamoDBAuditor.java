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
import io.flamingock.core.community.TransactionManager;
import io.flamingock.core.engine.audit.writer.AuditEntry;
import io.flamingock.core.engine.audit.writer.AuditStageStatus;
import io.flamingock.core.community.LocalAuditor;
import io.flamingock.oss.driver.dynamodb.internal.entities.AuditEntryEntity;
import io.flamingock.commons.utils.DynamoDBConstants;
import io.flamingock.commons.utils.DynamoDBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class DynamoDBAuditor implements LocalAuditor {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBAuditor.class);

    private final DynamoDBUtil dynamoDBUtil;
    protected DynamoDbTable<AuditEntryEntity> table;
    protected final TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager;

    protected DynamoDBAuditor(DynamoDbClient client,
                              TransactionManager<TransactWriteItemsEnhancedRequest.Builder> transactionManager) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
        this.transactionManager = transactionManager;
    }

    protected void initialize(Boolean autoCreate, String tableName, long readCapacityUnits, long writeCapacityUnits) {
        if (autoCreate) {
            dynamoDBUtil.createTable(
                    dynamoDBUtil.getAttributeDefinitions(DynamoDBConstants.AUDIT_LOG_PK, null),
                    dynamoDBUtil.getKeySchemas(DynamoDBConstants.AUDIT_LOG_PK, null),
                    dynamoDBUtil.getProvisionedThroughput(readCapacityUnits, writeCapacityUnits),
                    tableName,
                    emptyList(),
                    emptyList()
            );
        }
        table = dynamoDBUtil.getEnhancedClient().table(tableName, TableSchema.fromBean(AuditEntryEntity.class));
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        AuditEntryEntity entity = new AuditEntryEntity(auditEntry);
        logger.debug("Saving audit entry with key {}", entity.getPartitionKey());

        TransactWriteItemsEnhancedRequest.Builder transactionBuilder = transactionManager
                .getSession(auditEntry.getTaskId())
                .orElse(null);

        if(transactionBuilder != null) {
            transactionBuilder.addPutItem(table, entity);
        } else {
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
