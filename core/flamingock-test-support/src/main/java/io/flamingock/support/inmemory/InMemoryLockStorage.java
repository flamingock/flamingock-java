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
package io.flamingock.support.inmemory;

import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of LockStorage for core e2e testing.
 * 
 * <p>This implementation stores locks in thread-safe ConcurrentHashMap, making it
 * ideal for testing core Flamingock lock functionality without external dependencies.
 * It provides fast execution and easy setup for unit and integration tests.</p>
 * 
 * <p><strong>Usage:</strong> Typically used through InMemoryTestKit, but can be used
 * directly when needed:</p>
 * <pre>{@code
 * InMemoryLockStorage storage = new InMemoryLockStorage();
 * LockTestHelper helper = new LockTestHelper(storage);
 * }</pre>
 *
 */
class InMemoryLockStorage {
    
    private final Map<LockKey, LockAcquisition> locks = new ConcurrentHashMap<>();
    private final Map<String, Object> metadata = new HashMap<>();

    public synchronized void storeLock(LockKey key, LockAcquisition acquisition) {
        locks.put(key, acquisition);
    }

    public synchronized LockAcquisition getLock(LockKey key) {
        return locks.get(key);
    }

    public synchronized Map<LockKey, LockAcquisition> getAllLocks() {
        return new HashMap<>(locks);
    }

    public synchronized void removeLock(LockKey key) {
        locks.remove(key);
    }

    public synchronized void clear() {
        locks.clear();
        metadata.clear();
    }

    public synchronized void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public synchronized Object getMetadata(String key) {
        return metadata.get(key);
    }

    public synchronized boolean hasLocks() {
        return !locks.isEmpty();
    }
}