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

import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageStatus;
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                .status(StageStatus.FAILED)
                .build();
        RuntimeException cause = new RuntimeException("boom");
        StageExecutionException exception = StageExecutionException.fromResult(cause, stageResult, "change-1");

        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(a));
        pipelineRun.markStageFailed("alpha", exception);

        StageState state = pipelineRun.getStageRun("alpha").getState();
        assertTrue(state.isFailed());
        ErrorInfo info = state.getErrorInfo().get();
        assertEquals("change-1", info.getChangeId());
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
}
