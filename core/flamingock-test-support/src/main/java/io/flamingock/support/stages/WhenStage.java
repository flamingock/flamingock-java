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
 * Represents the "When" phase of the BDD test flow for defining expectations.
 *
 * <p>This stage is returned by {@link GivenStage#whenRun()} and allows you to define
 * expectations about the audit entries that should be created when the change runner
 * executes, or exceptions that should be thrown.</p>
 *
 * <p>All methods in this interface are intermediate operations. No execution occurs
 * until {@link ThenStage#verify()} is called at the end of the chain.</p>
 *
 * <h2>Assertion Types</h2>
 * <ul>
 *   <li><b>Strict audit sequence</b>: Verifies exact count, order, and field values of audit entries</li>
 *   <li><b>Exception</b>: Verifies that a specific exception type was thrown</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * .whenRun()
 * .thenExpectAuditFinalStateSequence(
 *     AuditEntryDefinition.APPLIED("change-1"),
 *     AuditEntryDefinition.APPLIED("change-2")
 * )
 * .verify();
 * }</pre>
 *
 * @see GivenStage#whenRun()
 * @see ThenStage
 * @see AuditEntryDefinition
 */
public interface WhenStage {

    /**
     * Asserts that the final state sequence of audit entries matches the given definitions exactly.
     *
     * <p>This validates only <strong>final states</strong> (APPLIED, FAILED, ROLLED_BACK, ROLLBACK_FAILED),
     * filtering out intermediate states like STARTED. The actual audit log may contain multiple entries
     * per change (e.g., STARTED then APPLIED), but only the final outcome is validated.</p>
     *
     * <p><strong>Exact matching:</strong> The number of definitions must match the number of
     * final-state entries in the audit log. This is not a subset check.</p>
     *
     * <p><strong>Field validation:</strong> Only fields set in each definition are validated.
     * Use class-based factories (e.g., {@code APPLIED(MyChange.class)}) to auto-extract fields
     * from annotations, or string-based factories with {@code withXxx()} methods for fine-grained control.</p>
     *
     * @param definitions the expected audit entry definitions, in exact order
     * @return a ThenStage for adding additional expectations or calling verify()
     * @see AuditEntryDefinition
     */
    ThenStage thenExpectAuditFinalStateSequence(AuditEntryDefinition... definitions);

    /**
     * Defines an expectation that the execution should throw a specific exception.
     *
     * <p>Use this method when testing error scenarios where the change runner
     * is expected to fail with a particular exception type.</p>
     *
     * @param exceptionClass the expected exception type
     * @param validator      a consumer to perform additional assertions on the thrown exception;
     *                       may be {@code null} if no additional validation is needed
     * @return the {@link ThenStage} for chaining additional assertions or calling {@code verify()}
     */
    ThenStage thenExpectException(Class<? extends Throwable> exceptionClass, Consumer<Throwable> validator);
}
