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
package io.flamingock.support;

import io.flamingock.support.domain.AuditEntryExpectation;

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
 * .thenExpectAuditSequenceStrict(
 *     AuditEntryExpectation.APPLIED("change-1"),
 *     AuditEntryExpectation.APPLIED("change-2")
 * )
 * .verify();
 * }</pre>
 *
 * @see GivenStage#whenRun()
 * @see ThenStage
 * @see AuditEntryExpectation
 */
public interface WhenStage {

    /**
     * Defines a strict expectation for the audit entry sequence.
     *
     * <p><b>Strict validation</b> means:</p>
     * <ul>
     *   <li>The number of actual audit entries must exactly match the number of expectations</li>
     *   <li>The order of audit entries must exactly match the order of expectations</li>
     *   <li>Each audit entry is validated against its corresponding expectation</li>
     * </ul>
     *
     * <p>For each {@link AuditEntryExpectation}, only the fields explicitly set via
     * {@code withXxx()} methods are verified. The change ID and status are always verified.</p>
     *
     * @param expectations the expected audit entries in exact order
     * @return the {@link ThenStage} for chaining additional assertions or calling {@code verify()}
     * @see AuditEntryExpectation
     */
    ThenStage thenExpectAuditSequenceStrict(AuditEntryExpectation... expectations);

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
