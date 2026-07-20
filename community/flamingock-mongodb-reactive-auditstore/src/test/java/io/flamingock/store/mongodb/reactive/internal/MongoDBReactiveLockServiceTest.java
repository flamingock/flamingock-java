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

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.external.store.lock.LockServiceException;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.reactive.util.PublisherSync;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class MongoDBReactiveLockServiceTest {

    private static final String DB_NAME = "test";
    private static final String LOCK_COLLECTION = "testFlamingockLock";
    private static final LockKey LOCK_KEY = LockKey.fromString("lockKey1");

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"));

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoDBReactiveLockService lockService;

    @BeforeEach
    void beforeEach() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase(DB_NAME);
        lockService = new MongoDBReactiveLockService(
                database,
                LOCK_COLLECTION,
                ReadConcern.MAJORITY,
                ReadPreference.primary(),
                WriteConcern.MAJORITY.withJournal(true),
                TimeService.getDefault());
        lockService.initialize(true);
    }

    @AfterEach
    void afterEach() {
        PublisherSync.complete(database.drop());
        mongoClient.close();
    }

    @Test
    @DisplayName("Should acquire and read lock")
    void shouldAcquireAndReadLock() {
        RunnerId runnerId = RunnerId.fromString("runner-1");

        LockAcquisition lockAcquisition = lockService.upsert(LOCK_KEY, runnerId, 10000);
        LockAcquisition lockInfo = lockService.getLockInfo(LOCK_KEY);

        assertEquals(runnerId, lockAcquisition.getOwner());
        assertEquals(10000, lockAcquisition.getAcquiredForMillis());
        assertEquals(runnerId, lockInfo.getOwner());
    }

    @Test
    @DisplayName("Should not acquire a held lock with a different owner")
    void shouldNotAcquireHeldLockWithDifferentOwner() {
        lockService.upsert(LOCK_KEY, RunnerId.fromString("runner-1"), 10000);

        assertThrows(LockServiceException.class, () ->
                lockService.upsert(LOCK_KEY, RunnerId.fromString("runner-2"), 10000));
    }

    @Test
    @DisplayName("Should extend and release lock")
    void shouldExtendAndReleaseLock() {
        RunnerId runnerId = RunnerId.fromString("runner-1");
        lockService.upsert(LOCK_KEY, runnerId, 10000);

        LockAcquisition extension = lockService.extendLock(LOCK_KEY, runnerId, 20000);
        lockService.releaseLock(LOCK_KEY, runnerId);

        assertEquals(runnerId, extension.getOwner());
        assertEquals(20000, extension.getAcquiredForMillis());
        assertNull(lockService.getLockInfo(LOCK_KEY));
    }
}
