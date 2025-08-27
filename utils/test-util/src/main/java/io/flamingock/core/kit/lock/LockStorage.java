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
package io.flamingock.core.kit.lock;

import io.flamingock.internal.core.store.lock.LockAcquisition;
import io.flamingock.internal.core.store.lock.LockKey;

import java.util.Map;

/**
 * Storage abstraction for lock operations used in testing.
 * 
 * <p>This interface enables lock-store agnostic testing by providing a consistent API
 * for storing and managing locks regardless of the underlying storage technology.
 * Implementations can use in-memory maps, MongoDB, DynamoDB, or any other storage.</p>
 * 
 * <p><strong>Implementing for new storage types:</strong></p>
 * <pre>{@code
 * public class MyStorageLockStorage implements LockStorage {
 *     public void storeLock(LockKey key, LockAcquisition acquisition) {
 *         // Store to your specific storage
 *     }
 *     // ... implement other methods
 * }
 * }</pre>
 * 
 * <p>Used by LockTestHelper to provide storage-agnostic lock testing capabilities.</p>
 */
public interface LockStorage {

    /**
     * Stores a lock acquisition.
     * @param key the lock key
     * @param acquisition the lock acquisition details
     */
    void storeLock(LockKey key, LockAcquisition acquisition);

    /**
     * Retrieves a lock acquisition by key.
     * @param key the lock key
     * @return lock acquisition if exists, null otherwise
     */
    LockAcquisition getLock(LockKey key);

    /**
     * Retrieves all stored locks.
     * @return map of all lock keys and their acquisitions
     */
    Map<LockKey, LockAcquisition> getAllLocks();

    /**
     * Removes a lock by key.
     * @param key the lock key to remove
     */
    void removeLock(LockKey key);

    /**
     * Checks if any locks exist.
     * @return true if any locks exist, false otherwise
     */
    boolean hasLocks();

    /**
     * Clears all stored locks (useful for test cleanup).
     */
    void clear();

    /**
     * Stores metadata for testing purposes.
     * @param key metadata key
     * @param value metadata value
     */
    void setMetadata(String key, Object value);

    /**
     * Retrieves metadata by key.
     * @param key metadata key
     * @return metadata value if exists, null otherwise
     */
    Object getMetadata(String key);
}