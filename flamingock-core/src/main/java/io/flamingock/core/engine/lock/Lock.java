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

package io.flamingock.core.engine.lock;


import io.flamingock.core.runner.RunnerId;
import io.flamingock.core.util.TimeService;
import io.flamingock.core.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static java.time.temporal.ChronoUnit.MILLIS;

public class Lock {

    private static final Logger logger = LoggerFactory.getLogger(Lock.class);

    public static final String LOG_EXPIRED_TEMPLATE = "Lock[{}] not refreshed at[{}] because the it's canceled/expired[{}]";

    protected final LockKey lockKey;

    //injections
    protected final LockService lockService;

    protected final TimeService timeService;

    protected final RunnerId owner;

    protected final long leaseMillis;

    protected final long retryFrequencyMillis;

    protected final long stopTryingAfterMillis;

    /**
     * It should never be null(after acquisition), just acquired(after now) or expired(before now)
     */
    protected volatile LocalDateTime expiresAt;


    public Lock(RunnerId owner,
                LockKey lockKey,
                long leaseMillis,
                long stopTryingAfterMillis,
                long retryFrequencyMillis,
                LockService lockService,
                TimeService timeService) {
        this.lockKey = lockKey;
        this.leaseMillis = leaseMillis;
        this.stopTryingAfterMillis = stopTryingAfterMillis;
        this.retryFrequencyMillis = retryFrequencyMillis;
        this.owner = owner;
        this.lockService = lockService;
        this.timeService = timeService;
    }


    /**
     * Ensures the lock is safely acquired(safely here means it's acquired with enough margin to operate),
     * or throws an exception otherwise.
     * <br />
     * In case the lock is about to expire, it will try to refresh it. In this scenario, the lock won't be considered
     * ensured until it's successfully extended. However, this scenario shouldn't happen, when a well configured daemon
     * is set up.
     *
     * @throws LockException if it cannot be ensured. Either is expired or, close to be expired and cannot be extended.
     */
    public final void ensure() throws LockException {
        logger.debug("Ensuring the lock");
        boolean ensured = false;
        Instant shouldStopTryingAt = timeService.nowPlusMillis(stopTryingAfterMillis);
        do {
            if (isExpired()) {
                throw new LockException(String.format(
                        "Lock not ensured at [%s] because the it's canceled/expired[%s]", timeService.currentDateTime(), expiresAt()
                ));
            }
            long margin = Math.max((long) (leaseMillis * 0.33/*30%*/), 1000L/*1sec*/);
            LocalDateTime threshold = expiresAt().minus(margin, MILLIS);
            if (timeService.currentDateTime().isAfter(threshold)) {
                try {
                    ensured = extend();
                } catch (LockServiceException ex) {
                    handleLockException(false, shouldStopTryingAt, ex);
                }
            } else {
                logger.debug("Dont need to refresh the lock at[{}], it's acquired until: {}", timeService.currentDateTime(), expiresAt());
                ensured = true;
            }
        } while (!ensured);
    }

    /**
     * Refreshes the lock if it's already taken. Throws an exception otherwise.
     *
     * @return true if the lock has been successfully refreshed, or false if lock shouldn't be refreshed because it's in the middle
     * of a release process, or it's already released.
     * @throws LockException if there is any problem refreshing the lock or it's not acquired at all.
     */
    public final boolean extend() throws LockException {
        if (isExpired()) {
            logger.info(LOG_EXPIRED_TEMPLATE, owner, timeService.currentDateTime(), expiresAt());
            return false;
        }
        try {
            logger.debug("Flamingock trying to refresh the lock");
            synchronized (this) {
                if (isExpired()) {
                    logger.info(LOG_EXPIRED_TEMPLATE, owner, timeService.currentDateTime(), expiresAt());
                    return false;
                }
                LockAcquisition lockAcquisition = lockService.extendLock(lockKey, owner, leaseMillis);
                updateLease(lockAcquisition.getAcquiredForMillis());
                logger.debug("Flamingock refreshed the lock until: {}", expiresAt());
                return true;
            }

        } catch (Exception ex) {
            throw new LockException(ex);
        }
    }


