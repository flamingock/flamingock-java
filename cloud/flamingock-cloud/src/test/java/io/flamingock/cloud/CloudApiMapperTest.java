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
package io.flamingock.cloud;

import io.flamingock.cloud.api.vo.CloudStageStatus;
import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.StageState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CloudApiMapperTest {

    @Test
    @DisplayName("toCloud(null) returns null (wire shape for NOT_STARTED is field absent)")
    void nullReturnsNull() {
        assertNull(CloudApiMapper.toCloud((StageState) null));
    }

    @Test
    @DisplayName("toCloud(NOT_STARTED) returns null (wire shape for NOT_STARTED is field absent)")
    void notStartedReturnsNull() {
        assertNull(CloudApiMapper.toCloud(StageState.NOT_STARTED));
    }

    @Test
    @DisplayName("toCloud(STARTED) maps to STARTED")
    void startedMaps() {
        assertEquals(CloudStageStatus.STARTED, CloudApiMapper.toCloud(StageState.STARTED));
    }

    @Test
    @DisplayName("toCloud(COMPLETED) maps to COMPLETED")
    void completedMaps() {
        assertEquals(CloudStageStatus.COMPLETED, CloudApiMapper.toCloud(StageState.COMPLETED));
    }

    @Test
    @DisplayName("toCloud(Failed) maps to FAILED")
    void failedMaps() {
        StageState failed = StageState.failed(new ErrorInfo("RuntimeException", "boom", Collections.emptyList(), "stage-1"));
        assertEquals(CloudStageStatus.FAILED, CloudApiMapper.toCloud(failed));
    }

    @Test
    @DisplayName("toCloud(BlockedForMI) maps to BLOCKED_MANUAL_INTERVENTION — not FAILED — even though BlockedForMI extends Failed")
    void blockedForMIMapsBeforeFailed() {
        StageState blocked = StageState.blockedManualIntervention(
                "stage-1", Collections.singletonList(new RecoveryIssue("change-1")));
        assertEquals(CloudStageStatus.BLOCKED_MANUAL_INTERVENTION, CloudApiMapper.toCloud(blocked));
    }
}
