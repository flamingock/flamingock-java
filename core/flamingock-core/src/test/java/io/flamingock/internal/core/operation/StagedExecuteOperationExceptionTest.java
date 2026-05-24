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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagedExecuteOperationExceptionTest {

    @Test
    void getMessageIsSingleLineAndContainsFailedStageNames() {
        ExecuteResponseData result = failureResult();
        StagedExecuteOperationException ex = new StagedExecuteOperationException(result);

        String msg = ex.getMessage();
        assertFalse(msg.contains(System.lineSeparator()), "getMessage must remain single-line: " + msg);
        assertTrue(msg.startsWith("Flamingock execution failed:"), msg);
        assertTrue(msg.contains("bad-stage"), msg);
        assertTrue(msg.contains("failed=1"), msg);
    }

    @Test
    void getMessageAppendsManualInterventionChangeIds() {
        StageResult blocked = StageResult.builder()
                .stageId("mi").stageName("mi-stage")
                .state(StageState.blockedManualIntervention("mi-stage",
                        Arrays.asList(new RecoveryIssue("change-a"), new RecoveryIssue("change-b"))))
                .build();
        ExecuteResponseData result = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).failedStages(1)
                .stages(Collections.singletonList(blocked))
                .build();

        StagedExecuteOperationException ex = new StagedExecuteOperationException(result);
        assertTrue(ex.getMessage().contains("manual intervention required for change(s): change-a, change-b"),
                ex.getMessage());
    }

    @Test
    void toStringReturnsMultiLineReport() {
        ExecuteResponseData result = failureResult();
        StagedExecuteOperationException ex = new StagedExecuteOperationException(result);

        String rendered = ex.toString();
        assertTrue(rendered.contains(System.lineSeparator()),
                "toString must be multi-line: " + rendered);
        assertTrue(rendered.contains("Flamingock execution report"), rendered);
        assertTrue(rendered.contains("[FAILED]"), rendered);
    }

    private static ExecuteResponseData failureResult() {
        StageResult failed = StageResult.builder()
                .stageId("bad").stageName("bad-stage")
                .state(StageState.failed(new ErrorInfo("Boom", "kaboom", Collections.singletonList("c1"), "bad-stage")))
                .durationMs(70)
                .build();
        return ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).failedStages(1)
                .totalChanges(1).failedChanges(1)
                .totalDurationMs(120)
                .stages(Collections.singletonList(failed))
                .build();
    }
}
