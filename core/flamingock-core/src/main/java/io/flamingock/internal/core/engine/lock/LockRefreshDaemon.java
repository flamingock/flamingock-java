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

import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockRefreshDaemon extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(LockRefreshDaemon.class);

    private final Lock lock;

    private final TimeService timeService;

    public LockRefreshDaemon(Lock lock, TimeService timeService) {
        this.lock = lock;
        this.timeService = timeService;
        setDaemon(true);
    }

    @Override
    public void run() {
        logger.info("Starting Flamingock lock daemon...");
        boolean keepRefreshing = true;
        do {
            try {
                logger.debug("Flamingock(daemon) refreshing lock");
                keepRefreshing = lock.extend();
            } catch (LockException e) {
                logger.warn("Flamingock(daemon)Error refreshing lock: {}", e.getMessage());
            } catch (Exception e) {
                logger.warn("Flamingock(daemon)Generic error from daemon: {}", e.getMessage());
            }
            reposeIfRequired();
        } while (keepRefreshing);
        logger.info("Cancelled Flamingock lock daemon");
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
            logger.debug("Lock is canceled/expired[{}]", lock.expiresAt());
        }

    }

    private void logAcquisitionUntil(long sleepingTime) {
        logger.info("Lock acquired until[{}]. Lock daemon sleeping until[{}]...for {}ms",
                lock.expiresAt(),
                timeService.currentDatePlusMillis(sleepingTime),
                sleepingTime);
    }


}