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
package io.flamingock.internal.core.change.navigation.step.afteraudit;

import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@code FailedAfterExecutionAuditStep.getRollbackStep()} returns an empty
 * {@link Optional} when the failed change doesn't declare a rollback method. This is the contract
 * that lets the navigator strategies skip the manual-rollback path (and its audit entry) instead
 * of NPE'ing on a null reflection method.
 */
class FailedAfterExecutionAuditStepRollbackOptionalTest {

    @Test
    @DisplayName("Optional.empty() when the failed change has no rollback method")
    void returnsEmptyWhenNoRollback() {
        ExecutableChange change = mock(ExecutableChange.class);
        when(change.hasRollback()).thenReturn(false);

        FailedAfterExecutionAuditStep step = FailedAfterExecutionAuditStep.fromFailedApply(
                change, new RuntimeException("apply failed"), Result.OK());

        Optional<RollableStep> rollback = step.getRollbackStep();
        assertFalse(rollback.isPresent(), "no rollback method → getRollbackStep() must be empty");
    }

    @Test
    @DisplayName("Optional.of(step) when the failed change does declare a rollback method")
    void returnsPresentWhenRollbackDeclared() {
        ExecutableChange change = mock(ExecutableChange.class);
        when(change.hasRollback()).thenReturn(true);

        FailedAfterExecutionAuditStep step = FailedAfterExecutionAuditStep.fromFailedApply(
                change, new RuntimeException("apply failed"), Result.OK());

        Optional<RollableStep> rollback = step.getRollbackStep();
        assertTrue(rollback.isPresent(), "rollback declared → getRollbackStep() must be present");
        assertSame(change, rollback.get().getChange(),
                "rollback step must wrap the failed change");
    }

    @Test
    @DisplayName("Empty Optional regardless of which FailedAfterExecutionAuditStep variant is built")
    void emptyAcrossVariants() {
        ExecutableChange change = mock(ExecutableChange.class);
        when(change.hasRollback()).thenReturn(false);

        // Variant 1: failed apply + successful audit
        FailedAfterExecutionAuditStep v1 = FailedAfterExecutionAuditStep.fromFailedApply(
                change, new RuntimeException("apply failed"), Result.OK());
        assertEquals(Optional.empty(), v1.getRollbackStep());

        // Variant 2: failed apply + failed audit
        FailedAfterExecutionAuditStep v2 = FailedAfterExecutionAuditStep.fromFailedApply(
                change, new RuntimeException("apply failed"),
                new Result.Error(new RuntimeException("audit failed")));
        assertEquals(Optional.empty(), v2.getRollbackStep());

        // Variant 3: successful apply + failed audit
        FailedAfterExecutionAuditStep v3 = FailedAfterExecutionAuditStep.fromSuccessApply(
                change, new Result.Error(new RuntimeException("audit failed")));
        assertEquals(Optional.empty(), v3.getRollbackStep());
    }
}
