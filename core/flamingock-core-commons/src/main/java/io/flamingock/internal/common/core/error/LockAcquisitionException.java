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
package io.flamingock.internal.common.core.error;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Exception thrown when process lock acquisition or management fails.
 * 
 * <p>This exception provides detailed context about lock failures including:
 * <ul>
 *   <li>Lock key and current ownership information</li>
 *   <li>Timeout and retry attempt details</li>
 *   <li>Lock expiration and refresh status</li>
 *   <li>Competing process information</li>
 *   <li>Daemon and refresh operation context</li>
 * </ul>
 * 
 * <p>This exception should be used for all lock-related failures including acquisition
 * timeouts, refresh failures, and lock release issues.
 * 
 * @since 6.0.0
 */
public class LockAcquisitionException extends FlamingockException {

    public enum LockFailureType {
        ACQUISITION_TIMEOUT,
        ALREADY_OWNED,
        REFRESH_FAILED,
        RELEASE_FAILED,
        EXPIRED,
        DAEMON_FAILURE
    }

    private final LockFailureType failureType;
    private final String lockKey;
    private final String currentOwner;
    private final String requestingOwner;
    private final Duration attemptDuration;
    private final Duration configuredTimeout;
    private final int retryAttempts;
    private final LocalDateTime lockExpiration;

    /**
     * Creates a new LockAcquisitionException with comprehensive lock context.
     *
     * @param message descriptive error message
     * @param failureType the specific type of lock failure
     * @param lockKey the key/identifier of the lock
     * @param currentOwner who currently owns the lock (if known)
     * @param requestingOwner who was trying to acquire the lock
     * @param attemptDuration how long the acquisition attempt took
     * @param configuredTimeout the configured lock timeout
     * @param retryAttempts number of retry attempts made
     * @param lockExpiration when the current lock expires (if applicable)
     * @param cause the underlying exception that caused the failure
     */
    public LockAcquisitionException(String message,
                                   LockFailureType failureType,
                                   String lockKey,
                                   String currentOwner,
                                   String requestingOwner,
                                   Duration attemptDuration,
                                   Duration configuredTimeout,
                                   int retryAttempts,
                                   LocalDateTime lockExpiration,
                                   Throwable cause) {
        super(buildLockMessage(message, failureType, lockKey, currentOwner, requestingOwner,
                             attemptDuration, configuredTimeout, retryAttempts, lockExpiration), cause);
        this.failureType = failureType;
        this.lockKey = lockKey;
        this.currentOwner = currentOwner;
        this.requestingOwner = requestingOwner;
        this.attemptDuration = attemptDuration;
        this.configuredTimeout = configuredTimeout;
        this.retryAttempts = retryAttempts;
        this.lockExpiration = lockExpiration;
    }

    /**
     * Creates a LockAcquisitionException with minimal context (for backward compatibility).
     *
     * @param message descriptive error message
     * @param failureType the type of lock failure
     * @param cause the underlying exception
     */
    public LockAcquisitionException(String message, LockFailureType failureType, Throwable cause) {
        this(message, failureType, null, null, null, null, null, 0, null, cause);
    }

    /**
     * Factory method for lock acquisition timeouts.
     *
     * @param lockKey the lock that couldn't be acquired
     * @param currentOwner who currently owns the lock
     * @param attemptDuration how long we tried
     * @param configuredTimeout the configured timeout
     * @param retryAttempts number of retries attempted
     */
    public static LockAcquisitionException timeout(String lockKey, String currentOwner, 
                                                 Duration attemptDuration, Duration configuredTimeout, 
                                                 int retryAttempts) {
        String message = String.format("Failed to acquire lock '%s' within timeout", lockKey);
        return new LockAcquisitionException(message, LockFailureType.ACQUISITION_TIMEOUT, lockKey,
                                          currentOwner, null, attemptDuration, configuredTimeout,
                                          retryAttempts, null, null);
    }

    /**
     * Factory method for lock refresh failures.
     *
     * @param lockKey the lock that couldn't be refreshed
     * @param lockExpiration when the lock was set to expire
     * @param cause the underlying exception
     */
    public static LockAcquisitionException refreshFailed(String lockKey, LocalDateTime lockExpiration, Throwable cause) {
        String message = String.format("Failed to refresh lock '%s'", lockKey);
        return new LockAcquisitionException(message, LockFailureType.REFRESH_FAILED, lockKey,
                                          null, null, null, null, 0, lockExpiration, cause);
    }

    private static String buildLockMessage(String message,
                                         LockFailureType failureType,
                                         String lockKey,
                                         String currentOwner,
                                         String requestingOwner,
                                         Duration attemptDuration,
                                         Duration configuredTimeout,
                                         int retryAttempts,
                                         LocalDateTime lockExpiration) {
        StringBuilder contextMessage = new StringBuilder(message);
        
        if (failureType != null) {
            contextMessage.append("\n  Failure Type: ").append(failureType);
        }
        if (lockKey != null) {
            contextMessage.append("\n  Lock Key: ").append(lockKey);
        }
        if (currentOwner != null) {
            contextMessage.append("\n  Current Owner: ").append(currentOwner);
        }
        if (requestingOwner != null) {
            contextMessage.append("\n  Requesting Owner: ").append(requestingOwner);
        }
        if (attemptDuration != null) {
            contextMessage.append("\n  Attempt Duration: ").append(formatDuration(attemptDuration));
        }
        if (configuredTimeout != null) {
            contextMessage.append("\n  Configured Timeout: ").append(formatDuration(configuredTimeout));
        }
        if (retryAttempts > 0) {
            contextMessage.append("\n  Retry Attempts: ").append(retryAttempts);
        }
        if (lockExpiration != null) {
            contextMessage.append("\n  Lock Expires: ").append(lockExpiration);
        }
        
        return contextMessage.toString();
    }

    private static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%.1fm", millis / 60000.0);
        }
    }

    // Getters for programmatic access
    public LockFailureType getFailureType() { return failureType; }
    public String getLockKey() { return lockKey; }
    public String getCurrentOwner() { return currentOwner; }
    public String getRequestingOwner() { return requestingOwner; }
    public Duration getAttemptDuration() { return attemptDuration; }
    public Duration getConfiguredTimeout() { return configuredTimeout; }
    public int getRetryAttempts() { return retryAttempts; }
    public LocalDateTime getLockExpiration() { return lockExpiration; }
}