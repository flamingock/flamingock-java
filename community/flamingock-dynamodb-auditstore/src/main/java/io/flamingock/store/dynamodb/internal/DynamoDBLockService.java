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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.externalsystem.dynamodb.api.DynamoDBExternalSystem;
import io.flamingock.internal.util.dynamodb.entities.LockEntryEntity;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockEntry;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.external.store.lock.LockServiceException;
import io.flamingock.internal.core.external.store.lock.LockStatus;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.dynamodb.DynamoDBConstants;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static java.util.Collections.emptyList;

public class DynamoDBLockService implements CommunityLockService {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("DynamoLockService");

    private final DynamoDBUtil dynamoDBUtil;

    private final TimeService timeService;
    protected DynamoDbTable<LockEntryEntity> table;

    public DynamoDBLockService(DynamoDbClient client,
                                  TimeService timeService) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
        this.timeService = timeService;
    }


    public void initialize(Boolean autoCreate, String tableName, long readCapacityUnits, long writeCapacityUnits) {
        if (autoCreate) {
            dynamoDBUtil.createTable(
                    dynamoDBUtil.getAttributeDefinitions(DynamoDBConstants.LOCK_PK, null),
                    dynamoDBUtil.getKeySchemas(DynamoDBConstants.LOCK_PK, null),
                    dynamoDBUtil.getProvisionedThroughput(readCapacityUnits, writeCapacityUnits),
                    tableName,
                    emptyList(),
                    emptyList()
            );
        }
        table = dynamoDBUtil.getEnhancedClient().table(tableName, TableSchema.fromBean(LockEntryEntity.class));
    }

    @Override
    public LockAcquisition upsert(LockKey key, RunnerId owner, long leaseMillis) {
        CommunityLockEntry newLock = new CommunityLockEntry(key.toString(), LockStatus.LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
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
        CommunityLockEntry updatedLock = new CommunityLockEntry(key.toString(), LockStatus.LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
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
    public LockAcquisition getLockInfo(LockKey lockKey) {
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
        // Atomic conditional delete: removes the row only if the current owner matches the
        // caller. Replaces a previous get-then-delete pattern that had a TOCTOU race where
        // another runner could acquire between the read and the unconditional delete, and we
        // would then wipe their lock.
        try {
            table.deleteItem(software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest.builder()
                    .key(Key.builder().partitionValue(lockKey.toString()).build())
                    .conditionExpression(Expression.builder()
                            .expression("attribute_exists(partitionKey) AND lockOwner = :ownerVal")
                            .putExpressionValue(":ownerVal", AttributeValue.builder().s(owner.toString()).build())
                            .build())
                    .build());
            logger.debug("Lock for key {} removed (conditional on owner)", lockKey);
        } catch (software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException ex) {
            // Either the lock no longer exists, or it belongs to a different runner — both are
            // benign for release: do nothing.
            logger.debug("Lock for key {} not removed (no row, or owned by another runner)", lockKey);
        }
    }
}
