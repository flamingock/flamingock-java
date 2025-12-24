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

import io.flamingock.support.domain.AuditEntryDefinition;

import java.util.function.Consumer;

/**
 * Represents the "Then" phase of the BDD test flow for chaining assertions
 * and triggering final execution and verification.
 *
 * <p>This stage allows you to add additional expectations after the initial assertion
 * defined in {@link WhenStage}. The {@code andExpectXxx()} methods are intermediate
 * operations that accumulate expectations.</p>
 *
 * <p>The {@link #verify()} method is the <b>terminal operation</b> that:</p>
 * <ol>
 *   <li>Executes the change runner with the configured preconditions</li>
 *   <li>Validates all accumulated expectations against the actual results</li>
 *   <li>Throws an assertion error if any expectation is not met</li>
 * </ol>
 *
 * <h2>Example with Multiple Assertions</h2>
 * <pre>{@code
 * .whenRun()
 * .thenExpectAuditFinalStateSequence(
 *     AuditEntryDefinition.APPLIED("change-1")
 * )
 * .andExpectAuditFinalStateSequence(
 *     AuditEntryDefinition.APPLIED("change-2")
 * )
 * .verify();  // Execution and verification happen here
 * }</pre>
 *
 * @see WhenStage
 * @see AuditEntryDefinition
 */
public interface ThenStage {

    /**
     * Adds an additional expectation that the final state sequence of audit entries matches
     * the given definitions exactly.
     *
     * <p>This validates only <strong>final states</strong> (APPLIED, FAILED, ROLLED_BACK, ROLLBACK_FAILED),
     * filtering out intermediate states like STARTED.</p>
     *
     * <p><strong>Exact matching:</strong> The number of definitions must match the number of
     * final-state entries in the audit log. This is not a subset check.</p>
     *
     * <p><strong>Field validation:</strong> Only fields set in each definition are validated.
     * See {@link AuditEntryDefinition} for details on class-based vs string-based factories.</p>
     *
     * @param definitions the expected audit entry definitions, in exact order
     * @return this stage for method chaining
     * @see AuditEntryDefinition
     */
    ThenStage andExpectAuditFinalStateSequence(AuditEntryDefinition... definitions);

    /**
     * Adds an expectation that the execution should throw a specific exception.
     *
     * <p>This method has the same semantics as
     * {@link WhenStage#thenExpectException(Class, Consumer)}
     * but allows chaining after other assertions.</p>
     *
     * @param exceptionClass the expected exception type
     * @param exceptionConsumer      a consumer to perform additional assertions on the thrown exception;
     *                       may be {@code null} if no additional validation is needed
     * @return this stage for method chaining
     */
    ThenStage andExpectException(Class<? extends Throwable> exceptionClass, Consumer<Throwable> exceptionConsumer);

    /**
     * Terminal operation that executes the change runner and verifies all expectations.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Sets up the audit store with the preconditions defined in {@link GivenStage}</li>
     *   <li>Executes the Flamingock change runner</li>
     *   <li>Validates all accumulated expectations against the actual audit entries</li>
     * </ol>
     *
     * <p>This is the only method in the fluent chain that triggers actual execution.
     * All preceding methods ({@code given}, {@code andExistingAudit}, {@code whenRun},
     * {@code thenExpect...}) are intermediate operations that only configure the test.</p>
     *
     * @throws AssertionError if any expectation is not met
     */
    void verify();
}
