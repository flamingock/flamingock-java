/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class ThreadSleeper {

    private static final Logger logger = LoggerFactory.getLogger(ThreadSleeper.class);


    private final long totalMaxTimeWaitingMillis;
    private final StopWatch stopWatch;
    private final Function<String, RuntimeException> exceptionThrower;

    public ThreadSleeper(long totalMaxTimeWaitingMillis,
                         Function<String, RuntimeException> exceptionThrower) {
        this.totalMaxTimeWaitingMillis = totalMaxTimeWaitingMillis;
        this.stopWatch = StopWatch.startAndGet();
        this.exceptionThrower = exceptionThrower;
    }

    /**
     * It checks if the threshold hasn't been reached. In that case it will decide if it waits the maximum allowed
     * (maxTimeAllowedToWait) or less, which it's restricted by totalMaxTimeWaitingMillis
     * @param maxTimeToWait Max time allowed to wait in this iteration.
     */
    public void checkThresholdAndWait(long maxTimeToWait) {
        if (stopWatch.hasReached(totalMaxTimeWaitingMillis)) {
            throwException("Maximum waiting millis reached: " + totalMaxTimeWaitingMillis);
        }
        if (maxTimeToWait > 0) {
            logger.info("Trying going to sleep for maximum {}ms", maxTimeToWait);
            waitForMillis(maxTimeToWait);
        } else {
            logger.info("Not going to sleep. Because max time to wait[{}] is less than zero", maxTimeToWait);
        }
    }

    private void waitForMillis(long maxAllowedTimeToWait) {
        try {
            long timeToSleep = maxAllowedTimeToWait;

            //How log until max Time waiting reached
            long remainingTime = getRemainingMillis();
            if (remainingTime <= 0) {
                throwException("Maximum waiting millis reached: " + totalMaxTimeWaitingMillis);
            }

            if (timeToSleep > remainingTime) {
                timeToSleep = remainingTime;
            }
            logger.info("Going to sleep finally for {}ms", timeToSleep);
            Thread.sleep(timeToSleep);
            logger.info("Woke up");
        } catch (InterruptedException ex) {
            logger.warn(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }
    }

    private void throwException(String cause) {
        throw exceptionThrower.apply(String.format(
                "Quit trying to acquire the lock after %d millis[ %s ]",
                stopWatch.getElapsed(),
                cause));
    }

    private long getRemainingMillis() {
        return totalMaxTimeWaitingMillis - stopWatch.getElapsed();
    }

}
