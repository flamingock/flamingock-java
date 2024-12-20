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

package io.flamingock.oss.driver.couchbase.internal;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.RemoveOptions;
import com.couchbase.client.java.kv.ReplaceOptions;
import io.flamingock.core.driver.lock.LocalLockService;
import io.flamingock.core.engine.lock.LockAcquisition;
import io.flamingock.core.driver.lock.LockEntry;
import io.flamingock.core.engine.lock.LockKey;
import io.flamingock.core.engine.lock.LockServiceException;
import io.flamingock.core.engine.lock.LockStatus;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.TimeService;
import io.flamingock.commons.utils.TimeUtil;
import io.flamingock.oss.driver.couchbase.internal.util.CouchBaseUtil;
import io.flamingock.oss.driver.couchbase.internal.util.LockEntryKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

import static io.flamingock.core.driver.lock.LockEntryField.EXPIRES_AT_FIELD;
import static io.flamingock.core.driver.lock.LockEntryField.KEY_FIELD;
import static io.flamingock.core.driver.lock.LockEntryField.OWNER_FIELD;
import static io.flamingock.core.driver.lock.LockEntryField.STATUS_FIELD;
import static io.flamingock.oss.driver.couchbase.internal.CouchbaseConstants.DOCUMENT_TYPE_KEY;
import static io.flamingock.oss.driver.couchbase.internal.CouchbaseConstants.DOCUMENT_TYPE_LOCK_ENTRY;

public class CouchbaseLockService implements LocalLockService {

    private static final Logger logger = LoggerFactory.getLogger(CouchbaseLockService.class);

    private static final Set<String> QUERY_FIELDS = Collections.emptySet();

    protected final Collection collection;
    protected final Cluster cluster;
    protected final CouchbaseGenericRepository couchbaseGenericRepository;

    private final LockEntryKeyGenerator keyGenerator = new LockEntryKeyGenerator();
    private final TimeService timeService;

    protected CouchbaseLockService(Cluster cluster, Collection collection, TimeService timeService) {
        this.cluster = cluster;
        this.collection = collection;
        this.couchbaseGenericRepository = new CouchbaseGenericRepository(cluster, collection, QUERY_FIELDS);
        this.timeService = timeService;
    }

    protected void initialize(boolean indexCreation) {
        this.couchbaseGenericRepository.initialize(indexCreation);
    }

    @Override
    public LockAcquisition upsert(LockKey key, RunnerId owner, long leaseMillis) {
        LockEntry newLock = new LockEntry(key.toString(), LockStatus.LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        String keyId = keyGenerator.toKey(newLock);
        try {
            GetResult result = collection.get(keyId);
            LockEntry existingLock = CouchBaseUtil.lockEntryFromEntity(result.contentAsObject());
            if (newLock.getOwner().equals(existingLock.getOwner()) ||
                    LocalDateTime.now().isAfter(existingLock.getExpiresAt())) {
                logger.debug("Lock with key {} already owned by us or is expired, so trying to perform a lock.",
                        existingLock.getKey());
                collection.replace(keyId, toEntity(newLock), ReplaceOptions.replaceOptions().cas(result.cas()));
                logger.debug("Lock with key {} updated", keyId);
            } else if (LocalDateTime.now().isBefore(existingLock.getExpiresAt())) {
                logger.debug("Already locked by {}, will expire at {}", existingLock.getOwner(),
                        existingLock.getExpiresAt());
                throw new LockServiceException("Get By" + keyId, newLock.toString(),
                        "Still locked by " + existingLock.getOwner() + " until " + existingLock.getExpiresAt());
            }
        } catch (DocumentNotFoundException documentNotFoundException) {
            logger.debug("Lock with key {} does not exist, so trying to perform a lock.", newLock.getKey());
            collection.insert(keyId, toEntity(newLock));
            logger.debug("Lock with key {} created", keyId);
        }
        return new LockAcquisition(owner, leaseMillis);
    }

    private JsonObject toEntity(LockEntry lockEntry) {
        JsonObject document = JsonObject.create();
        this.couchbaseGenericRepository.addField(document, KEY_FIELD, lockEntry.getKey());
        this.couchbaseGenericRepository.addField(document, OWNER_FIELD, lockEntry.getOwner());
        this.couchbaseGenericRepository.addField(document, STATUS_FIELD, lockEntry.getStatus().name());
        this.couchbaseGenericRepository.addField(document, EXPIRES_AT_FIELD, TimeUtil.toDate(lockEntry.getExpiresAt()));
        this.couchbaseGenericRepository.addField(document, DOCUMENT_TYPE_KEY, DOCUMENT_TYPE_LOCK_ENTRY);
        return document;
    }

    @Override
    public LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException {
        LockEntry newLock = new LockEntry(key.toString(), LockStatus.LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        String keyId = keyGenerator.toKey(newLock);
        try {
            GetResult result = collection.get(keyId);
            LockEntry existingLock = CouchBaseUtil.lockEntryFromEntity(result.contentAsObject());
            if (newLock.getOwner().equals(existingLock.getOwner())) {
                logger.debug("Lock with key {} already owned by us, so trying to perform a lock.",
                        existingLock.getKey());
                collection.replace(keyId, toEntity(newLock), ReplaceOptions.replaceOptions().cas(result.cas()));
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
        String key = keyGenerator.toKey(lockKey.toString());
        try {
            GetResult result = collection.get(key);
            return CouchBaseUtil.lockAcquisitionFromEntity(result.contentAsObject());
        } catch (DocumentNotFoundException documentNotFoundException) {
            logger.debug("Lock for key {} was not found.", key);
            return null;
        }
    }

    @Override
    public void releaseLock(LockKey lockKey, RunnerId owner) {
        String key = keyGenerator.toKey(lockKey.toString());
        try {
            GetResult result = collection.get(key);
            LockEntry existingLock = CouchBaseUtil.lockEntryFromEntity(result.contentAsObject());
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
}
