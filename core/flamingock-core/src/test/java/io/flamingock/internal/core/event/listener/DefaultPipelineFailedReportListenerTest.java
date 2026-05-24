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
import io.flamingock.internal.core.event.model.IPipelineFailedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DefaultPipelineFailedReportListenerTest {

    private final DefaultPipelineFailedReportListener listener = new DefaultPipelineFailedReportListener();

    @Test
    void acceptsNormalEventWithoutThrowing() {
        ExecuteResponseData data = ExecuteResponseData.builder()
                .status(ExecutionStatus.FAILED)
                .totalStages(1).failedStages(1)
                .totalDurationMs(50)
                .build();
        Exception cause = new RuntimeException("boom");
        assertDoesNotThrow(() -> listener.accept(event(cause, data)));
    }

    @Test
    void acceptsNullEventWithoutThrowing() {
        assertDoesNotThrow(() -> listener.accept(null));
    }

    @Test
    void acceptsEventWithNullResultWithoutThrowing() {
        assertDoesNotThrow(() -> listener.accept(event(new RuntimeException("boom"), null)));
    }

    @Test
    void swallowsThrowableFromPoisonedEvent() {
        IPipelineFailedEvent poisoned = new IPipelineFailedEvent() {
            @Override
            public Exception getException() {
                throw new IllegalStateException("getException blew up");
            }

            @Override
            public ExecuteResponseData getResult() {
                throw new IllegalStateException("getResult blew up");
            }
        };
        // Contract: defensive — must never propagate.
        assertDoesNotThrow(() -> listener.accept(poisoned));
    }

    private static IPipelineFailedEvent event(Exception cause, ExecuteResponseData result) {
        return new IPipelineFailedEvent() {
            @Override
            public Exception getException() {
                return cause;
            }

            @Override
            public ExecuteResponseData getResult() {
                return result;
            }
        };
    }
}
