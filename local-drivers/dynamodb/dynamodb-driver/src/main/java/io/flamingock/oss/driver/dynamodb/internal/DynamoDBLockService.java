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
import io.flamingock.core.driver.lock.LocalLockService;
import io.flamingock.core.driver.lock.LockEntry;
import io.flamingock.core.engine.lock.LockAcquisition;
import io.flamingock.core.engine.lock.LockKey;
import io.flamingock.core.engine.lock.LockServiceException;
import io.flamingock.core.engine.lock.LockStatus;
import io.flamingock.oss.driver.dynamodb.internal.entities.LockEntryEntity;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoClients;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoDBConstants;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoDBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static java.util.Collections.emptyList;

public class DynamoDBLockService implements LocalLockService {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBLockService.class);

    protected final DynamoClients client;

    private final TimeService timeService;
    private final DynamoDBUtil dynamoDBUtil = new DynamoDBUtil();
    protected DynamoDbTable<LockEntryEntity> table;

    protected DynamoDBLockService(DynamoClients client, TimeService timeService) {
        this.client = client;
        this.timeService = timeService;
    }

    protected void initialize(Boolean indexCreation) {
        if (indexCreation) {
            dynamoDBUtil.createTable(
                    client.getDynamoDbClient(),
                    dynamoDBUtil.getAttributeDefinitions(DynamoDBConstants.LOCK_PK, null),
                    dynamoDBUtil.getKeySchemas(DynamoDBConstants.LOCK_PK, null),
                    dynamoDBUtil.getProvisionedThroughput(5L, 5L),
                    DynamoDBConstants.LOCK_TABLE_NAME,
                    emptyList(),
                    emptyList()
            );
        }
        table = client.getEnhancedClient().table(DynamoDBConstants.LOCK_TABLE_NAME, TableSchema.fromBean(LockEntryEntity.class));
    }

    @Override
    public LockAcquisition upsert(LockKey key, RunnerId owner, long leaseMillis) {
        LockEntry newLock = new LockEntry(key.toString(), LockStatus.LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        table.putItem(
                PutItemEnhancedRequest.builder(LockEntryEntity.class)
                        .item(new LockEntryEntity(newLock))
                        .conditionExpression(Expression.builder()
                                .expression("attribute_not_exists(partitionKey) OR " +
                                        "(lockOwner = :ownerVal AND expiresAt > :currentTime) OR " +
                                        "(expiresAt < :currentTime)")
                                .putExpressionValue(":ownerVal", AttributeValue.builder().s(newLock.getOwner()).build())
                                .putExpressionValue(":currentTime", AttributeValue.builder().n(String.valueOf(Timestamp.valueOf(LocalDateTime.now()).getTime())).build())
                                .build())
                        .build()
        );
        return new LockAcquisition(owner, leaseMillis);
    }

    @Override
    public LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException {
        LockEntry updatedLock = new LockEntry(key.toString(), LockStatus.LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        table.updateItem(
                UpdateItemEnhancedRequest.builder(LockEntryEntity.class)
                        .item(new LockEntryEntity(updatedLock))
                        .conditionExpression(Expression.builder()
                                .expression("attribute_exists(partitionKey) AND lockOwner = :ownerVal AND expiresAt > :currentTime")
                                .putExpressionValue(":ownerVal", AttributeValue.builder().s(updatedLock.getOwner()).build())
                                .putExpressionValue(":currentTime", AttributeValue.builder().n(String.valueOf(Timestamp.valueOf(LocalDateTime.now()).getTime())).build())
                                .build())
                        .build()
        );
        return new LockAcquisition(owner, leaseMillis);
    }

    @Override
    public LockAcquisition getLock(LockKey lockKey) {
        LockEntryEntity existingLockEntity = table.getItem(
                Key.builder()
                        .partitionValue(lockKey.toString())
                        .build()
        );
        if (existingLockEntity != null) {
            return existingLockEntity.getlockAcquisition();
        } else {
            logger.debug("Lock for key {} was not found.", lockKey);
            return null;
        }
    }

    @Override
    public void releaseLock(LockKey lockKey, RunnerId owner) {
        LockEntryEntity existingLockEntity = table.getItem(
                Key.builder()
                        .partitionValue(lockKey.toString())
                        .build()
        );
        if (existingLockEntity != null) {
            LockEntry existingLock = existingLockEntity.toLockEntry();
            if (owner.equals(RunnerId.fromString(existingLock.getOwner()))) {
                logger.debug("Lock for key {} belongs to us, so removing.", lockKey);
                table.deleteItem(
                        Key.builder()
                                .partitionValue(lockKey.toString())
                                .build()
                );
            } else {
                logger.debug("Lock for key {} belongs to other owner, can not delete.", existingLock.getKey());
            }
        } else {
            logger.debug("Lock for key {} is not found, nothing to do", lockKey);
        }
    }
}
