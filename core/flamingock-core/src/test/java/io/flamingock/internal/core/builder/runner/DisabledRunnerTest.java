/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.builder.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DisabledRunner}. The runner is returned by
 * {@code AbstractChangeRunnerBuilder.build()} when {@code flamingock.enabled=false} and the
 * build is not in CLI mode; its sole contract is to log a single line and exit without side
 * effects.
 */
class DisabledRunnerTest {

    @Test
    @DisplayName("run() returns cleanly without throwing")
    void runDoesNotThrow() {
        DisabledRunner runner = new DisabledRunner();
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) runner::run);
    }

    @Test
    @DisplayName("run() is repeatable (idempotent no-op)")
    void runIsIdempotent() {
        DisabledRunner runner = new DisabledRunner();
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) runner::run);
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) runner::run);
    }

    @Test
    @DisplayName("DisabledRunner is a Runner")
    void isARunner() {
        assertTrue(new DisabledRunner() instanceof Runner,
                "DisabledRunner must implement the Runner contract so AbstractChangeRunnerBuilder.build() can return it");
    }
}
