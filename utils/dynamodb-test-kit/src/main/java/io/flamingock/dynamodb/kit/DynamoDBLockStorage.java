/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.dynamodb.kit;

import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.util.id.RunnerId;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DynamoDB implementation of LockStorage for real database testing.
 * 
 * <p>This implementation provides lock storage functionality backed by actual DynamoDB
 * tables, enabling comprehensive testing of lock behavior in distributed scenarios.
 * It handles table lifecycle management and provides efficient lock operations.</p>
 */
public class DynamoDBLockStorage implements LockStorage {
    
    private final DynamoDbClient client;
    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName;
    private final boolean autoCleanup;
    private final boolean createTablesIfNotExist;
    private final Long readCapacityUnits;
    private final Long writeCapacityUnits;
    private final DynamoDbTable<DynamoDBLockEntity> table;
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();
    
    public DynamoDBLockStorage(DynamoDbClient client, String tableName,
                              boolean createTablesIfNotExist, boolean autoCleanup,
                              Long readCapacityUnits, Long writeCapacityUnits) {
        this.client = client;
        this.enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        this.tableName = tableName;
        this.createTablesIfNotExist = createTablesIfNotExist;
        this.autoCleanup = autoCleanup;
        this.readCapacityUnits = readCapacityUnits;
        this.writeCapacityUnits = writeCapacityUnits;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(DynamoDBLockEntity.class));
        
        if (createTablesIfNotExist) {
            createTableIfNotExists();
        }
    }
    
    @Override
    public void storeLock(LockKey key, LockAcquisition acquisition) {
        DynamoDBLockEntity entity = new DynamoDBLockEntity();
        entity.setLockKey(key.toString());
        entity.setOwner(acquisition.getOwner().toString());
        entity.setAcquiredForMillis(acquisition.getAcquiredForMillis());
        
        table.putItem(entity);
    }
    
    @Override
    public LockAcquisition getLock(LockKey key) {
        try {
            DynamoDBLockEntity entity = table.getItem(Key.builder()
                .partitionValue(key.toString())
                .build());
            
            if (entity != null) {
                return new LockAcquisition(
                    RunnerId.fromString(entity.getOwner()),
                    entity.getAcquiredForMillis()
                );
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public Map<LockKey, LockAcquisition> getAllLocks() {
        Map<LockKey, LockAcquisition> locks = new HashMap<>();
        
        table.scan(ScanEnhancedRequest.builder()
                .consistentRead(true)
                .build())
                .items()
                .forEach(entity -> {
                    LockKey lockKey = LockKey.fromString(entity.getLockKey());
                    LockAcquisition acquisition = new LockAcquisition(
                        RunnerId.fromString(entity.getOwner()),
                        entity.getAcquiredForMillis()
                    );
                    locks.put(lockKey, acquisition);
                });
        
        return locks;
    }
    
    @Override
    public void removeLock(LockKey key) {
        try {
            table.deleteItem(Key.builder()
                .partitionValue(key.toString())
                .build());
        } catch (Exception e) {
            // Ignore if lock doesn't exist
        }
    }
    
    @Override
    public boolean hasLocks() {
        return table.scan(ScanEnhancedRequest.builder()
                .consistentRead(true)
                .limit(1)
                .build())
                .items()
                .stream()
                .findFirst()
                .isPresent();
    }
    
    @Override
    public void clear() {
        if (autoCleanup) {
            // Delete all items from the table
            table.scan().items().forEach(item -> 
                table.deleteItem(Key.builder()
                    .partitionValue(item.getLockKey())
                    .build())
            );
        }
        metadata.clear();
    }
    
    @Override
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    @Override
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * Creates the lock table if it doesn't exist.
     */
    private void createTableIfNotExists() {
        try {
            // Check if table exists
            client.describeTable(DescribeTableRequest.builder()
                .tableName(tableName)
                .build());
        } catch (ResourceNotFoundException e) {
            // Table doesn't exist, create it
            table.createTable(CreateTableEnhancedRequest.builder()
                .provisionedThroughput(ProvisionedThroughput.builder()
                    .readCapacityUnits(readCapacityUnits)
                    .writeCapacityUnits(writeCapacityUnits)
                    .build())
                .build());
            
            // Wait for table to be active
            waitForTableToBeActive();
        }
    }
    
    /**
     * Waits for the table to become active.
     */
    private void waitForTableToBeActive() {
        try {
            client.waiter().waitUntilTableExists(DescribeTableRequest.builder()
                .tableName(tableName)
                .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to wait for table to become active: " + tableName, e);
        }
    }
    
    /**
     * Deletes the lock table if auto cleanup is enabled.
     * Called during test cleanup.
     */
    public void deleteTable() {
        if (autoCleanup) {
            try {
                table.deleteTable();
            } catch (ResourceNotFoundException e) {
                // Table already deleted, ignore
            }
        }
    }
}