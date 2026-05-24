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
package io.flamingock.springboot.event;

import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.core.event.model.IPipelineCompletedEvent;
import io.flamingock.internal.core.event.model.IPipelineFailedEvent;
import io.flamingock.internal.core.event.model.IStageCompletedEvent;
import io.flamingock.internal.core.event.model.IStageFailedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies that the Spring {@code ApplicationEvent} wrappers expose the typed
 * {@code ExecuteResponseData} / {@code StageResult} payloads from the wrapped core event, so
 * Spring user code can render the canonical execution report without downcasting.
 */
class SpringEventPayloadPassthroughTest {

    private final Object source = new Object();

    @Test
    void pipelineCompletedWrapperExposesResult() {
        ExecuteResponseData data = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS).build();
        IPipelineCompletedEvent core = () -> data;
        SpringPipelineCompletedEvent wrapper = new SpringPipelineCompletedEvent(source, core);
        assertSame(data, wrapper.getResult());
    }

    @Test
    void pipelineFailedWrapperExposesResultAndException() {
        ExecuteResponseData data = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED).build();
        Exception cause = new RuntimeException("boom");
        IPipelineFailedEvent core = new IPipelineFailedEvent() {
            @Override public Exception getException() { return cause; }
            @Override public ExecuteResponseData getResult() { return data; }
        };
        SpringPipelineFailedEvent wrapper = new SpringPipelineFailedEvent(source, core);
        assertSame(data, wrapper.getResult());
        assertSame(cause, wrapper.getException());
    }

    @Test
    void stageCompletedWrapperExposesResult() {
        StageResult stage = StageResult.builder()
                .stageId("s").stageName("s").state(StageState.COMPLETED).build();
        IStageCompletedEvent core = () -> stage;
        SpringStageCompletedEvent wrapper = new SpringStageCompletedEvent(source, core);
        assertSame(stage, wrapper.getResult());
    }

    @Test
    void stageFailedWrapperExposesResultAndException() {
        StageResult stage = StageResult.builder()
                .stageId("s").stageName("s")
                .state(StageState.failed(null)).build();
        Exception cause = new RuntimeException("boom");
        IStageFailedEvent core = new IStageFailedEvent() {
            @Override public Exception getException() { return cause; }
            @Override public StageResult getResult() { return stage; }
        };
        SpringStageFailedEvent wrapper = new SpringStageFailedEvent(source, core);
        assertSame(stage, wrapper.getResult());
        assertSame(cause, wrapper.getException());
    }
}
