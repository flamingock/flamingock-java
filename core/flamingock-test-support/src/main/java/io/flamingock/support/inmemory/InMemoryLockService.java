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
import io.flamingock.internal.core.external.store.lock.LockServiceException;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.util.id.RunnerId;

class InMemoryLockService implements CommunityLockService {
    
    private final InMemoryLockStorage lockStorage;
    private final RunnerId instanceId;
    
    public InMemoryLockService(InMemoryLockStorage lockStorage, RunnerId instanceId) {
        this.lockStorage = lockStorage;
        this.instanceId = instanceId;
    }
    
    @Override
    public LockAcquisition upsert(LockKey lockKey, RunnerId owner, long leaseMillis) throws LockServiceException {
        // Check if lock failure simulation is enabled
        if (isLockFailureSimulationEnabled()) {
            throw new LockServiceException("Simulated lock upsert failure", lockKey.toString(), owner.toString());
        }
        
        // Simple implementation: create and store the lock
        LockAcquisition acquisition = new LockAcquisition(owner, leaseMillis);
        lockStorage.storeLock(lockKey, acquisition);
        
        return acquisition;
    }
    
    @Override
    public LockAcquisition extendLock(LockKey lockKey, RunnerId owner, long leaseMillis) throws LockServiceException {
        // Check if lock failure simulation is enabled
        if (isLockFailureSimulationEnabled()) {
            throw new LockServiceException("Simulated lock extend failure", lockKey.toString(), owner.toString());
        }
        
        // Simple implementation: update existing lock
        LockAcquisition acquisition = new LockAcquisition(owner, leaseMillis);
        lockStorage.storeLock(lockKey, acquisition);
        
        return acquisition;
    }
    
    @Override
    public LockAcquisition getLock(LockKey lockKey) {
        return lockStorage.getLock(lockKey);
    }
    
    @Override
    public void releaseLock(LockKey lockKey, RunnerId owner) {
        if (isLockFailureSimulationEnabled()) {
            // Don't throw exception for release in test - just log or ignore
            return;
        }
        
        lockStorage.removeLock(lockKey);
    }

    private boolean isLockFailureSimulationEnabled() {
        return Boolean.TRUE.equals(lockStorage.getMetadata("lockService.shouldFail"));
    }
}