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
package io.flamingock.internal.core.configuration.core;

import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.preview.PreviewPipeline;

import java.util.HashMap;
import java.util.Map;

import static io.flamingock.internal.util.Constants.DEFAULT_LOCK_ACQUIRED_FOR_MILLIS;
import static io.flamingock.internal.util.Constants.DEFAULT_QUIT_TRYING_AFTER_MILLIS;
import static io.flamingock.internal.util.Constants.DEFAULT_TRY_FREQUENCY_MILLIS;

public class CoreConfiguration implements CoreConfigurable {

    private final LockConfiguration lockConfiguration = new LockConfiguration();


    /**
     * If false, will disable Flamingock. Default true
     */
    private boolean enabled = true;

    /**
     * Service identifier.
     */
    private String serviceIdentifier = null;

    /**
     * Map for custom data you want to attach to your change
     */
    private Map<String, Object> metadata = new HashMap<>();

    public LockConfiguration getLockConfiguration() {
        return lockConfiguration;
    }

    @Override
    public PreviewPipeline getPreviewPipeline() {
        return Deserializer.readPreviewPipelineFromFile();
    }


    @Override
    public void setLockAcquiredForMillis(long lockAcquiredForMillis) {
        lockConfiguration.setLockAcquiredForMillis(lockAcquiredForMillis);
    }

    @Override
    public void setLockTryFrequencyMillis(long lockTryFrequencyMillis) {
        lockConfiguration.setLockTryFrequencyMillis(lockTryFrequencyMillis);
    }

    @Override
    public void setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
        lockConfiguration.setThrowExceptionIfCannotObtainLock(throwExceptionIfCannotObtainLock);
    }

    @Override
    public void setLockQuitTryingAfterMillis(long lockQuitTryingAfterMillis) {
        lockConfiguration.setLockQuitTryingAfterMillis(lockQuitTryingAfterMillis);
    }


    @Override
    public void setEnableRefreshDaemon(boolean enableRefreshDaemon) {
        lockConfiguration.setEnableRefreshDaemon(enableRefreshDaemon);
    }

    @Override
    public boolean isEnableRefreshDaemon() {
        return lockConfiguration.isEnableRefreshDaemon();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void setServiceIdentifier(String serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
    }

    @Override
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public long getLockAcquiredForMillis() {
        return lockConfiguration.getLockAcquiredForMillis();
    }

    @Override
    public Long getLockQuitTryingAfterMillis() {
        return lockConfiguration.getLockQuitTryingAfterMillis();
    }

    @Override
    public long getLockTryFrequencyMillis() {
        return lockConfiguration.getLockTryFrequencyMillis();
    }

    @Override
    public boolean isThrowExceptionIfCannotObtainLock() {
        return lockConfiguration.isThrowExceptionIfCannotObtainLock();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getServiceIdentifier() {
        return serviceIdentifier;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static class LockConfiguration {

        /**
         * The period the lock will be reserved once acquired.
         * If it finishes before, it will release it earlier.
         * If the process takes longer thant this period, it will be automatically extended.
         * Default 1 minute.
         * Minimum 3 seconds.
         */
        private long lockAcquiredForMillis = DEFAULT_LOCK_ACQUIRED_FOR_MILLIS;

        /**
         * The time after what Flamingock will quit trying to acquire the lock in case it's acquired by another process.
         * Default 3 minutes.
         * Minimum 0, which means won't wait whatsoever.
         */
        private long lockQuitTryingAfterMillis = DEFAULT_QUIT_TRYING_AFTER_MILLIS;

        /**
         * In case the lock is held by another process, it indicates the frequency to try to acquire it.
         * Regardless of this value, the longest Flamingock will wait if until the current lock's expiration.
         * Default 1 second.
         * Minimum 500 millis.
         */
        private long lockTryFrequencyMillis = DEFAULT_TRY_FREQUENCY_MILLIS;

        /**
         * Flamingock will throw FlamingockException if lock can not be obtained. Default true
         */
        private boolean throwExceptionIfCannotObtainLock = true;

        /**
         * Flamingock will run a daemon thread in charge of refreshing the lock
         */
        private boolean enableRefreshDaemon = true;


        public void setLockAcquiredForMillis(long lockAcquiredForMillis) {
            this.lockAcquiredForMillis = lockAcquiredForMillis;
        }

        public void setLockQuitTryingAfterMillis(long lockQuitTryingAfterMillis) {
            this.lockQuitTryingAfterMillis = lockQuitTryingAfterMillis;
        }

        public void setLockTryFrequencyMillis(long lockTryFrequencyMillis) {
            this.lockTryFrequencyMillis = lockTryFrequencyMillis;
        }

        public void setThrowExceptionIfCannotObtainLock(boolean throwExceptionIfCannotObtainLock) {
            this.throwExceptionIfCannotObtainLock = throwExceptionIfCannotObtainLock;
        }

        public void setEnableRefreshDaemon(boolean enableRefreshDaemon) {
            this.enableRefreshDaemon = enableRefreshDaemon;
        }

        public long getLockAcquiredForMillis() {
            return lockAcquiredForMillis;
        }

        public Long getLockQuitTryingAfterMillis() {
            return lockQuitTryingAfterMillis;
        }

        public long getLockTryFrequencyMillis() {
            return lockTryFrequencyMillis;
        }

        public boolean isThrowExceptionIfCannotObtainLock() {
            return throwExceptionIfCannotObtainLock;
        }

        public boolean isEnableRefreshDaemon() {
            return enableRefreshDaemon;
        }
    }

}
