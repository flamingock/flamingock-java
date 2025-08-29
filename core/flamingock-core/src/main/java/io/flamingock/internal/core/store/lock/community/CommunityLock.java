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
package io.flamingock.internal.core.store.lock.community;

import io.flamingock.internal.core.store.lock.LockKey;
import io.flamingock.internal.core.store.lock.Lock;
import io.flamingock.internal.core.store.lock.LockAcquisition;
import io.flamingock.internal.core.store.lock.LockException;
import io.flamingock.internal.core.store.lock.LockServiceException;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.time.Instant;

public class CommunityLock extends Lock {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("Lock");


    public static Lock getLock(long leaseMillis,
                               long stopTryingAfterMillis,
                               long retryFrequencyMillis,
                               RunnerId owner,
                               CommunityLockService lockService,
                               TimeService timeService) {
        CommunityLock lock = new CommunityLock(leaseMillis, stopTryingAfterMillis, retryFrequencyMillis, owner, lockService, timeService);
        lock.acquire();
        return lock;

    }


    private CommunityLock(long leaseMillis,
                          long stopTryingAfterMillis,
                          long retryFrequencyMillis,
                          RunnerId owner,
                          CommunityLockService lockService,
                          TimeService timeService) {
        super(owner, LockKey.fromString("DEFAULT_KEY"), leaseMillis, stopTryingAfterMillis, retryFrequencyMillis, lockService, timeService);
    }

    private CommunityLockService getLockService() {
        return (CommunityLockService) lockService;
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
                logger.info("Attempting to acquire process lock [timeout={}s]", stopTryingAfterMillis / 1000);
                LockAcquisition lockAcquisition = getLockService().upsert(lockKey, owner, leaseMillis);
                updateLease(lockAcquisition.getAcquiredForMillis());
                keepLooping = false;
            } catch (LockServiceException ex) {
                handleLockException(true, shouldStopTryingAt, ex);
            }

        } while (keepLooping);
        logger.info("Process lock acquired successfully [expires_at={}]", expiresAt());
    }

}