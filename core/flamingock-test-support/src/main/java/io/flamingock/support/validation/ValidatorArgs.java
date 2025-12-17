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
package io.flamingock.support.validation;

/**
 * Marker interface for validator argument carriers used in the BDD test framework.
 *
 * <p>This interface serves as a deferred construction pattern for validators. Instead of
 * creating validators immediately when defining expectations in the "Then" stage, implementations
 * of this interface carry the necessary arguments until the {@link io.flamingock.support.stages.ThenStage#verify()} method
 * is called. This allows validators to be constructed with access to the {@link io.flamingock.support.context.TestContext},
 * particularly the {@link io.flamingock.internal.common.core.audit.AuditReader}.</p>
 *
 * <h2>Purpose</h2>
 * <p>The deferred construction is necessary because:</p>
 * <ul>
 *   <li>The audit reader is only available after the test context is fully initialized</li>
 *   <li>Validators need access to actual audit entries, which are only available after execution</li>
 *   <li>The BDD API is lazy - expectations are defined but not executed until {@code verify()}</li>
 * </ul>
 *
 * <h2>Implementation</h2>
 * <p>Implementations are typically static inner classes within their corresponding validators:</p>
 * <pre>{@code
 * public class AuditSequenceStrictValidator implements SimpleValidator {
 *     // ... validator implementation
 *
 *     public static class Args implements ValidatorArgs {
 *         private final List<AuditEntryDefinition> expectations;
 *
 *         public Args(List<AuditEntryDefinition> expectations) {
 *             this.expectations = expectations;
 *         }
 *
 *         public List<AuditEntryDefinition> getExpectations() {
 *             return expectations;
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Usage in Framework</h2>
 * <p>The framework uses these argument carriers as follows:</p>
 * <ol>
 *   <li>User calls expectation method (e.g., {@code thenExpectAuditSequenceStrict(...)})</li>
 *   <li>Stage creates an {@code Args} instance and stores it in the validators list</li>
 *   <li>When {@code verify()} is called, the {@link ValidationHandler} constructs actual
 *       validators using {@link ValidatorFactory#getValidator(ValidatorArgs)}</li>
 *   <li>Validators are executed with access to the test context and audit reader</li>
 * </ol>
 *
 * @see ValidatorFactory#getValidator(ValidatorArgs)
 * @see ValidationHandler
 * @see io.flamingock.support.stages.ThenStage#verify()
 */
public interface ValidatorArgs {
}


