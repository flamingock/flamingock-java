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


import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static java.time.temporal.ChronoUnit.MILLIS;

public class Lock {

    public static final String LOG_EXPIRED_TEMPLATE = "Lock[{}] not refreshed at[{}] because the it's canceled/expired[{}]";
    private static final Logger logger = FlamingockLoggerFactory.getLogger("Lock");
    protected final LockKey lockKey;

    //injections
    protected final LockService lockService;

    protected final TimeService timeService;

    protected final RunnerId owner;
    protected final long leaseMillis;
    protected final long retryFrequencyMillis;
    protected final long stopTryingAfterMillis;
    private final boolean refreshDaemonEnabled;
    /**
     * It should never be null(after acquisition), just acquired(after now) or expired(before now)
     */
    protected volatile LocalDateTime expiresAt;

    /**
     * The single refresh daemon associated with this Lock instance, if any. Guarded by
     * {@code synchronized(this)} together with {@link #expiresAt} mutations in {@link #release()}
     * and {@link #startDaemonIfEnabled()}. Package-private so tests in the same package can
     * inspect daemon state without needing a public accessor.
     */
    LockRefreshDaemon activeDaemon;


    public Lock(RunnerId owner,
                LockKey lockKey,
                long leaseMillis,
                long stopTryingAfterMillis,
                long retryFrequencyMillis,
                LockService lockService,
                TimeService timeService,
                boolean refreshDaemonEnabled) {
        this.lockKey = lockKey;
        this.leaseMillis = leaseMillis;
        this.stopTryingAfterMillis = stopTryingAfterMillis;
        this.retryFrequencyMillis = retryFrequencyMillis;
        this.owner = owner;
        this.lockService = lockService;
        this.timeService = timeService;
        this.refreshDaemonEnabled = refreshDaemonEnabled;
    }


    /**
     * Ensures the lock is safely acquired(safely here means it's acquired with enough margin to operate),
     * or throws an exception otherwise.
     * <p>
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
     * Otherwise, a race condition where a process A ensures the lock, starts a change that takes 2 seconds, but before finishing,
     * the lock is released(closed) and another instances acquire the lock, before process A has finished.
     */
    public final void release() {
        logger.debug("Releasing the lock");
        final LockRefreshDaemon daemonToStop;
        synchronized (this) {
            try {
                updateLease(timeService.daysToMills(-1));//forces expiring
                lockService.releaseLock(lockKey, owner);
                logger.debug("Lock released successfully");
            } catch (Exception ex) {
                logger.warn("Error removing the lock. Doesn't need manual intervention.", ex);
            }
            daemonToStop = activeDaemon;
            activeDaemon = null;
        }
        if (daemonToStop != null) {
            // Wake the daemon if it is sleeping so it observes expiry and exits promptly,
            // instead of waiting for its next scheduled iteration.
            daemonToStop.interrupt();
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


    /**
     * Starts the lock refresh daemon if {@code refreshDaemonEnabled} was set on this Lock and
     * one isn't already running for this instance. Idempotent: subsequent calls while a daemon
     * is already alive are no-ops, so callers higher up in the planner/cloud-mapper code paths
     * cannot accidentally spawn parallel daemons for the same Lock. The daemon is bound to this
     * specific Lock instance and is interrupted by {@link #release()} so it terminates promptly
     * when the lock's lifecycle ends.
     */
    public final synchronized void startDaemonIfEnabled() {
        if (!refreshDaemonEnabled) {
            logger.debug("Lock refresh daemon disabled by configuration [lock_key={}]", lockKey);
            return;
        }
        if (activeDaemon != null && activeDaemon.isAlive()) {
            logger.debug("Lock refresh daemon already running [lock_key={}]", lockKey);
            return;
        }
        activeDaemon = new LockRefreshDaemon(this, timeService);
        activeDaemon.start();
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
        LockAcquisition currentLock = lockService.getLockInfo(lockKey);
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
            logger.debug("Configured retry frequency[{}ms] exceeds lock expiration time", retryFrequencyMillis);
            sleepingMillis = Math.max(currentLockWillExpireInMillis, 500L);//0.5secs the minimum waiting before retrying
        }
        try {
            logger.debug("Waiting {}ms before retrying lock acquisition", sleepingMillis);
            Thread.sleep(sleepingMillis);
        } catch (InterruptedException ex) {
            logger.warn("Lock acquisition interrupted", ex);
            Thread.currentThread().interrupt();
        }
    }

}
