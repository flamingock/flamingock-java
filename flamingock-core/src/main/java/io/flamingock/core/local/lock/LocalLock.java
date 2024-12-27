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

package io.flamingock.core.local.lock;

import io.flamingock.core.engine.lock.LockKey;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.engine.lock.LockAcquisition;
import io.flamingock.core.engine.lock.LockException;
import io.flamingock.core.engine.lock.LockServiceException;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class LocalLock extends Lock {

    private static final Logger logger = LoggerFactory.getLogger(LocalLock.class);


    public static Lock getLock(long leaseMillis,
                               long stopTryingAfterMillis,
                               long retryFrequencyMillis,
                               RunnerId owner,
                               LocalLockService lockService,
                               TimeService timeService) {
        LocalLock lock = new LocalLock(leaseMillis, stopTryingAfterMillis, retryFrequencyMillis, owner, lockService, timeService);
        lock.acquire();
        return lock;

    }


    private LocalLock(long leaseMillis,
                      long stopTryingAfterMillis,
                      long retryFrequencyMillis,
                      RunnerId owner,
                      LocalLockService lockService,
                      TimeService timeService) {
        super(owner, LockKey.fromString("DEFAULT_KEY"), leaseMillis, stopTryingAfterMillis, retryFrequencyMillis, lockService, timeService);
    }

    private LocalLockService getLockService() {
        return (LocalLockService) lockService;
    }


    /**
     * This is supposed to be called just once, per lock, from the static method `getLock`
     *
     * @throws LockException if the lock cannot be acquired
     */
    private void acquire() throws LockException {
        Instant shouldStopTryingAt = timeService.nowPlusMillis(stopTryingAfterMillis);
        boolean keepLooping = true;
        do {
            try {
                logger.info("Flamingock trying to acquire the lock");
                LockAcquisition lockAcquisition = getLockService().upsert(lockKey, owner, leaseMillis);
                updateLease(lockAcquisition.getAcquiredForMillis());
                keepLooping = false;
            } catch (LockServiceException ex) {
                handleLockException(true, shouldStopTryingAt, ex);
            }

        } while (keepLooping);
        logger.info("Flamingock acquired the lock until: {}", expiresAt());
    }

}