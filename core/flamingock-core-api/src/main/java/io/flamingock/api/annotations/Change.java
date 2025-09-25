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
 * Marks a class as a change that encapsulates a system evolution operation.
 * Each change represents an atomic, versioned modification to your distributed system.
 *
 * <p>Example usage:
 * <pre>{@code
 * &#64;Change(id = "create-user-index", order = "2024-11-15-001", author = "john.doe")
 * public class CreateUserIndexChange {
 *     &#64;Apply
 *     public void createIndex(MongoDatabase db) {
 *         // Implementation
 *     }
 * }
 * }</pre>
 *
 * @see Apply
 * @see Rollback
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Change {

    /**
     * Unique identifier for this change. Must be globally unique across all changes.
     * Typically, follows a kebab-case naming convention describing the operation.
     *
     * @return the unique change identifier
     */
    String id();

    /**
     * Execution order for this change. Changes are applied in lexicographical order.
     * Minimum 4 characters required. Recommended format: date-based with index (e.g., "2024-05-19-001").
     * This format provides optimal sorting, clarity, and sequential indexing within the same day.
     * Alternative formats like zero-padded numbers ("0001", "0002") are also supported.
     *
     * @return the execution order string
     */
    String order() default "NULL_VALUE";

    /**
     * Author of this change. Required for audit trail and accountability.
     * Typically, an email, username, or team identifier.
     *
     * @return the change author identifier
     */
    String author();

    /**
     * Whether this change should run within a transaction if supported by the target system.
     * Set to {@code false} for operations that cannot be transactional (e.g., DDL in some databases).
     *
     * @return {@code true} if transactional execution is required, {@code false} otherwise
     */
    boolean transactional() default true;

}
