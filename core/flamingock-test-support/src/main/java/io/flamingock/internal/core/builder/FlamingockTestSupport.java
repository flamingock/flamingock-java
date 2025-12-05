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
package io.flamingock.internal.core.builder;

import io.flamingock.internal.core.builder.change.AbstractChangeRunnerBuilder;

/**
 * Public entry point for the BDD-style test support.
 * Usage:
 *   FlamingockTestSupport.given(builder).andAppliedChanges(...).whenRun()...
 */
public final class FlamingockTestSupport {

    private FlamingockTestSupport() {
    }

    /**
     * Starts the Given stage with the provided builder.
     * Returns the GivenStage interface (implementation present in the module).
     *
     * @param builder the change runner builder under test (must not be null)
     * @return GivenStage entry for fluent BDD flow
     * @throws NullPointerException if builder is null
     */
    public static GivenStage given(AbstractChangeRunnerBuilder<?, ?> builder) {
        if (builder == null) {
            throw new NullPointerException("builder must not be null");
        }
        return new GivenStageImpl(builder);
    }
}
