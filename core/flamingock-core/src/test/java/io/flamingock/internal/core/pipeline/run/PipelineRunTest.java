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

import io.flamingock.internal.common.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineRunTest {

    @Test
    void buildsOneStageRunPerStageInOrderAllNotStarted() {
        AbstractLoadedStage a = mockStage("alpha");
        AbstractLoadedStage b = mockStage("beta");

        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(a, b));

        List<StageRun> runs = pipelineRun.getStageRuns();
        assertEquals(2, runs.size());
        assertEquals("alpha", runs.get(0).getName());
        assertEquals("beta", runs.get(1).getName());
        assertSame(StageState.NOT_STARTED, runs.get(0).getState());
        assertSame(StageState.NOT_STARTED, runs.get(1).getState());
    }

    @Test
    void markStageStartedUpdatesTheRightStage() {
        AbstractLoadedStage a = mockStage("alpha");
        AbstractLoadedStage b = mockStage("beta");

        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(a, b));
        pipelineRun.markStageStarted("alpha");

        assertSame(StageState.STARTED, pipelineRun.getStageRun("alpha").getState());
        assertSame(StageState.NOT_STARTED, pipelineRun.getStageRun("beta").getState());
    }

    @Test
    void markStageFailedDerivesErrorInfoFromException() {
        AbstractLoadedStage a = mockStage("alpha");
        StageResult stageResult = StageResult.builder()
                .stageId("alpha")
                .stageName("alpha")
                .state(StageState.failed(null))
                .build();
        RuntimeException cause = new RuntimeException("boom");
        StageExecutionException exception = StageExecutionException.fromResult(cause, stageResult, "change-1");

        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(a));
        pipelineRun.markStageFailed("alpha", exception);

        StageState state = pipelineRun.getStageRun("alpha").getState();
        assertTrue(state.isFailed());
        ErrorInfo info = state.getErrorInfo().get();
        assertEquals(java.util.Collections.singletonList("change-1"), info.getChangeIds());
        assertEquals("alpha", info.getStageId());
    }

    @Test
    void markStageStartedThrowsForUnknownStage() {
        AbstractLoadedStage a = mockStage("alpha");
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(a));

        assertThrows(
                IllegalArgumentException.class,
                () -> pipelineRun.markStageStarted("missing"));
    }

    @Test
    void getStageRunReturnsNullForUnknownStage() {
        AbstractLoadedStage a = mockStage("alpha");
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(a));

        assertNull(pipelineRun.getStageRun("missing"));
    }

    @Test
    void markStageFailedWithThrowableSynthesisesErrorInfo() {
        AbstractLoadedStage a = mockStage("alpha");
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(a));

        RuntimeException cause = new RuntimeException("boom");
        pipelineRun.markStageFailed("alpha", cause);

        StageState state = pipelineRun.getStageRun("alpha").getState();
        assertTrue(state.isFailed());
        ErrorInfo info = state.getErrorInfo().get();
        assertEquals("alpha", info.getStageId());
        assertEquals("RuntimeException", info.getErrorType());
        assertEquals("boom", info.getMessage());
    }

    @Test
    void markPipelineFailedIsIdempotentFirstWins() {
        AbstractLoadedStage a = mockStage("alpha");
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(a));

        pipelineRun.markPipelineFailed(new RuntimeException("first"));
        pipelineRun.markPipelineFailed(new IllegalStateException("second"));

        ErrorInfo info = pipelineRun.getPipelineError().get();
        assertEquals("RuntimeException", info.getErrorType());
        assertEquals("first", info.getMessage());
    }

    @Test
    void getPipelineErrorIsEmptyUntilMarked() {
        AbstractLoadedStage a = mockStage("alpha");
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(a));

        assertFalse(pipelineRun.getPipelineError().isPresent());
    }

    @Test
    void markStagesBlockedFromMIGroupsIssuesByOwningStage() {
        AbstractLoadedChange alphaChange1 = mockChange("alpha-c1");
        AbstractLoadedChange alphaChange2 = mockChange("alpha-c2");
        AbstractLoadedChange betaChange = mockChange("beta-c1");

        AbstractLoadedStage alpha = mockStageWithChanges("alpha", alphaChange1, alphaChange2);
        AbstractLoadedStage beta = mockStageWithChanges("beta", betaChange);
        AbstractLoadedStage gamma = mockStageWithChanges("gamma");   // no MI'd changes

        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(alpha, beta, gamma));

        List<RecoveryIssue> issues = Arrays.asList(
                new RecoveryIssue("alpha-c1"),
                new RecoveryIssue("alpha-c2"),
                new RecoveryIssue("beta-c1"));
        ManualInterventionRequiredException miEx = new ManualInterventionRequiredException(issues, "alpha");

        pipelineRun.markStagesBlockedFromMI(miEx);

        StageState alphaState = pipelineRun.getStageRun("alpha").getState();
        assertTrue(alphaState.isBlockedForManualIntervention());
        assertEquals(2, alphaState.getRecoveryIssues().size());

        StageState betaState = pipelineRun.getStageRun("beta").getState();
        assertTrue(betaState.isBlockedForManualIntervention());
        assertEquals(1, betaState.getRecoveryIssues().size());
        assertEquals("beta-c1", betaState.getRecoveryIssues().get(0).getChangeId());

        // No MI'd changes for gamma → stays NOT_STARTED.
        assertSame(StageState.NOT_STARTED, pipelineRun.getStageRun("gamma").getState());
    }

    @Test
    void markStagesBlockedFromMIThrowsForUnknownChange() {
        AbstractLoadedStage alpha = mockStageWithChanges("alpha", mockChange("alpha-c1"));
        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(alpha));

        ManualInterventionRequiredException miEx = new ManualInterventionRequiredException(
                Arrays.asList(new RecoveryIssue("ghost-change")), "alpha");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> pipelineRun.markStagesBlockedFromMI(miEx));
        assertTrue(ex.getMessage().contains("ghost-change"));

        // No partial state — the alpha stage stays NOT_STARTED.
        assertSame(StageState.NOT_STARTED, pipelineRun.getStageRun("alpha").getState());
    }

    @Test
    void getLoadedStagesReturnsTheUnderlyingStagesInOrder() {
        AbstractLoadedStage a = mockStage("alpha");
        AbstractLoadedStage b = mockStage("beta");

        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(a, b));

        List<AbstractLoadedStage> loaded = pipelineRun.getLoadedStages();
        assertEquals(2, loaded.size());
        assertSame(a, loaded.get(0));
        assertSame(b, loaded.get(1));
    }

    private static AbstractLoadedStage mockStage(String name) {
        AbstractLoadedStage stage = mock(AbstractLoadedStage.class);
        when(stage.getName()).thenReturn(name);
        return stage;
    }

    private static AbstractLoadedStage mockStageWithChanges(String name, AbstractLoadedChange... changes) {
        AbstractLoadedStage stage = mock(AbstractLoadedStage.class);
        when(stage.getName()).thenReturn(name);
        when(stage.getChanges()).thenReturn(Arrays.asList(changes));
        return stage;
    }

    private static AbstractLoadedChange mockChange(String id) {
        AbstractLoadedChange change = mock(AbstractLoadedChange.class);
        when(change.getId()).thenReturn(id);
        return change;
    }
}
