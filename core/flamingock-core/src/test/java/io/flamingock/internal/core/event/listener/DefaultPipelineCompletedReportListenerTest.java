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
package io.flamingock.internal.core.event.listener;

import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.core.event.model.IPipelineCompletedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DefaultPipelineCompletedReportListenerTest {

    private final DefaultPipelineCompletedReportListener listener = new DefaultPipelineCompletedReportListener();

    @Test
    void acceptsNormalEventWithoutThrowing() {
        ExecuteResponseData data = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS)
                .totalStages(1).completedStages(1)
                .totalDurationMs(50)
                .build();
        assertDoesNotThrow(() -> listener.accept(() -> data));
    }

    @Test
    void acceptsNullEventWithoutThrowing() {
        assertDoesNotThrow(() -> listener.accept(null));
    }

    @Test
    void acceptsEventWithNullResultWithoutThrowing() {
        assertDoesNotThrow(() -> listener.accept(() -> null));
    }

    @Test
    void swallowsThrowableFromPoisonedEvent() {
        IPipelineCompletedEvent poisoned = () -> { throw new IllegalStateException("blew up"); };
        assertDoesNotThrow(() -> listener.accept(poisoned));
    }
}
