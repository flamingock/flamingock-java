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

package io.flamingock.oss.driver.dynamodb.internal.entities;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.local.lock.LockEntry;
import io.flamingock.core.local.lock.LockEntryField;
import io.flamingock.core.engine.lock.LockAcquisition;
import io.flamingock.core.engine.lock.LockStatus;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoDBConstants;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;


@DynamoDbBean
public class LockEntryEntity {

    private String partitionKey;
    private String key;
    private LockStatus status;
    private String lockOwner;
    private LocalDateTime expiresAt;

    public LockEntryEntity(LockEntry lock) {
        this.partitionKey = lock.getKey();
        this.key = lock.getKey();
        this.status = lock.getStatus();
        this.lockOwner = lock.getOwner();
        this.expiresAt = lock.getExpiresAt();
    }

    public LockEntryEntity() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute(DynamoDBConstants.LOCK_PK)
    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    @DynamoDbAttribute(LockEntryField.KEY_FIELD)
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @DynamoDbAttribute(LockEntryField.STATUS_FIELD)
    public LockStatus getStatus() {
        return status;
    }

    public void setStatus(LockStatus status) {
        this.status = status;
    }

    @DynamoDbAttribute(DynamoDBConstants.LOCK_OWNER)
    public String getLockOwner() {
        return lockOwner;
    }

    public void setLockOwner(String lockOwner) {
        this.lockOwner = lockOwner;
    }

    @DynamoDbAttribute(LockEntryField.EXPIRES_AT_FIELD)
    public Long getExpiresAt() {
        return Timestamp.valueOf(expiresAt).getTime();
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresAt), ZoneId.systemDefault());
    }

    public LockEntry toLockEntry() {
        return new LockEntry(key, status, lockOwner, expiresAt);
    }

    public LockAcquisition getlockAcquisition() {
        long expiration = this.expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long diffMillis = expiration - now;
        return new LockAcquisition(RunnerId.fromString(this.lockOwner), diffMillis);
    }
}
