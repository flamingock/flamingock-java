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

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * DynamoDB entity representation of a distributed lock for TestKit usage.
 * 
 * <p>This entity represents a distributed lock stored in DynamoDB, providing
 * the necessary fields for lock management including ownership and acquisition
 * duration tracking.</p>
 * 
 * <p>The entity uses lockKey as the partition key to ensure unique
 * lock identification and atomic operations for lock acquisition and release.</p>
 */
@DynamoDbBean
public class DynamoDBLockEntity {
    
    private String lockKey;
    private String owner;
    private long acquiredForMillis;
    
    public DynamoDBLockEntity() {
        // Required for DynamoDB Enhanced Client
    }
    
    @DynamoDbPartitionKey
    @DynamoDbAttribute("lockKey")
    public String getLockKey() {
        return lockKey;
    }
    
    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }
    
    @DynamoDbAttribute("owner")
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    @DynamoDbAttribute("acquiredForMillis")
    public long getAcquiredForMillis() {
        return acquiredForMillis;
    }
    
    public void setAcquiredForMillis(long acquiredForMillis) {
        this.acquiredForMillis = acquiredForMillis;
    }
}