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
package io.flamingock.mongodb.reactive.kit;

import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.reactive.util.PublisherSync;
import org.bson.Document;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;

/**
 * MongoDB reactive implementation of LockStorage for real database testing.
 * Only depends on MongoDB client/database and core Flamingock classes.
 * Does not depend on MongoDB-specific Flamingock components like MongoDBReactiveTargetSystem.
 */
public class MongoDBReactiveLockStorage implements LockStorage {

    private final MongoCollection<Document> lockCollection;
    private final MongoCollection<Document> metadataCollection;

    public MongoDBReactiveLockStorage(MongoDatabase database) {
        this(database, DEFAULT_LOCK_STORE_NAME);
    }

    public MongoDBReactiveLockStorage(MongoDatabase database, String lockCollectionName) {
        this.lockCollection = database.getCollection(lockCollectionName);
        this.metadataCollection = database.getCollection(lockCollectionName + "Metadata");
    }

    @Override
    public void storeLock(LockKey key, LockAcquisition acquisition) {
        Document lockDoc = new Document()
                .append("_id", key.toString())
                .append("key", key.toString())
                .append("owner", acquisition.getOwner().getKey())
                .append("leaseMillis", acquisition.getAcquiredForMillis())
                .append("createdAt", new Date());

        PublisherSync.complete(lockCollection.replaceOne(
                new Document("_id", key.toString()),
                lockDoc,
                new ReplaceOptions().upsert(true)
        ));
    }

    @Override
    public LockAcquisition getLockInfo(LockKey key) {
        Document doc = PublisherSync.first(lockCollection.find(new Document("_id", key.toString())).first());
        if (doc == null) {
            return null;
        }

        return documentToLockAcquisition(doc);
    }

    @Override
    public Map<LockKey, LockAcquisition> getAllLocks() {
        Map<LockKey, LockAcquisition> locks = new HashMap<>();

        for (Document doc : PublisherSync.collect(lockCollection.find())) {
            LockKey key = LockKey.fromString(doc.getString("key"));
            LockAcquisition acquisition = documentToLockAcquisition(doc);
            locks.put(key, acquisition);
        }

        return locks;
    }

    @Override
    public void removeLock(LockKey key) {
        PublisherSync.complete(lockCollection.deleteOne(new Document("_id", key.toString())));
    }

    @Override
    public boolean hasLocks() {
        Long count = PublisherSync.first(lockCollection.countDocuments());
        return count != null && count > 0;
    }

    @Override
    public void clear() {
        PublisherSync.complete(lockCollection.deleteMany(new Document()));
        PublisherSync.complete(metadataCollection.deleteMany(new Document()));
    }

    @Override
    public void setMetadata(String key, Object value) {
        Document metadataDoc = new Document()
                .append("_id", key)
                .append("key", key)
                .append("value", value);

        PublisherSync.complete(metadataCollection.replaceOne(
                new Document("_id", key),
                metadataDoc,
                new ReplaceOptions().upsert(true)
        ));
    }

    @Override
    public Object getMetadata(String key) {
        Document doc = PublisherSync.first(metadataCollection.find(new Document("_id", key)).first());
        return doc != null ? doc.get("value") : null;
    }

    private LockAcquisition documentToLockAcquisition(Document doc) {
        RunnerId owner = RunnerId.fromString(doc.getString("owner"));
        long leaseMillis = doc.getLong("leaseMillis");

        return new LockAcquisition(owner, leaseMillis);
    }
}
