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

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct tests of {@link LockRefreshDaemon#reposeIfRequired}, which is the predicate where the
 * daemon decides whether and how long to sleep between refresh iterations. The contract:
 *
 * <ul>
 *   <li>If {@code expiresAt > now}: sleep proportional to remaining lease (one third).</li>
 *   <li>If {@code expiresAt <= now} (expired but not released): sleep
 *       {@code lock.retryFrequencyMillis} so we don't tight-loop.</li>
 *   <li>Compute on a single read of {@code expiresAt} so a {@code release()} mid-method that
 *       back-dates the lease can't push {@code Thread.sleep} into a negative-argument
 *       {@code IllegalArgumentException}.</li>
 *   <li>{@code InterruptedException} during sleep restores the interrupt flag.</li>
 * </ul>
 */
class LockRefreshDaemonTest {

    private static final long LEASE_MILLIS = 9_000L;
    private static final long RETRY_FREQUENCY_MILLIS = 100L;

    private LockService lockService;
    private TimeService timeService;
    private RunnerId owner;
    private LockKey lockKey;

    @BeforeEach
    void setUp() {
        lockService = mock(LockService.class);
        timeService = TimeService.getDefault();
        owner = RunnerId.fromString("runner-A");
        lockKey = LockKey.fromString("test-lock");
    }

    @Test
    @DisplayName("reposeIfRequired: when expired (diff <= 0) sleeps retryFrequencyMillis to floor the retry rate")
    void reposeFloorsRetryWhenExpired() {
        Lock lock = newLock();
        // Force expiresAt into the past — diff in reposeIfRequired will be <= 0.
        lock.updateLease(-1_000L);

        LockRefreshDaemon daemon = new LockRefreshDaemon(lock, timeService);

        long elapsed = timeMillis(daemon::reposeIfRequired);

        assertTrue(elapsed >= RETRY_FREQUENCY_MILLIS - 50,
                "expired-lock branch must sleep at least retryFrequencyMillis, slept[" + elapsed + "]ms");
        assertTrue(elapsed < RETRY_FREQUENCY_MILLIS + 1_500,
                "expired-lock branch must not sleep proportional to a future expiry, slept[" + elapsed + "]ms");
    }

    @Test
    @DisplayName("reposeIfRequired: when not expired sleeps a third of the remaining lease")
    void reposeSleepsProportionallyWhenNotExpired() {
        Lock lock = newLock();
        // Lease ~600ms in the future → expected sleep ~200ms (one third).
        lock.updateLease(600L);

        LockRefreshDaemon daemon = new LockRefreshDaemon(lock, timeService);

        long elapsed = timeMillis(daemon::reposeIfRequired);

        assertTrue(elapsed >= 100, "expected ~200ms proportional sleep, slept[" + elapsed + "]ms");
        assertTrue(elapsed < 600, "must sleep less than the full lease, slept[" + elapsed + "]ms");
    }

    @Test
    @DisplayName("reposeIfRequired: restores the interrupt flag when the sleep is interrupted")
    void reposeRestoresInterruptFlag() throws InterruptedException {
        Lock lock = newLock();
        // Long sleep so the test thread reliably catches it sleeping.
        lock.updateLease(LEASE_MILLIS);
        LockRefreshDaemon daemon = new LockRefreshDaemon(lock, timeService);

        AtomicLong observedFlag = new AtomicLong(-1);
        Thread runner = new Thread(() -> {
            daemon.reposeIfRequired();
            observedFlag.set(Thread.currentThread().isInterrupted() ? 1 : 0);
        }, "repose-test");

        runner.start();
        // Give the runner a moment to enter sleep, then interrupt.
        Thread.sleep(50);
        runner.interrupt();
        runner.join(2_000L);

        assertFalse(runner.isAlive(), "runner thread must finish after interrupt");
        assertTrue(observedFlag.get() == 1,
                "reposeIfRequired must restore the interrupt flag (Thread.currentThread().interrupt())");
    }

    private Lock newLock() {
        when(lockService.extendLock(org.mockito.ArgumentMatchers.any(),
                                     org.mockito.ArgumentMatchers.any(),
                                     org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> new LockAcquisition(owner, LEASE_MILLIS));

        return new Lock(
                owner,
                lockKey,
                LEASE_MILLIS,
                /* stopTryingAfterMillis */ 1_000L,
                RETRY_FREQUENCY_MILLIS,
                lockService,
                timeService,
                /* refreshDaemonEnabled */ false
        ) {
            @Override
            public LocalDateTime expiresAt() {
                return super.expiresAt();
            }
        };
    }

    private static long timeMillis(Runnable r) {
        long start = System.currentTimeMillis();
        r.run();
        return System.currentTimeMillis() - start;
    }
}
