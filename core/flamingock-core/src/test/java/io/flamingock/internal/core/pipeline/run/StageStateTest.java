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
import org.junit.jupiter.api.Test;

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
        ErrorInfo info = new ErrorInfo("RuntimeException", "boom", "change-1", "stage-1");
        StageState state = StageState.failed(info);

        assertTrue(state.isFailed());
        assertTrue(state.getErrorInfo().isPresent());
        assertSame(info, state.getErrorInfo().get());
    }
}
