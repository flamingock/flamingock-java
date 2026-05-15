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

import io.flamingock.api.StageType;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StageRunBlockTest {

    @Test
    void exposesTypeAndStageRuns() {
        StageRun a = newStageRun("alpha");
        StageRun b = newStageRun("beta");
        StageRunBlock block = new StageRunBlock(StageType.DEFAULT, Arrays.asList(a, b));

        assertSame(StageType.DEFAULT, block.getType());
        assertEquals(2, block.getStageRuns().size());
        assertSame(a, block.getStageRuns().get(0));
        assertSame(b, block.getStageRuns().get(1));
    }

    @Test
    void isTerminalTrueWhenAllStagesAreCompletedOrFailed() {
        StageRun a = newStageRun("alpha", StageState.COMPLETED);
        StageRun b = newStageRun("beta", StageState.failed(errorInfo("boom")));
        StageRunBlock block = new StageRunBlock(StageType.DEFAULT, Arrays.asList(a, b));

        assertTrue(block.isTerminal());
    }

    @Test
    void isTerminalFalseIfAnyStageNotStartedOrStarted() {
        StageRun a = newStageRun("alpha", StageState.COMPLETED);
        StageRun b = newStageRun("beta", StageState.STARTED);
        StageRunBlock block = new StageRunBlock(StageType.DEFAULT, Arrays.asList(a, b));

        assertFalse(block.isTerminal());
    }

    @Test
    void isSuccessfulTrueOnlyWhenEveryStageCompleted() {
        StageRun a = newStageRun("alpha", StageState.COMPLETED);
        StageRun b = newStageRun("beta", StageState.COMPLETED);
        StageRunBlock block = new StageRunBlock(StageType.DEFAULT, Arrays.asList(a, b));

        assertTrue(block.isSuccessful());
    }

    @Test
    void isSuccessfulFalseIfAnyStageNotCompleted() {
        StageRun a = newStageRun("alpha", StageState.COMPLETED);
        StageRun b = newStageRun("beta", StageState.failed(errorInfo("boom")));
        StageRunBlock block = new StageRunBlock(StageType.DEFAULT, Arrays.asList(a, b));

        assertFalse(block.isSuccessful());
    }

    @Test
    void hasFailuresTrueWhenAnyStageFailed() {
        StageRun a = newStageRun("alpha", StageState.COMPLETED);
        StageRun b = newStageRun("beta", StageState.failed(errorInfo("boom")));
        StageRunBlock block = new StageRunBlock(StageType.DEFAULT, Arrays.asList(a, b));

        assertTrue(block.hasFailures());
    }

    @Test
    void hasFailuresTrueForBlockedForMIBecauseItIsAFailedSubtype() {
        StageRun a = newStageRun(
                "alpha",
                StageState.blockedManualIntervention("alpha", Collections.emptyList()));
        StageRunBlock block = new StageRunBlock(StageType.LEGACY, Collections.singletonList(a));

        assertTrue(block.hasFailures());
        assertTrue(block.isTerminal());
        assertFalse(block.isSuccessful());
    }

    @Test
    void hasFailuresFalseWhenAllStagesSuccessfulOrPending() {
        StageRun a = newStageRun("alpha", StageState.COMPLETED);
        StageRun b = newStageRun("beta", StageState.STARTED);
        StageRunBlock block = new StageRunBlock(StageType.DEFAULT, Arrays.asList(a, b));

        assertFalse(block.hasFailures());
    }

    @Test
    void emptyBlockPredicatesAreVacuouslyTrueForTerminalAndSuccessful() {
        StageRunBlock block = new StageRunBlock(StageType.SYSTEM, Collections.emptyList());

        assertTrue(block.isTerminal());
        assertTrue(block.isSuccessful());
        assertFalse(block.hasFailures());
    }

    @Test
    void getStageRunsReturnsAnUnmodifiableList() {
        StageRun a = newStageRun("alpha");
        StageRunBlock block = new StageRunBlock(StageType.DEFAULT, Collections.singletonList(a));

        List<StageRun> stageRuns = block.getStageRuns();
        try {
            stageRuns.add(newStageRun("beta"));
            // If we reach here, the list is mutable — fail the test.
            org.junit.jupiter.api.Assertions.fail("Expected getStageRuns() to return an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // --- helpers ---

    private static StageRun newStageRun(String name) {
        AbstractLoadedStage loaded = mock(AbstractLoadedStage.class);
        when(loaded.getName()).thenReturn(name);
        return new StageRun(loaded);
    }

    private static StageRun newStageRun(String name, StageState state) {
        StageRun stageRun = newStageRun(name);
        stageRun.setResult(StageResult.builder(stageRun.getResult())
                .state(state)
                .build());
        return stageRun;
    }

    private static ErrorInfo errorInfo(String message) {
        return ErrorInfo.fromThrowable(new RuntimeException(message), Collections.emptyList(), null);
    }
}
