/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests covering the singleton lifecycle of the lock refresh daemon — ensuring that repeated
 * {@code startDaemonIfEnabled()} calls (e.g. across consecutive execution plans) never spawn
 * parallel daemons for the same Lock instance, and that {@code release()} stops the daemon
 * promptly via interrupt.
 */
class LockTest {

    private static final long LEASE_MILLIS = 60_000L;

    private LockService lockService;
    private TimeService timeService;
    private LockKey lockKey;
    private RunnerId owner;

    @BeforeEach
    void setUp() {
        lockService = mock(LockService.class);
        timeService = TimeService.getDefault();
        lockKey = LockKey.fromString("test-lock");
        owner = RunnerId.fromString("runner-A");
        // Daemon iterations call extendLock; return a successful acquisition so the daemon
        // proceeds to its sleep and stays observable in tests.
        when(lockService.extendLock(any(), any(), anyLong()))
                .thenAnswer(inv -> new LockAcquisition(owner, LEASE_MILLIS));
    }

    @Test
    @DisplayName("startDaemonIfEnabled: idempotent — second call does not spawn a parallel daemon")
    void startDaemonIfEnabledIsIdempotent() throws InterruptedException {
        Lock lock = lockWithFutureExpiry(true);

        lock.startDaemonIfEnabled();
        LockRefreshDaemon firstDaemon = lock.activeDaemon;
        assertNotNull(firstDaemon);
        assertTrue(firstDaemon.isAlive());

        lock.startDaemonIfEnabled();
        LockRefreshDaemon secondDaemon = lock.activeDaemon;

        assertSame(firstDaemon, secondDaemon, "subsequent startDaemonIfEnabled calls must not replace the running daemon");

        // cleanup
        lock.release();
        firstDaemon.join(2_000L);
    }

    @Test
    @DisplayName("startDaemonIfEnabled: no-op when refreshDaemonEnabled is false")
    void startDaemonIfEnabledIsNoOpWhenDisabled() {
        Lock lock = lockWithFutureExpiry(false);

        lock.startDaemonIfEnabled();

        assertNull(lock.activeDaemon, "no daemon should be created when refreshDaemonEnabled is false");
    }

    @Test
    @DisplayName("isReleased: false until release() is called, true forever after")
    void releasedFlagFlipsOnRelease() {
        Lock lock = lockWithFutureExpiry(false);

        assertFalse(lock.isReleased(), "fresh Lock must report isReleased() == false");

        lock.release();

        assertTrue(lock.isReleased(), "release() must set isReleased() == true");
    }

    @Test
    @DisplayName("release: clears the active daemon reference and interrupts the daemon thread so it terminates promptly")
    void releaseStopsRunningDaemon() throws InterruptedException {
        Lock lock = lockWithFutureExpiry(true);
        lock.startDaemonIfEnabled();
        LockRefreshDaemon daemon = lock.activeDaemon;
        assertNotNull(daemon);
        assertTrue(daemon.isAlive());

        lock.release();

        assertNull(lock.activeDaemon, "release must clear the active daemon reference");
        daemon.join(2_000L);
        assertFalse(daemon.isAlive(), "daemon thread must terminate shortly after release()");
    }

    private Lock lockWithFutureExpiry(boolean refreshDaemonEnabled) {
        Lock lock = new Lock(
                owner,
                lockKey,
                LEASE_MILLIS,
                /* stopTryingAfterMillis */ 1_000L,
                /* retryFrequencyMillis */ 100L,
                lockService,
                timeService,
                refreshDaemonEnabled
        );
        // Set expiresAt to the future so the daemon's first iteration succeeds and proceeds to
        // sleep (rather than seeing the lock expired and exiting immediately).
        lock.updateLease(LEASE_MILLIS);
        return lock;
    }
}
