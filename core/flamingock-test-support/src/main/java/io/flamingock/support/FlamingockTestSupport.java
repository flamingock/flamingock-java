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


import io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder;
import io.flamingock.internal.core.builder.BuilderAccessor;
import io.flamingock.support.stages.GivenStage;
import io.flamingock.support.stages.GivenStageImpl;
import io.flamingock.support.stages.ThenStage;
import io.flamingock.support.stages.WhenStage;

/**
 * Entry point for the Flamingock BDD-style test support framework.
 *
 * <p>This class provides a fluent API for testing Flamingock change executions using the
 * Given-When-Then pattern. It allows you to set up preconditions (existing audit state),
 * define expectations, and verify the resulting audit entries.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * import static io.flamingock.support.domain.AuditEntryDefinition.*;
 *
 * FlamingockTestSupport
 *     .given(builder)
 *     .andExistingAudit(
 *         APPLIED(InitialSetupChange.class),
 *         APPLIED(SchemaV1Change.class),
 *         FAILED(FailedMigrationChange.class)
 *     )
 *     .whenRun()
 *     .thenExpectAuditSequenceStrict(
 *         APPLIED(SchemaV2Change.class)
 *             .withAuthor("dev-team"),
 *         APPLIED(DataMigrationChange.class)
 *     )
 *     .verify();
 * }</pre>
 *
 * <h2>Test Flow</h2>
 * <ol>
 *   <li><b>Given</b> ({@link GivenStage}): Define the initial audit state (preconditions)</li>
 *   <li><b>When</b> ({@link WhenStage}): Define expectations for when the runner executes</li>
 *   <li><b>Then</b> ({@link ThenStage}): Chain additional expectations</li>
 *   <li><b>Verify</b> ({@link ThenStage#verify()}): Execute the runner and validate expectations</li>
 * </ol>
 *
 * <p><b>Note:</b> This is a lazy API similar to Java Streams. All methods except
 * {@link ThenStage#verify()} are intermediate operations. The actual execution
 * and verification only occur when {@code verify()} is called.</p>
 *
 * @see GivenStage
 * @see WhenStage
 * @see ThenStage
 * @see io.flamingock.support.domain.AuditEntryDefinition
 */
public final class FlamingockTestSupport {

    private FlamingockTestSupport() {
    }

    /**
     * Starts the test setup phase with the provided Flamingock builder.
     *
     * <p>This method initializes the BDD test flow by accepting a configured
     * {@link AbstractChangeRunnerBuilder}. The builder should be fully configured
     * with the pipeline, audit store, and any other required settings before
     * being passed to this method.</p>
     *
     * @param builder the configured change runner builder to test; must not be {@code null}
     * @return the {@link GivenStage} for defining initial audit state preconditions
     * @throws NullPointerException if {@code builder} is {@code null}
     */
    public static GivenStage given(AbstractChangeRunnerBuilder<?, ?> builder) {
        if (builder == null) {
            throw new NullPointerException("builder must not be null");
        }
        BuilderAccessor builderAccessor = new BuilderAccessor(builder);
        return new GivenStageImpl(new BuilderAccessor(builder));
    }
}
