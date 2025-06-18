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

package io.flamingock.internal.core.community.lock;

import io.flamingock.internal.core.engine.lock.LockAcquisition;
import io.flamingock.internal.core.engine.lock.LockKey;
import io.flamingock.internal.core.engine.lock.LockService;
import io.flamingock.internal.core.engine.lock.LockServiceException;
import io.flamingock.internal.util.id.RunnerId;

public interface LocalLockService extends LockService {
    /**
     * a) If there is an existing lock in the database for the same key and owner {@code (existingLock.key==newLock.key &&
     * existingLoc.owner==newLock.owner)}, then the lock in database is updated/refreshed with the new values. The most
     * common scenario is to extend the lock's expiry date.
     * <p>
     * b) If there is an existing lock in the database for the same key and different owner, but expired {@code (existingLock.key==newLock.key &&
     * existingLock.owner!=newLock.owner && now > expiredAt)}, the lock is replaced with the newLock, so the owner of the lock for
     * that key is newLock.owner
     * <p>
     * c) If scenario b, but lock is not expired yet, should throw an LockPersistenceException.
     * <p>
     * d) If there isn't any lock with key=newLock.key, newLock is inserted.
     *
     * @param key Specially important in cloud execution as it represents a service
     * @param owner It references the running instance
     * @param leaseMillis How long(in millis) the lock will be acquired for.
     * @return the lockAcquisition with the owner and leaseMillis, among others
     * @throws LockServiceException if there is a lock in database with same key, but is expired and belong to
     *                                  another owner or cannot insert/update the lock for any other reason
     */
    LockAcquisition upsert(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException;


}
