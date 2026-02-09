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
package io.flamingock.internal.common.core.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import io.flamingock.internal.common.core.response.data.AuditListResponseData;
import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageStatus;
import io.flamingock.internal.util.JsonObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for serialization round-trip of response data classes.
 */
class ResponseSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // JsonObjectMapper.DEFAULT_INSTANCE already includes JavaTimeModule
        objectMapper = JsonObjectMapper.DEFAULT_INSTANCE.copy();
        objectMapper.registerSubtypes(
                new NamedType(AuditListResponseData.class, "audit_list"),
                new NamedType(ExecuteResponseData.class, "execute")
        );
    }

    @Test
    @DisplayName("Should serialize and deserialize ExecuteResponseData")
    void shouldSerializeAndDeserializeExecuteResponseData() throws Exception {
        // Given
        ExecuteResponseData original = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS)
                .startTime(Instant.parse("2026-02-09T10:00:00Z"))
                .endTime(Instant.parse("2026-02-09T10:00:01Z"))
                .totalDurationMs(1000)
                .totalStages(2)
                .completedStages(2)
                .failedStages(0)
                .totalChanges(5)
                .appliedChanges(3)
                .skippedChanges(2)
                .failedChanges(0)
                .addStage(StageResult.builder()
                        .stageId("stage-1")
                        .stageName("Stage One")
                        .status(StageStatus.COMPLETED)
                        .durationMs(500)
                        .addChange(ChangeResult.builder()
                                .changeId("change-001")
                                .author("developer")
                                .status(ChangeStatus.APPLIED)
                                .durationMs(100)
                                .build())
                        .build())
                .build();

        // When
        String json = objectMapper.writeValueAsString(original);
        ExecuteResponseData deserialized = objectMapper.readValue(json, ExecuteResponseData.class);

        // Then
        assertNotNull(deserialized);
        assertEquals(ExecutionStatus.SUCCESS, deserialized.getStatus());
        assertEquals(2, deserialized.getTotalStages());
        assertEquals(2, deserialized.getCompletedStages());
        assertEquals(5, deserialized.getTotalChanges());
        assertEquals(3, deserialized.getAppliedChanges());
        assertEquals(2, deserialized.getSkippedChanges());
        assertEquals(0, deserialized.getFailedChanges());
        assertEquals(1, deserialized.getStages().size());
        assertEquals("stage-1", deserialized.getStages().get(0).getStageId());
        assertEquals(StageStatus.COMPLETED, deserialized.getStages().get(0).getStatus());
        assertEquals(1, deserialized.getStages().get(0).getChanges().size());
        assertEquals("change-001", deserialized.getStages().get(0).getChanges().get(0).getChangeId());
    }

    @Test
    @DisplayName("Should serialize and deserialize AuditListResponseData")
    void shouldSerializeAndDeserializeAuditListResponseData() throws Exception {
        // Given
        AuditListResponseData original = new AuditListResponseData(Arrays.asList(
                new AuditListResponseData.AuditEntryDto(
                        "change-001",
                        "developer",
                        "APPLIED",
                        "stage-1",
                        LocalDateTime.of(2026, 2, 9, 10, 0, 0),
                        100
                ),
                new AuditListResponseData.AuditEntryDto(
                        "change-002",
                        "developer",
                        "APPLIED",
                        "stage-1",
                        LocalDateTime.of(2026, 2, 9, 10, 0, 1),
                        150
                )
        ));

        // When
        String json = objectMapper.writeValueAsString(original);
        AuditListResponseData deserialized = objectMapper.readValue(json, AuditListResponseData.class);

        // Then
        assertNotNull(deserialized);
        assertNotNull(deserialized.getEntries());
        assertEquals(2, deserialized.getEntries().size());

        AuditListResponseData.AuditEntryDto first = deserialized.getEntries().get(0);
        assertEquals("change-001", first.getTaskId());
        assertEquals("developer", first.getAuthor());
        assertEquals("APPLIED", first.getState());
        assertEquals("stage-1", first.getStageId());
        assertEquals(100, first.getExecutionMillis());

        AuditListResponseData.AuditEntryDto second = deserialized.getEntries().get(1);
        assertEquals("change-002", second.getTaskId());
        assertEquals(150, second.getExecutionMillis());
    }

    @Test
    @DisplayName("Should serialize ResponseEnvelope with polymorphic data")
    void shouldSerializeResponseEnvelopeWithPolymorphicData() throws Exception {
        // Given
        ExecuteResponseData executeData = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS)
                .totalStages(1)
                .completedStages(1)
                .totalChanges(2)
                .appliedChanges(2)
                .skippedChanges(0)
                .failedChanges(0)
                .totalDurationMs(500)
                .build();

        ResponseEnvelope original = ResponseEnvelope.success("EXECUTE", executeData, 500);

        // When
        String json = objectMapper.writeValueAsString(original);
        ResponseEnvelope deserialized = objectMapper.readValue(json, ResponseEnvelope.class);

        // Then
        assertNotNull(deserialized);
        assertTrue(deserialized.isSuccess());
        assertEquals("EXECUTE", deserialized.getOperation());
        assertEquals(500, deserialized.getDurationMs());
        assertNotNull(deserialized.getData());

        // The data should be deserializable to ExecuteResponseData
        ExecuteResponseData data = objectMapper.convertValue(deserialized.getData(), ExecuteResponseData.class);
        assertEquals(ExecutionStatus.SUCCESS, data.getStatus());
        assertEquals(1, data.getTotalStages());
        assertEquals(2, data.getTotalChanges());
        assertEquals(2, data.getAppliedChanges());
    }
}
