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
package io.flamingock.community.couchbase.internal;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.RemoveOptions;
import com.couchbase.client.java.kv.ReplaceOptions;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionInitializator;
import io.flamingock.internal.common.couchbase.CouchbaseLockMapper;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.store.lock.LockAcquisition;
import io.flamingock.internal.core.store.lock.community.CommunityLockEntry;
import io.flamingock.internal.core.store.lock.LockKey;
import io.flamingock.internal.core.store.lock.LockServiceException;
import io.flamingock.internal.core.store.lock.LockStatus;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;
import org.slf4j.Logger;

import java.time.LocalDateTime;

public class CouchbaseLockService implements CommunityLockService {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("CouchbaseLock");

    protected final Cluster cluster;
    protected final Bucket bucket;
    protected Collection collection;
    protected CouchbaseCollectionInitializator collectionInitializator;

    private final CouchbaseLockMapper mapper = new CouchbaseLockMapper();
    private final TimeService timeService;

    public CouchbaseLockService(CouchbaseTargetSystem targetSystem, TimeService timeService) {
        this.cluster = targetSystem.getCluster();
        this.bucket = targetSystem.getBucket();
        this.timeService = timeService;
    }

    public void initialize(boolean autoCreate, String scopeName, String collectionName) {
        this.collectionInitializator = new CouchbaseCollectionInitializator(cluster, bucket, scopeName, collectionName);
        this.collectionInitializator.initialize(autoCreate);
        this.collection = this.bucket.scope(scopeName).collection(collectionName);
    }

    @Override
    public LockAcquisition upsert(LockKey key, RunnerId owner, long leaseMillis) {
        CommunityLockEntry newLock = new CommunityLockEntry(key.toString(), LockStatus.LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        String keyId = toKey(newLock);
        try {
            GetResult result = collection.get(keyId);
            CommunityLockEntry existingLock = mapper.lockEntryFromDocument(result.contentAsObject());
            if (newLock.getOwner().equals(existingLock.getOwner()) ||
                    LocalDateTime.now().isAfter(existingLock.getExpiresAt())) {
                logger.debug("Lock with key {} already owned by us or is expired, so trying to perform a lock.",
                        existingLock.getKey());
                collection.replace(keyId, mapper.toDocument(newLock), ReplaceOptions.replaceOptions().cas(result.cas()));
                logger.debug("Lock with key {} updated", keyId);
            } else if (LocalDateTime.now().isBefore(existingLock.getExpiresAt())) {
                logger.debug("Already locked by {}, will expire at {}", existingLock.getOwner(),
                        existingLock.getExpiresAt());
                throw new LockServiceException("Get By" + keyId, newLock.toString(),
                        "Still locked by " + existingLock.getOwner() + " until " + existingLock.getExpiresAt());
            }
        } catch (DocumentNotFoundException documentNotFoundException) {
            logger.debug("Lock with key {} does not exist, so trying to perform a lock.", newLock.getKey());
            collection.insert(keyId, mapper.toDocument(newLock));
            logger.debug("Lock with key {} created", keyId);
        }
        return new LockAcquisition(owner, leaseMillis);
    }

    @Override
    public LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException {
        CommunityLockEntry newLock = new CommunityLockEntry(key.toString(), LockStatus.LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        String keyId = toKey(newLock);
        try {
            GetResult result = collection.get(keyId);
            CommunityLockEntry existingLock = mapper.lockEntryFromDocument(result.contentAsObject());
            if (newLock.getOwner().equals(existingLock.getOwner())) {
                logger.debug("Lock with key {} already owned by us, so trying to perform a lock.",
                        existingLock.getKey());
                collection.replace(keyId, mapper.toDocument(newLock), ReplaceOptions.replaceOptions().cas(result.cas()));
                logger.debug("Lock with key {} updated", keyId);
            } else {
                logger.debug("Already locked by {}, will expire at {}", existingLock.getOwner(),
                        existingLock.getExpiresAt());
                throw new LockServiceException("Get By " + keyId, newLock.toString(),
                        "Lock belongs to " + existingLock.getOwner());
            }
        } catch (DocumentNotFoundException documentNotFoundException) {
            throw new LockServiceException("Get By " + keyId, newLock.toString(),
                    documentNotFoundException.getMessage());
        }
        return new LockAcquisition(owner, leaseMillis);
    }

    @Override
    public LockAcquisition getLock(LockKey lockKey) {
        String key = toKey(lockKey);
        try {
            GetResult result = collection.get(key);
            return mapper.lockAcquisitionFromDocument(result.contentAsObject());
        } catch (DocumentNotFoundException documentNotFoundException) {
            logger.debug("Lock for key {} was not found.", key);
            return null;
        }
    }

    @Override
    public void releaseLock(LockKey lockKey, RunnerId owner) {
        String key = toKey(lockKey);
        try {
            GetResult result = collection.get(key);
            CommunityLockEntry existingLock = mapper.lockEntryFromDocument(result.contentAsObject());
            if (owner.equals(RunnerId.fromString(existingLock.getOwner()))) {
                logger.debug("Lock for key {} belongs to us, so removing.", key);
                collection.remove(key, RemoveOptions.removeOptions().cas(result.cas()));
            } else {
                logger.debug("Lock for key {} belongs to other owner, can not delete.", key);
            }
        } catch (DocumentNotFoundException documentNotFoundException) {
            logger.debug("Lock for key {} is not found, nothing to do", key);
        }
    }

    private String toKey(CommunityLockEntry lockEntry) {
        return lockEntry.getKey();
    }

    private String toKey(LockKey lockKey) {
        return lockKey.toString();
    }
}
