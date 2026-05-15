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
package io.flamingock.internal.core.plan;

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionPlanTest {

    @Test
    @DisplayName("ABORT plan should not require execution")
    void abortPlanShouldNotRequireExecution() {
        ExecutionPlan plan = ExecutionPlan.ABORT(Collections.singletonList(
                stageWith(mockChange("change-1", ChangeAction.APPLY))
        ));
        assertFalse(plan.isExecutionRequired());
    }

    @Test
    @DisplayName("ABORT plan should be marked as aborted")
    void abortPlanShouldBeAborted() {
        ExecutionPlan plan = ExecutionPlan.ABORT(Collections.emptyList());
        assertTrue(plan.isAborted());
    }

    @Test
    @DisplayName("ABORT plan should throw generic FlamingockException on validate")
    void abortPlanShouldThrowFlamingockExceptionOnValidate() {
        ExecutionPlan plan = ExecutionPlan.ABORT(Collections.singletonList(
                stageWith(mockChange("change-1", ChangeAction.APPLY))
        ));
        FlamingockException ex = assertThrows(FlamingockException.class, plan::validate);
        assertEquals("Execution aborted by the execution planner", ex.getMessage());
    }

    @Test
    @DisplayName("newExecution plan should not be aborted")
    void newExecutionPlanShouldNotBeAborted() {
        ExecutionPlan plan = ExecutionPlan.newExecution("exec-1", null,
                Collections.singletonList(stageWith(mockChange("c1", ChangeAction.APPLY))));
        assertFalse(plan.isAborted());
    }

    @Test
    @DisplayName("CONTINUE plan should not be aborted")
    void continuePlanShouldNotBeAborted() {
        ExecutionPlan plan = ExecutionPlan.CONTINUE(Collections.emptyList());
        assertFalse(plan.isAborted());
    }

    private static ExecutableStage stageWith(ExecutableChange... changes) {
        return new ExecutableStage("test-stage", Arrays.asList(changes));
    }

    private static ExecutableChange mockChange(String id, ChangeAction action) {
        ExecutableChange change = mock(ExecutableChange.class);
        when(change.getId()).thenReturn(id);
        when(change.getAction()).thenReturn(action);
        when(change.isAlreadyApplied()).thenReturn(action == ChangeAction.SKIP);
        return change;
    }
}
