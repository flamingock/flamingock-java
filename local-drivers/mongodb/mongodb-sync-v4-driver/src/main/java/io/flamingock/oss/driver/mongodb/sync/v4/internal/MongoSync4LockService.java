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

package io.flamingock.oss.driver.mongodb.sync.v4.internal;

import com.mongodb.DuplicateKeyException;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import io.flamingock.community.internal.lock.LocalLockService;
import io.flamingock.core.engine.lock.LockAcquisition;
import io.flamingock.community.internal.lock.LockEntry;
import io.flamingock.core.engine.lock.LockKey;
import io.flamingock.core.engine.lock.LockServiceException;
import io.flamingock.core.runner.RunnerId;
import io.flamingock.core.util.TimeService;
import io.flamingock.oss.driver.common.mongodb.CollectionInitializator;
import io.flamingock.oss.driver.common.mongodb.MongoDBLockMapper;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.mongodb.MongoSync4CollectionWrapper;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.mongodb.MongoSync4DocumentWrapper;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.mongodb.ReadWriteConfiguration;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Date;

import static io.flamingock.community.internal.lock.LockEntryField.EXPIRES_AT_FIELD;
import static io.flamingock.community.internal.lock.LockEntryField.KEY_FIELD;
import static io.flamingock.community.internal.lock.LockEntryField.OWNER_FIELD;
import static io.flamingock.community.internal.lock.LockEntryField.STATUS_FIELD;
import static io.flamingock.core.engine.lock.LockStatus.LOCK_HELD;

public class MongoSync4LockService implements LocalLockService {

    private final MongoDBLockMapper<MongoSync4DocumentWrapper> mapper = new MongoDBLockMapper<>(() -> new MongoSync4DocumentWrapper(new Document()));


    private final MongoCollection<Document> collection;
    private final TimeService timeService;

    protected MongoSync4LockService(MongoDatabase mongoDatabase,
                                    String lockCollectionName,
                                    ReadWriteConfiguration readWriteConfiguration,
                                    TimeService timeService) {
        this.collection = mongoDatabase.getCollection(lockCollectionName)
                .withReadConcern(readWriteConfiguration.getReadConcern())
                .withReadPreference(readWriteConfiguration.getReadPreference())
                .withWriteConcern(readWriteConfiguration.getWriteConcern());
        this.timeService = timeService;
    }

    public void initialize(boolean indexCreation) {
        CollectionInitializator<MongoSync4DocumentWrapper> initializer = new CollectionInitializator<>(
                new MongoSync4CollectionWrapper(collection),
                () -> new MongoSync4DocumentWrapper(new Document()),
                new String[]{KEY_FIELD}
        );
        if (indexCreation) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }
    }


    @Override
    public LockAcquisition upsert(LockKey key, RunnerId owner, long leaseMillis) {
        LockEntry newLock = new LockEntry(key.toString(), LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        insertUpdate(newLock, false);
        return new LockAcquisition(owner, leaseMillis);
    }

    @Override
    public LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException {
        LockEntry newLock = new LockEntry(key.toString(), LOCK_HELD, owner.toString(), timeService.currentDatePlusMillis(leaseMillis));
        insertUpdate(newLock, true);
        return new LockAcquisition(owner, leaseMillis);
    }

    @Override
    public LockAcquisition getLock(LockKey lockKey) {
        Document result = collection.find(new Document().append(KEY_FIELD, lockKey.toString())).first();
        if (result != null) {
            return mapper.fromDocument(new MongoSync4DocumentWrapper(result));
        }
        return null;
    }

    @Override
    public void releaseLock(LockKey lockKey, RunnerId owner) {
        collection.deleteMany(Filters.and(Filters.eq(KEY_FIELD, lockKey.toString()), Filters.eq(OWNER_FIELD, owner.toString())));
    }

    protected void insertUpdate(LockEntry newLock, boolean onlyIfSameOwner)  {
        boolean lockHeld;
        String debErrorDetail = "not db error";
        Bson acquireLockQuery = getAcquireLockQuery(newLock.getKey(), newLock.getOwner(), onlyIfSameOwner);
        Document lockDocument = mapper.toDocument(newLock).getDocument();
        Document newLockDocumentSet = new Document().append("$set", lockDocument);
        try {
            UpdateResult result = collection.updateMany(acquireLockQuery, newLockDocumentSet, new UpdateOptions().upsert(!onlyIfSameOwner));
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