    /**
     * This should be called once all the process guarded by the lock(those who assumed the lock is ensured) has finished.
     * Otherwise, a race condition where a process A ensures the lock, starts a task that takes 2 seconds, but before finishing,
     * the lock is released(closed) and another instances acquire the lock, before process A has finished.
     */
    public final void release() {
        logger.info("Flamingock waiting to release the lock");
        synchronized (this) {
            try {
                logger.info("Flamingock releasing the lock");
                logger.debug("Flamingock expiring the lock");
                updateLease(timeService.daysToMills(-1));//forces expiring
                logger.debug("Flamingock removing the lock from database");
                lockService.releaseLock(lockKey, owner);
                logger.info("Flamingock released the lock");
            } catch (Exception ex) {
                logger.warn("Error removing the lock. Doesn't need manually intervention.", ex);
            }
        }
    }


    public LocalDateTime expiresAt() {
        return expiresAt;
    }


    protected final void updateLease(long leaseMillis) {
        expiresAt = timeService.currentDatePlusMillis(leaseMillis);
    }


    public final boolean isExpired() {
        return timeService.isPast(expiresAt());
    }


    @Override
    public String toString() {
        return "MongockLock{" +
                "owner='" + owner + '\'' +
                ", leaseMillis=" + leaseMillis +
                ", retryFrequencyMillis=" + retryFrequencyMillis +
                ", stopTryingAfterMillis=" + stopTryingAfterMillis +
                '}';
    }

    protected void handleLockException(boolean acquiringLock, Instant shouldStopTryingAt, LockServiceException ex) {
        LockAcquisition currentLock = lockService.getLock(lockKey);
        if (timeService.isPast(shouldStopTryingAt)) {
            throw new LockException(String.format(
                    "Quit trying lock after %s millis due to LockPersistenceException: \n\tcurrent lock:  %s\n\tnew lock: %s\n\tacquireLockQuery: %s\n\tdb error detail: %s",
                    stopTryingAfterMillis,
                    currentLock != null ? currentLock.toString() : "none",
                    ex.getNewLockEntity(),
                    ex.getAcquireLockQuery(),
                    ex.getErrorDetail()));
        }

        final boolean isLockOwnedByOtherProcess = currentLock != null && !currentLock.doesBelongTo(owner);
        if (isLockOwnedByOtherProcess) {
            LocalDateTime currentLockExpiresAt = LocalDateTime.now().plus(currentLock.getAcquiredForMillis(), MILLIS);
            logger.warn("Lock is taken by other process until: {}", currentLockExpiresAt);
            if (!acquiringLock) {
                throw new LockException(String.format(
                        "Lock held by other process. Cannot ensure lock.\n\tcurrent lock:  %s\n\tnew lock: %s\n\tacquireLockQuery: %s\n\tdb error detail: %s",
                        currentLock,
                        ex.getNewLockEntity(),
                        ex.getAcquireLockQuery(),
                        ex.getErrorDetail()));
            }
            waitForLock(currentLockExpiresAt);
        }

    }

    protected void waitForLock(LocalDateTime expiresAt) {
        long currentMillis = timeService.currentMillis();
        long currentLockWillExpireInMillis = expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - currentMillis;
        long sleepingMillis = retryFrequencyMillis;
        if (retryFrequencyMillis > currentLockWillExpireInMillis) {
            logger.info("The configured time frequency[{} millis] is higher than the current lock's expiration", retryFrequencyMillis);
            sleepingMillis = Math.max(currentLockWillExpireInMillis, 500L);//0.5secs the minimum waiting before retrying
        }
        logger.info("Flamingock will try to acquire the lock in {} mills", sleepingMillis);
        try {
            logger.info(
                    "Flamingock is going to sleep. Will retry in {}ms ({} minutes)",
                    sleepingMillis,
                    TimeUtil.millisToMinutes(sleepingMillis));
            Thread.sleep(sleepingMillis);
        } catch (InterruptedException ex) {
            logger.error("ERROR acquiring the lock", ex);
            Thread.currentThread().interrupt();
        }
    }

}
