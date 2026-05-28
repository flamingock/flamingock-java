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
package io.flamingock.internal.core.change.executable;

import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.change.loaded.AbstractReflectionLoadedChange;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers the {@code hasRollback()} contract and the defensive guard on {@code rollback()}
 * for code-based changes. The bug we're closing: when a non-transactional change without an
 * {@code @RollbackExecution} method failed, callers would invoke {@code rollback()} on a null
 * reflection method and surface as an NPE inside reflection. The guard converts that into a
 * meaningful {@code IllegalStateException} and {@code hasRollback()} lets callers avoid the
 * call entirely.
 */
class CodeExecutableChangeRollbackGuardTest {

    @Test
    @DisplayName("hasRollback() returns true when the rollback method reference is present")
    void hasRollbackTrueWhenMethodPresent() throws NoSuchMethodException {
        Method applyMethod = Sample.class.getMethod("apply");
        Method rollbackMethod = Sample.class.getMethod("rollback");

        CodeExecutableChange<AbstractReflectionLoadedChange> change = new CodeExecutableChange<>(
                "stage-1",
                mock(AbstractReflectionLoadedChange.class),
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod);

        assertTrue(change.hasRollback(), "rollback method present → hasRollback() must be true");
    }

    @Test
    @DisplayName("hasRollback() returns false when the rollback method reference is null")
    void hasRollbackFalseWhenMethodAbsent() throws NoSuchMethodException {
        Method applyMethod = Sample.class.getMethod("apply");

        CodeExecutableChange<AbstractReflectionLoadedChange> change = new CodeExecutableChange<>(
                "stage-1",
                mock(AbstractReflectionLoadedChange.class),
                ChangeAction.APPLY,
                applyMethod,
                null);

        assertFalse(change.hasRollback(), "no rollback method → hasRollback() must be false");
    }

    @Test
    @DisplayName("rollback() throws IllegalStateException (not NPE) when no rollback method is declared")
    void rollbackGuardThrowsIllegalStateWhenNoRollbackMethod() throws NoSuchMethodException {
        Method applyMethod = Sample.class.getMethod("apply");
        AbstractReflectionLoadedChange loaded = mock(AbstractReflectionLoadedChange.class);
        // The loaded change provides the change id used in the guard's error message.
        org.mockito.Mockito.when(loaded.getId()).thenReturn("change-no-rollback");

        CodeExecutableChange<AbstractReflectionLoadedChange> change = new CodeExecutableChange<>(
                "stage-1",
                loaded,
                ChangeAction.APPLY,
                applyMethod,
                null);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> change.rollback(mock(ExecutionRuntime.class)),
                "rollback() with no @RollbackExecution method must throw IllegalStateException, not NPE");

        assertTrue(thrown.getMessage().contains("change-no-rollback"),
                "error message should name the offending change: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("hasRollback"),
                "error message should point callers at hasRollback(): " + thrown.getMessage());
    }

    /** Sample type providing reachable apply/rollback methods. */
    @SuppressWarnings("unused")
    static class Sample {
        public void apply() { /* no-op */ }
        public void rollback() { /* no-op */ }
    }
}
