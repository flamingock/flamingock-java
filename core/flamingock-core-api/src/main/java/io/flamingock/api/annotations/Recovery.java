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
package io.flamingock.api.annotations;

import io.flamingock.api.RecoveryStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures the recovery behavior for a change when execution fails.
 * Determines whether Flamingock should automatically retry or require manual intervention.
 *
 * <p>Recovery strategies help maintain system consistency during failures by defining
 * clear policies for handling transient vs. permanent errors.
 *
 * <p>Example usage:
 * <pre>{@code
 * &#64;Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
 * &#64;Change(id = "populate-cache", order = "2024-11-18-001", author = "cache-team")
 * public class PopulateCacheChange {
 *     &#64;Apply
 *     public void populateCache(CacheManager cache, ExternalAPI api) {
 *         // Might fail due to transient network issues - safe to retry
 *         cache.put("data", api.fetchData());
 *     }
 * }
 *
 * &#64;Recovery(strategy = RecoveryStrategy.MANUAL_INTERVENTION)
 * &#64;Change(id = "critical-data-migration", order = "2024-11-18-002", author = "data-team")
 * public class CriticalDataMigration {
 *     &#64;Apply
 *     public void migrateData(Database db) {
 *         // Complex migration requiring human verification on failure
 *         db.executeMigration();
 *     }
 * }
 * }</pre>
 *
 * @see Change
 * @see RecoveryStrategy
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Recovery {

    /**
     * The recovery strategy to apply when this change fails.
     * Defaults to {@link RecoveryStrategy#MANUAL_INTERVENTION} for safety.
     *
     * @return the recovery strategy
     */
    RecoveryStrategy strategy() default RecoveryStrategy.MANUAL_INTERVENTION;

}