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
package io.flamingock.internal.common.core.response.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import io.flamingock.internal.util.JsonObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageStateSerializationTest {

    private final ObjectMapper mapper = JsonObjectMapper.DEFAULT_INSTANCE.copy();

    @Test
    void roundTripNotStarted() throws Exception {
        String json = mapper.writeValueAsString(StageState.NOT_STARTED);
        assertTrue(json.contains("\"type\":\"NOT_STARTED\""));

        StageState back = mapper.readValue(json, StageState.class);
        assertTrue(back.isNotStarted());
        assertFalse(back.isFailed());
    }

    @Test
    void roundTripStarted() throws Exception {
        String json = mapper.writeValueAsString(StageState.STARTED);
        assertTrue(json.contains("\"type\":\"STARTED\""));

        StageState back = mapper.readValue(json, StageState.class);
        assertTrue(back.isStarted());
    }

    @Test
    void roundTripCompleted() throws Exception {
        String json = mapper.writeValueAsString(StageState.COMPLETED);
        assertTrue(json.contains("\"type\":\"COMPLETED\""));

        StageState back = mapper.readValue(json, StageState.class);
        assertTrue(back.isCompleted());
    }

    @Test
    void roundTripFailed() throws Exception {
        ErrorInfo info = new ErrorInfo("RuntimeException", "boom", java.util.Collections.singletonList("change-1"), "stage-1");
        StageState failed = StageState.failed(info);

        String json = mapper.writeValueAsString(failed);
        assertTrue(json.contains("\"type\":\"FAILED\""));
        assertTrue(json.contains("\"errorInfo\""));

        StageState back = mapper.readValue(json, StageState.class);
        assertTrue(back.isFailed());
        ErrorInfo backInfo = back.getErrorInfo().orElseThrow(AssertionError::new);
        assertEquals("RuntimeException", backInfo.getErrorType());
        assertEquals("boom", backInfo.getMessage());
        assertEquals(java.util.Collections.singletonList("change-1"), backInfo.getChangeIds());
        assertEquals("stage-1", backInfo.getStageId());
    }

    @Test
    void roundTripBlockedForMI() throws Exception {
        List<RecoveryIssue> issues = Arrays.asList(new RecoveryIssue("c1"), new RecoveryIssue("c2"));
        StageState blocked = StageState.blockedManualIntervention("stage-1", issues);

        String json = mapper.writeValueAsString(blocked);
        assertTrue(json.contains("\"type\":\"BLOCKED_MANUAL_INTERVENTION\""));
        assertTrue(json.contains("\"errorInfo\""));     // inherited from Failed
        assertTrue(json.contains("\"recoveryIssues\""));

        StageState back = mapper.readValue(json, StageState.class);
        assertTrue(back.isBlockedForManualIntervention());
        assertTrue(back.isFailed());
        // ErrorInfo round-tripped
        ErrorInfo info = back.getErrorInfo().orElseThrow(AssertionError::new);
        assertEquals("MANUAL_INTERVENTION_REQUIRED", info.getErrorType());
        assertEquals("stage-1", info.getStageId());
        // Recovery issues round-tripped
        assertEquals(2, back.getRecoveryIssues().size());
        assertEquals("c1", back.getRecoveryIssues().get(0).getChangeId());
        assertEquals("c2", back.getRecoveryIssues().get(1).getChangeId());
    }

    @Test
    void roundTripStageResultWithFailedState() throws Exception {
        ErrorInfo info = new ErrorInfo("RuntimeException", "boom", java.util.Collections.singletonList("change-1"), "stage-1");
        StageResult result = StageResult.builder()
                .stageId("stage-1")
                .stageName("stage-1")
                .state(StageState.failed(info))
                .durationMs(123)
                .build();

        String json = mapper.writeValueAsString(result);
        StageResult back = mapper.readValue(json, StageResult.class);

        assertEquals("stage-1", back.getStageId());
        assertTrue(back.getState().isFailed());
        assertEquals("boom", back.getState().getErrorInfo().orElseThrow(AssertionError::new).getMessage());
    }
}
