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
 * Marks the method that rolls back a change in case of failure or manual reversion.
 * This method should undo the operations performed by the corresponding {@link Apply} method.
 *
 * <p>Rollback methods are optional but recommended for production systems to ensure
 * safe change reversibility. They receive the same dependency injection as Apply methods.
 *
 * <p>Example usage:
 * <pre>{@code
 * &#64;Change(id = "migrate-user-schema", order = "2024-11-16-001", author = "ops-team")
 * public class MigrateUserSchema {
 *
 *     &#64;Apply
 *     public void migrateSchema(MongoDatabase db) {
 *         // Add new field and migrate data
 *         db.getCollection("users").updateMany(
 *             new Document(),
 *             Updates.set("createdAt", new Date())
 *         );
 *     }
 *
 *     &#64;Rollback
 *     public void revertSchema(MongoDatabase db) {
 *         // Remove the added field
 *         db.getCollection("users").updateMany(
 *             new Document(),
 *             Updates.unset("createdAt")
 *         );
 *     }
 * }
 * }</pre>
 *
 * @see Change
 * @see Apply
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Rollback {

}
