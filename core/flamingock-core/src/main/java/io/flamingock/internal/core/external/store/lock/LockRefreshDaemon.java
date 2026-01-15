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
import io.flamingock.internal.util.TimeUtil;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

public class LockRefreshDaemon extends Thread {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("LockDaemon");

    private final Lock lock;

    private final TimeService timeService;

    public LockRefreshDaemon(Lock lock, TimeService timeService) {
        this.lock = lock;
        this.timeService = timeService;
        setDaemon(true);
    }

    @Override
    public void run() {
        logger.debug("Starting lock refresh daemon [lock_key={}]", lock.lockKey);
        boolean keepRefreshing = true;
        do {
            try {
                logger.trace("Lock daemon refreshing lock [lock_key={}]", lock.lockKey);
                keepRefreshing = lock.extend();
            } catch (LockException e) {
                logger.warn("Lock daemon refresh failed [lock_key={} error={}]", lock.lockKey, e.getMessage());
            } catch (Exception e) {
                logger.warn("Lock daemon encountered unexpected error [lock_key={} error={}]", lock.lockKey, e.getMessage());
            }
            reposeIfRequired();
        } while (keepRefreshing);
        logger.debug("Lock refresh daemon stopped [lock_key={}]", lock.lockKey);
    }

    private void reposeIfRequired() {


        if (!lock.isExpired()) {
            try {
                long sleepingTime = TimeUtil.diffInMillis(lock.expiresAt(), timeService.currentDateTime()) / 3;
                logAcquisitionUntil(sleepingTime);
                sleep(sleepingTime);

            } catch (InterruptedException ex) {
                logger.warn("Interrupted exception ignored");
            }
        } else {
            logger.trace("Lock daemon detected expired lock [expires_at={} lock_key={}]", lock.expiresAt(), lock.lockKey);
        }

    }

    private void logAcquisitionUntil(long sleepingTime) {
        logger.trace("Lock daemon sleeping [lock_key={} expires_at={} sleep_until={} sleep_duration={}ms]",
                lock.lockKey,
                lock.expiresAt(),
                timeService.currentDatePlusMillis(sleepingTime),
                sleepingTime);
    }


}