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
package io.flamingock.api;

/**
 * Defines how Flamingock handles change execution failures.
 * Determines whether failed changes should be automatically retried
 * or require manual intervention before proceeding.
 *
 * @see io.flamingock.api.annotations.Recovery
 * @see io.flamingock.api.annotations.Change
 */
public enum RecoveryStrategy {
    /**
     * Automatically retry the change on subsequent runs until successful.
     * Use for idempotent operations with transient failure modes.
     */
    ALWAYS_RETRY,

    /**
     * Require manual intervention before retrying.
     * Use for critical operations where failures need investigation.
     */
    MANUAL_INTERVENTION;

    /**
     * Checks if this strategy allows automatic retries.
     *
     * @return {@code true} if automatic retry is enabled
     */
    public boolean isAlwaysRetry() {
        return this == ALWAYS_RETRY;
    }
}