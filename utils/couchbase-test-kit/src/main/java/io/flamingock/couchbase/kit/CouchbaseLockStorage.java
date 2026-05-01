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
package io.flamingock.couchbase.kit;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.RemoveOptions;
import com.couchbase.client.java.kv.ReplaceOptions;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.common.couchbase.CouchbaseLockMapper;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.external.store.lock.LockServiceException;
import io.flamingock.internal.core.external.store.lock.LockStatus;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockEntry;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;

/**
 * Couchbase implementation of LockStorage for real database testing.
 * Only depends on Couchbase client/database and core Flamingock classes.
 * Does not depend on Couchbase-specific Flamingock components like CouchbaseTargetSystem.
 */
public class CouchbaseLockStorage implements LockStorage {

    private final Cluster cluster;
    private final Collection lockCollection;
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();
    private final TimeService timeService;

    private final CouchbaseLockMapper mapper = new CouchbaseLockMapper();

    public CouchbaseLockStorage(Cluster cluster, String bucketName, String scopeName) {
        this(cluster, bucketName, scopeName, DEFAULT_LOCK_STORE_NAME);
    }

    public CouchbaseLockStorage(Cluster cluster, String bucketName, String scopeName, String lockCollectionName) {
        this.cluster = cluster;
        this.lockCollection = cluster.bucket(bucketName).scope(scopeName).collection(lockCollectionName);
        this.timeService = TimeService.getDefault();
    }

    @Override
    public void storeLock(LockKey key, LockAcquisition acquisition) {
        CommunityLockEntry newLock = new CommunityLockEntry(key.toString(), LockStatus.LOCK_HELD, acquisition.getOwner().toString(), timeService.currentDatePlusMillis(acquisition.getAcquiredForMillis()));
        String keyId = toKey(newLock);
        try {
            GetResult result = lockCollection.get(keyId);
            CommunityLockEntry existingLock = mapper.lockEntryFromDocument(result.contentAsObject());
            if (newLock.getOwner().equals(existingLock.getOwner()) ||
                LocalDateTime.now().isAfter(existingLock.getExpiresAt())) {
                lockCollection.replace(keyId, mapper.toDocument(newLock), ReplaceOptions.replaceOptions().cas(result.cas()));
            } else if (LocalDateTime.now().isBefore(existingLock.getExpiresAt())) {
                throw new LockServiceException("Get By" + keyId, newLock.toString(),
                    "Still locked by " + existingLock.getOwner() + " until " + existingLock.getExpiresAt());
            }
        } catch (DocumentNotFoundException documentNotFoundException) {
            lockCollection.insert(keyId, mapper.toDocument(newLock));
        }
    }

    @Override
    public LockAcquisition getLockInfo(LockKey lockKey) {
        String key = toKey(lockKey);
        try {
            GetResult result = lockCollection.get(key);
            return mapper.lockAcquisitionFromDocument(result.contentAsObject());
        } catch (DocumentNotFoundException documentNotFoundException) {
            return null;
        }
    }

    @Override
    public Map<LockKey, LockAcquisition> getAllLocks() {
        Map<LockKey, LockAcquisition> locks = new HashMap<>();
        return CouchbaseCollectionHelper.selectAllDocuments(
            cluster, lockCollection.bucketName(), lockCollection.scopeName(), lockCollection.name())
            .stream()
            .collect(Collectors.toMap(
                entry -> LockKey.fromString(entry.getString("key")),
                this::documentToLockAcquisition
            ));
    }

    @Override
    public void removeLock(LockKey lockKey) {
        String key = toKey(lockKey);
        try {
            GetResult result = lockCollection.get(key);
            lockCollection.remove(key, RemoveOptions.removeOptions().cas(result.cas()));
        } catch (DocumentNotFoundException documentNotFoundException) {
            // Lock for key is not found, nothing to do
        }
    }

    @Override
    public boolean hasLocks() {
        return !this.getAllLocks().isEmpty();
    }

    @Override
    public void clear() {
        CouchbaseCollectionHelper.deleteAllDocuments(cluster, lockCollection.bucketName(), lockCollection.scopeName(), lockCollection.name());
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

    private String toKey(CommunityLockEntry lockEntry) {
        return lockEntry.getKey();
    }

    private String toKey(LockKey lockKey) {
        return lockKey.toString();
    }

    private LockAcquisition documentToLockAcquisition(JsonObject doc) {
        RunnerId owner = RunnerId.fromString(doc.getString("owner"));
        long leaseMillis = doc.getLong("leaseMillis");

        return new LockAcquisition(owner, leaseMillis);
    }
}
