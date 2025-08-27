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
package io.flamingock.mongodb.kit;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.core.store.lock.LockAcquisition;
import io.flamingock.internal.core.store.lock.LockKey;
import io.flamingock.internal.util.id.RunnerId;
import org.bson.Document;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB implementation of LockStorage for real database testing.
 * Only depends on MongoDB client/database and core Flamingock classes.
 * Does not depend on MongoDB-specific Flamingock components like MongoSyncTargetSystem.
 */
public class MongoSyncLockStorage implements LockStorage {
    
    private static final String LOCK_COLLECTION_NAME = "flamingockLocks";
    private static final String METADATA_COLLECTION_NAME = "flamingockLockMetadata";
    
    private final MongoDatabase database;
    private final MongoCollection<Document> lockCollection;
    private final MongoCollection<Document> metadataCollection;
    
    public MongoSyncLockStorage(MongoDatabase database) {
        this(database, LOCK_COLLECTION_NAME);
    }
    
    public MongoSyncLockStorage(MongoDatabase database, String lockCollectionName) {
        this.database = database;
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
        
        lockCollection.replaceOne(
                new Document("_id", key.toString()),
                lockDoc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
    }
    
    @Override
    public LockAcquisition getLock(LockKey key) {
        Document doc = lockCollection.find(new Document("_id", key.toString())).first();
        if (doc == null) {
            return null;
        }
        
        return documentToLockAcquisition(doc);
    }
    
    @Override
    public Map<LockKey, LockAcquisition> getAllLocks() {
        Map<LockKey, LockAcquisition> locks = new HashMap<>();
        
        for (Document doc : lockCollection.find()) {
            LockKey key = LockKey.fromString(doc.getString("key"));
            LockAcquisition acquisition = documentToLockAcquisition(doc);
            locks.put(key, acquisition);
        }
        
        return locks;
    }
    
    @Override
    public void removeLock(LockKey key) {
        lockCollection.deleteOne(new Document("_id", key.toString()));
    }
    
    @Override
    public boolean hasLocks() {
        return lockCollection.countDocuments() > 0;
    }
    
    @Override
    public void clear() {
        lockCollection.deleteMany(new Document());
        metadataCollection.deleteMany(new Document());
    }
    
    @Override
    public void setMetadata(String key, Object value) {
        Document metadataDoc = new Document()
                .append("_id", key)
                .append("key", key)
                .append("value", value);
        
        metadataCollection.replaceOne(
                new Document("_id", key),
                metadataDoc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
    }
    
    @Override
    public Object getMetadata(String key) {
        Document doc = metadataCollection.find(new Document("_id", key)).first();
        return doc != null ? doc.get("value") : null;
    }
    
    private LockAcquisition documentToLockAcquisition(Document doc) {
        RunnerId owner = RunnerId.fromString(doc.getString("owner"));
        long leaseMillis = doc.getLong("leaseMillis");
        
        return new LockAcquisition(owner, leaseMillis);
    }
}