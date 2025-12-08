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
 * FlamingockTestSupport
 *     .given(builder)
 *     .andAppliedChanges(SetupChange.class, MigrationV1.class)
 *     .andFailedChanges(FailedChange.class)
 *     .whenRun()
 *     // ... assertions
 * }</pre>
 *
 * @see FlamingockTestSupport#given(io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder)
 * @see WhenStage
 */
public interface GivenStage {

    /**
     * Specifies changes that should be marked as successfully applied in the audit store
     * before running the change runner.
     *
     * <p>These changes will be treated as if they were already executed successfully
     * in a previous run. The change runner will skip them during execution.</p>
     *
     * @param changes the change classes to mark as applied
     * @return this stage for method chaining
     */
    GivenStage andAppliedChanges(Class<?>... changes);

    /**
     * Specifies changes that should be marked as failed in the audit store
     * before running the change runner.
     *
     * <p>These changes will be treated as if they failed in a previous run.
     * Depending on the runner configuration, they may be retried or skipped.</p>
     *
     * @param changes the change classes to mark as failed
     * @return this stage for method chaining
     */
    GivenStage andFailedChanges(Class<?>... changes);

    /**
     * Specifies changes that should be marked as rolled back in the audit store
     * before running the change runner.
     *
     * <p>These changes will be treated as if they were executed but subsequently
     * rolled back in a previous run.</p>
     *
     * @param changes the change classes to mark as rolled back
     * @return this stage for method chaining
     */
    GivenStage andRolledBackChanges(Class<?>... changes);

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
