/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.external.store.lock;

import io.flamingock.internal.util.id.RunnerId;

/**
 * Operations against the persistent lock store.
 *
 * <p>This interface intentionally exposes only the post-acquisition operations
 * needed by the runtime: extending an already-held lock, reading current lock
 * state, and releasing. <strong>Acquisition is not part of this interface.</strong>
 * Each implementation owns its own acquisition path:</p>
 *
 * <ul>
 *   <li>Community implementations acquire a lock via {@code CommunityLockService.upsert(...)},
 *       called from inside {@code CommunityLock.acquire()}.</li>
 *   <li>The cloud implementation receives the lock as part of the execution-plan
 *       response and never calls a dedicated acquisition method on this interface.</li>
 * </ul>
 */
public interface LockService {
// TODO remove keys and runnerId from methods. LockService(and others) should be only for a specific runner and service


    /**
     * Refreshes (extends) a lock that the caller already owns.
     *
     * <p>Asserts that {@code (existingLock.key == newLock.key && existingLock.owner == newLock.owner)}
     * and rotates internal lock state (e.g. on cloud, the {@code acquisitionId}). This method
     * does <strong>not</strong> grant ownership: a missing lock or a lock held by a different
     * owner causes {@link LockServiceException}. If the lock has already expired but is still
     * recorded against the same owner, extending succeeds (no other process has taken it yet).</p>
     *
     * @param key         lock key (typically the service id in cloud)
     * @param owner       caller's runner id; must equal the existing lock's owner
     * @param leaseMillis requested lease duration in milliseconds. Honored by community
     *                    implementations; ignored on cloud (the server keeps the duration set
     *                    at acquire time and echoes it back).
     * @return a {@link LockAcquisition} describing the lock after extension
     * @throws LockServiceException if no lock exists for {@code key}, the lock belongs to a
     *                              different owner, or the underlying store rejects the update
     */
    LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException;


    /**
     * Reads the current state of a lock.
     *
     * <p><strong>Pure read.</strong> This method has no side effects: it never creates,
     * extends, takes over, or releases a lock. It exists for diagnostic / error-recovery
     * paths that need to inspect who currently holds a contended lock.</p>
     *
     * <p>Note: not every backend can support this operation. The cloud implementation
     * throws {@link UnsupportedOperationException} because the lock REST API exposes no
     * read-only endpoint.</p>
     *
     * @param lockKey lock key
     * @return current lock state, or {@code null} if no lock is recorded for {@code lockKey}
     */
    LockAcquisition getLockInfo(LockKey lockKey);

    /**
     * Releases (deletes) the lock identified by {@code lockKey} when the caller is the owner.
     *
     * <p>Best-effort: implementations should not throw on contention or transient errors,
     * because release is typically called from cleanup paths where rethrowing would mask
     * the original failure. A lock not owned by {@code owner} is left untouched.</p>
     *
     * @param lockKey lock key
     * @param owner   caller's runner id
     */
    void releaseLock(LockKey lockKey, RunnerId owner);

}
