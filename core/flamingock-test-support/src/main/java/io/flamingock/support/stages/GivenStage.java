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
package io.flamingock.support.stages;

import io.flamingock.support.FlamingockTestSupport;
import io.flamingock.support.domain.AuditEntryDefinition;

/**
 * Represents the "Given" phase of the BDD test flow for setting up preconditions.
 *
 * <p>This stage allows you to define the initial audit state that should exist
 * before running the change runner. You can specify which changes should be
 * marked as already applied, failed, or rolled back in the audit store.</p>
 *
 * <p>All methods in this interface are intermediate operations that return {@code this}
 * to enable fluent method chaining. When setup is complete, call {@link #whenRun()}
 * to transition to the assertion phase.</p>
 *
 * <p>Note: This is a lazy API. No execution occurs until {@link ThenStage#verify()}
 * is called at the end of the chain.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * import static io.flamingock.support.domain.AuditEntryDefinition.*;
 *
 * FlamingockTestSupport
 *     .given(builder)
 *     .andExistingAudit(
 *         APPLIED(SetupChange.class),
 *         APPLIED(MigrationV1.class),
 *         FAILED(FailedChange.class)
 *     )
 *     .whenRun()
 *     // ... assertions
 * }</pre>
 *
 * @see FlamingockTestSupport#given(io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder)
 * @see WhenStage
 * @see AuditEntryDefinition
 */
public interface GivenStage {

    /**
     * Specifies audit entries that should exist in the audit store before running the change runner.
     *
     * <p>Use the static factory methods from {@link AuditEntryDefinition} to create definitions:</p>
     * <ul>
     *   <li>{@link AuditEntryDefinition#APPLIED(Class)} - Mark a change as successfully applied</li>
     *   <li>{@link AuditEntryDefinition#FAILED(Class)} - Mark a change as failed</li>
     *   <li>{@link AuditEntryDefinition#ROLLED_BACK(Class)} - Mark a change as rolled back</li>
     *   <li>{@link AuditEntryDefinition#ROLLBACK_FAILED(Class)} - Mark a change as rollback failed</li>
     * </ul>
     *
     * <p>String-based variants are also available for manual change ID specification.</p>
     *
     * @param definitions the audit entry definitions to pre-populate
     * @return this stage for method chaining
     * @see AuditEntryDefinition
     */
    GivenStage andExistingAudit(AuditEntryDefinition... definitions);

    /**
     * Completes the setup phase and transitions to the assertion phase.
     *
     * <p>This method finalizes the preconditions and returns a {@link WhenStage}
     * where you can define expectations about the audit entries that should
     * result from running the change runner.</p>
     *
     * <p>Note: The actual execution occurs when {@link ThenStage#verify()} is called.</p>
     *
     * @return the {@link WhenStage} for defining assertions
     */
    WhenStage whenRun();
}
