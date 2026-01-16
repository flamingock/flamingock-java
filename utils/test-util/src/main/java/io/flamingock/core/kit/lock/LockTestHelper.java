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

import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.util.id.RunnerId;

import java.util.Map;

/**
 * Storage-agnostic helper for lock testing operations.
 * 
 * <p>This class provides convenient methods for testing lock functionality regardless of
 * the underlying storage implementation. It works with any LockStorage implementation,
 * enabling consistent lock testing across in-memory, MongoDB, DynamoDB, etc.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * LockTestHelper helper = testKit.getLockHelper();
 * 
 * // Check if lock exists
 * assertTrue(helper.hasLock(lockKey));
 * 
 * // Verify lock ownership
 * LockAcquisition acquisition = helper.getLock(lockKey);
 * assertEquals(runnerId, acquisition.getOwner());
 * }</pre>
 */
public class LockTestHelper {
    
    private final LockStorage lockStorage;
    
    public LockTestHelper(LockStorage lockStorage) {
        this.lockStorage = lockStorage;
    }

    public Map<LockKey, LockAcquisition> getAllLocks() {
        return lockStorage.getAllLocks();
    }

    public boolean hasLock(LockKey lockKey) {
        return lockStorage.getLock(lockKey) != null;
    }

    public LockAcquisition getLock(LockKey lockKey) {
        return lockStorage.getLock(lockKey);
    }

    public void addLock(LockKey lockKey, String owner, long leaseMillis) {
        RunnerId ownerId = RunnerId.fromString(owner);
        LockAcquisition acquisition = new LockAcquisition(ownerId, leaseMillis);
        lockStorage.storeLock(lockKey, acquisition);
    }

    public void enableLockFailureSimulation() {
        lockStorage.setMetadata("lockService.shouldFail", true);
    }

    public void disableLockFailureSimulation() {
        lockStorage.setMetadata("lockService.shouldFail", false);
    }

    public boolean isLockFailureSimulationEnabled() {
        return Boolean.TRUE.equals(lockStorage.getMetadata("lockService.shouldFail"));
    }

    public void clear() {
        lockStorage.clear();
    }

    public boolean hasNoLocks() {
        return !lockStorage.hasLocks();
    }

    public boolean hasLockCount(int expectedCount) {
        return lockStorage.getAllLocks().size() == expectedCount;
    }

    public boolean lockExists(LockKey lockKey) {
        return hasLock(lockKey);
    }

    public boolean lockNotExists(LockKey lockKey) {
        return !hasLock(lockKey);
    }
}