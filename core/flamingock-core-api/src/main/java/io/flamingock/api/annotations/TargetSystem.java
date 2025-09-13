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
package io.flamingock.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Associates a change class with a specific target system or subsystem.
 * Use this when managing multiple systems (databases, message queues, etc.) within the same pipeline.
 *
 * <p>Target systems allow fine-grained control over which changes apply to which components
 * of your distributed architecture, enabling selective execution and rollback.
 *
 * <p>Example usage:
 * <pre>{@code
 * &#64;TargetSystem(id = "user-database")
 * &#64;Change(id = "add-user-preferences", order = "2024-11-17-001", author = "backend-team")
 * public class AddUserPreferences {
 *     &#64;Apply
 *     public void addPreferencesTable(Connection conn) {
 *         // Create preferences table in user database
 *     }
 * }
 *
 * &#64;TargetSystem(id = "analytics-database")
 * &#64;Change(id = "create-metrics-view", order = "2024-11-17-002", author = "analytics-team")
 * public class CreateMetricsView {
 *     &#64;Apply
 *     public void createView(Connection conn) {
 *         // Create materialized view in analytics database
 *     }
 * }
 * }</pre>
 *
 * @see Change
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TargetSystem {

    /**
     * Identifier for the target system this change applies to.
     * Must match a system configured in your Flamingock setup.
     *
     * @return the target system identifier
     */
    String id();
}