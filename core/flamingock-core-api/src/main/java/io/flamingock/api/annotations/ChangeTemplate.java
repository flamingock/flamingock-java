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

import io.flamingock.api.template.AbstractChangeTemplate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Flamingock change template and configures its execution mode.
 *
 * <p>All template classes must extend {@link AbstractChangeTemplate} and be annotated with
 * this annotation to specify their unique identifier and whether they process single or multiple steps.
 *
 * <p><b>Simple templates</b> (default, {@code multiStep = false}):
 * <pre>
 * id: create-users-table
 * template: sql  # Uses the template id
 * apply: "CREATE TABLE users (id INT PRIMARY KEY)"
 * rollback: "DROP TABLE users"
 * </pre>
 *
 * <p><b>Steppable templates</b> ({@code multiStep = true}) process multiple operations:
 * <pre>
 * id: setup-orders
 * template: mongo  # Uses the template id
 * steps:
 *   - apply: { type: createCollection, collection: orders }
 *     rollback: { type: dropCollection, collection: orders }
 *   - apply: { type: insert, collection: orders, ... }
 *     rollback: { type: delete, collection: orders, ... }
 * </pre>
 *
 * <p><b>Steppable rollback behavior:</b>
 * <ul>
 *   <li>On failure, previously successful steps are rolled back in reverse order</li>
 *   <li>Steps without rollback are skipped during rollback</li>
 * </ul>
 *
 * @see AbstractChangeTemplate
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChangeTemplate {

    /**
     * Unique identifier for the template. Used in YAML files to reference
     * the template (e.g., {@code template: "sql"}).
     *
     * <p>This is a mandatory field - all templates must have a unique identifier.
     * The id should be short, descriptive, and use lowercase with hyphens
     * (e.g., "sql", "mongodb", "dynamodb").
     *
     * @return the unique template identifier
     */
    String id();

    /**
     * When {@code true}, the template expects a {@code steps} array in YAML.
     * When {@code false} (default), it expects {@code apply} and optional {@code rollback} at root.
     *
     * @return {@code true} for steppable templates, {@code false} for simple templates
     */
    boolean multiStep() default false;
}
