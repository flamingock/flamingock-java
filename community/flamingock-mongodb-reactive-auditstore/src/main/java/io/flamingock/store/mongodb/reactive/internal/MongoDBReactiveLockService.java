/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.mongodb.reactive.internal;

import com.mongodb.DuplicateKeyException;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.MongoDBLockMapper;
import io.flamingock.internal.common.mongodb.MongoDBReactiveCollectionHelper;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.external.store.lock.LockServiceException;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockEntry;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.reactive.util.PublisherSync;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Date;

import static io.flamingock.internal.core.external.store.lock.LockStatus.LOCK_HELD;
import static io.flamingock.internal.core.external.store.lock.community.CommunityLockEntryConstants.EXPIRES_AT_FIELD;
import static io.flamingock.internal.core.external.store.lock.community.CommunityLockEntryConstants.KEY_FIELD;
import static io.flamingock.internal.core.external.store.lock.community.CommunityLockEntryConstants.OWNER_FIELD;
import static io.flamingock.internal.core.external.store.lock.community.CommunityLockEntryConstants.STATUS_FIELD;

public class MongoDBReactiveLockService implements CommunityLockService {

    private final MongoDBLockMapper<MongoDBDocumentHelper> mapper = new MongoDBLockMapper<>(() -> new MongoDBDocumentHelper(new Document()));


    private final MongoCollection<Document> collection;
    private final TimeService timeService;

    public MongoDBReactiveLockService(MongoDatabase database,
                                    String collectionName,
                                    ReadConcern readConcern,
                                    ReadPreference readPreference,
                                    WriteConcern writeConcern,
                                    TimeService timeService) {
        this(database.getCollection(collectionName),
                            readConcern,
                            readPreference,
                            writeConcern,
                            timeService);
    }

    protected MongoDBReactiveLockService(MongoCollection<Document> collection,
                                       ReadConcern readConcern,
                                       ReadPreference readPreference,
                                       WriteConcern writeConcern,
                                       TimeService timeService) {
        this.collection = collection
                .withReadConcern(readConcern)
                .withReadPreference(readPreference)
                .withWriteConcern(writeConcern);
        this.timeService = timeService;
    }

    public void initialize(boolean autoCreate) {
        CollectionInitializator<MongoDBDocumentHelper> initializer = new CollectionInitializator<>(
                new MongoDBReactiveCollectionHelper(collection),
                () -> new MongoDBDocumentHelper(new Document()),
                new String[]{KEY_FIELD}
        );
        if (autoCreate) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }
    }


    @Override
    public LockAcquisition upsert(LockKey key, RunnerId owner, long leaseMillis) {
        CommunityLockEntry newLock = new CommunityLockEntry(key.toString(), LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        insertUpdate(newLock, false);
        return new LockAcquisition(owner, leaseMillis);
    }

    @Override
    public LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException {
        CommunityLockEntry newLock = new CommunityLockEntry(key.toString(), LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        insertUpdate(newLock, true);
        return new LockAcquisition(owner, leaseMillis);
    }

    @Override
    public LockAcquisition getLockInfo(LockKey lockKey) {
        Document result = PublisherSync.first(
                collection.find(new Document().append(KEY_FIELD, lockKey.toString())).first());
        if (result != null) {
            return mapper.fromDocument(new MongoDBDocumentHelper(result));
        }
        return null;
    }

    @Override
    public void releaseLock(LockKey lockKey, RunnerId owner) {
        PublisherSync.first(collection.deleteMany(
                Filters.and(Filters.eq(KEY_FIELD, lockKey.toString()), Filters.eq(OWNER_FIELD, owner.toString()))));
    }

    protected void insertUpdate(CommunityLockEntry newLock, boolean onlyIfSameOwner) {
        boolean lockHeld;
        String debErrorDetail = "not db error";
        Bson acquireLockQuery = getAcquireLockQuery(newLock.getKey(), newLock.getOwner(), onlyIfSameOwner);
        Document lockDocument = mapper.toDocument(newLock).getDocument();
        Document newLockDocumentSet = new Document().append("$set", lockDocument);
        try {
            UpdateResult result = PublisherSync.first(collection.updateMany(
                    acquireLockQuery,
                    newLockDocumentSet,
                    new UpdateOptions().upsert(!onlyIfSameOwner)));
            if (result == null) {
                throw new LockServiceException(
                        acquireLockQuery.toString(),
                        newLockDocumentSet.toString(),
                        "MongoDB updateMany operation completed without returning an UpdateResult"
                );
            }
            lockHeld = result.getModifiedCount() <= 0 && result.getUpsertedId() == null;

        } catch (MongoWriteException ex) {
            lockHeld = ex.getError().getCategory() == ErrorCategory.DUPLICATE_KEY;

            if (!lockHeld) {
                throw ex;
            }
            debErrorDetail = ex.getError().toString();

        } catch (DuplicateKeyException ex) {
            lockHeld = true;
            debErrorDetail = ex.getMessage();
        }

        if (lockHeld) {
            throw new LockServiceException(
                    acquireLockQuery.toString(),
                    newLockDocumentSet.toString(),
                    debErrorDetail
            );
        }
    }

    protected Bson getAcquireLockQuery(String lockKey, String owner, boolean onlyIfSameOwner) {
        Bson expirationCond = Filters.lt(EXPIRES_AT_FIELD, new Date());
        Bson ownerCond = Filters.eq(OWNER_FIELD, owner);
        Bson keyCond = Filters.eq(KEY_FIELD, lockKey);
        Bson statusCond = Filters.eq(STATUS_FIELD, LOCK_HELD.name());
        return onlyIfSameOwner
                ? Filters.and(keyCond, statusCond, ownerCond)
                : Filters.and(keyCond, Filters.or(expirationCond, ownerCond));
    }
}
