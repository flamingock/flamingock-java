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
package io.flamingock.internal.core.pipeline.run;

import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.StageState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageStateTest {

    @Test
    void notStartedIsNotFailedAndHasNoErrorInfo() {
        assertFalse(StageState.NOT_STARTED.isFailed());
        assertFalse(StageState.NOT_STARTED.getErrorInfo().isPresent());
    }

    @Test
    void startedIsNotFailedAndHasNoErrorInfo() {
        assertFalse(StageState.STARTED.isFailed());
        assertFalse(StageState.STARTED.getErrorInfo().isPresent());
    }

    @Test
    void completedIsNotFailedAndHasNoErrorInfo() {
        assertFalse(StageState.COMPLETED.isFailed());
        assertFalse(StageState.COMPLETED.getErrorInfo().isPresent());
    }

    @Test
    void failedCarriesItsErrorInfo() {
        ErrorInfo info = new ErrorInfo("RuntimeException", "boom", java.util.Collections.singletonList("change-1"), "stage-1");
        StageState state = StageState.failed(info);

        assertTrue(state.isFailed());
        assertTrue(state.getErrorInfo().isPresent());
        assertSame(info, state.getErrorInfo().get());
    }

    @Test
    void blockedForMICarriesSynthesizedErrorInfoAndRecoveryIssues() {
        List<RecoveryIssue> issues = Arrays.asList(new RecoveryIssue("c1"), new RecoveryIssue("c2"));
        StageState state = StageState.blockedManualIntervention("stage-1", issues);

        assertTrue(state.isBlockedForManualIntervention());
        assertTrue(state.isFailed());   // inherited from Failed

        // ErrorInfo is now synthesized at construction (inherited surface from Failed).
        assertTrue(state.getErrorInfo().isPresent());
        ErrorInfo info = state.getErrorInfo().get();
        assertEquals("MANUAL_INTERVENTION_REQUIRED", info.getErrorType());
        assertEquals("stage-1", info.getStageId());
        // Change IDs are now structured in the ErrorInfo.changeIds list.
        assertEquals(Arrays.asList("c1", "c2"), info.getChangeIds());

        // Recovery issues are still available for callers that want the richer detail
        // (today equivalent to changeIds, but the type carries more shape for future evolution).
        assertEquals(2, state.getRecoveryIssues().size());
        assertEquals("c1", state.getRecoveryIssues().get(0).getChangeId());
        assertEquals("c2", state.getRecoveryIssues().get(1).getChangeId());
    }

    @Test
    void nonBlockedStatesAreNotBlockedForMI() {
        assertFalse(StageState.NOT_STARTED.isBlockedForManualIntervention());
        assertFalse(StageState.STARTED.isBlockedForManualIntervention());
        assertFalse(StageState.COMPLETED.isBlockedForManualIntervention());
        assertFalse(StageState.failed(new ErrorInfo("E", "m", java.util.Collections.singletonList("c"), "s"))
                .isBlockedForManualIntervention());
    }
}
