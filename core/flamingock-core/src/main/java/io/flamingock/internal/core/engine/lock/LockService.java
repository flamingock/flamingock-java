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

package io.flamingock.internal.core.engine.lock;

import io.flamingock.internal.util.id.RunnerId;

/**
 * <p>Repository interface to manage lock in database, which will be used by LockManager</p>
 */
public interface LockService {
// TODO remove keys and runnerId from methods. LockService(and others) should be only for a specific runner and service


    /**
     * The only goal of this method is to update(mainly to extend the expiry date) the lock in case is already owned. So
     * it requires a Lock for the same key and owner {@code (existingLock.key==newLock.key && existingLoc.owner==newLock.owner)}.
     * <p>
     * If there is no lock for the key, or it doesn't belong to newLock.owner(), a LockPersistenceException is thrown.
     * <p>
     * Take into account that if it's already expired, but still with the same owner, we are lucky, no one has taken it yet,
     * so we can still extend the expiration time.
     *
     * @param key Specially important in cloud execution as it represents a service
     * @param owner It references the running instance
     * @param leaseMillis How long(in millis) the lock will be acquired for.
     * @return the lockAcquisition with the owner and leaseMillis, among others
     * @throws LockServiceException if there is a lock in database with same key, but is expired and belong to
     *                                  another owner or cannot insert/update the lock for any other reason
     */
    LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException;


    /**
     * Retrieves a lock by key
     *
     * @param lockKey key
     * @return LockEntry
     */
    //TODO Optional
    LockAcquisition getLock(LockKey lockKey);

    /**
     * Removes from database all the locks with the same key(only can be one) and owner
     *
     * @param lockKey lock key
     * @param owner   lock owner
     */
    void releaseLock(LockKey lockKey, RunnerId owner);

}
