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
package io.flamingock.cli.executor.result;

import io.flamingock.internal.common.core.response.ResponseEnvelope;
import io.flamingock.internal.common.core.response.data.AuditListResponseData;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResponseResultReader - reads and parses JSON response files from spawned processes.
 */
class ResponseResultReaderTest {

    private ResponseResultReader reader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reader = new ResponseResultReader();
    }

    @Test
    @DisplayName("Should parse valid execute response JSON")
    void shouldParseValidExecuteResponse() throws IOException {
        // Given
        String json = "{\n" +
                "  \"success\": true,\n" +
                "  \"operation\": \"EXECUTE\",\n" +
                "  \"timestamp\": \"2026-02-09T10:00:00Z\",\n" +
                "  \"durationMs\": 1500,\n" +
                "  \"data\": {\n" +
                "    \"@type\": \"execute\",\n" +
                "    \"status\": \"SUCCESS\",\n" +
                "    \"totalStages\": 2,\n" +
                "    \"completedStages\": 2,\n" +
                "    \"failedStages\": 0,\n" +
                "    \"totalChanges\": 5,\n" +
                "    \"appliedChanges\": 3,\n" +
                "    \"skippedChanges\": 2,\n" +
                "    \"failedChanges\": 0,\n" +
                "    \"totalDurationMs\": 1200\n" +
                "  }\n" +
                "}";

        Path responseFile = tempDir.resolve("response.json");
        Files.write(responseFile, json.getBytes(StandardCharsets.UTF_8));

        // When
        ResponseResultReader.ResponseResult<ExecuteResponseData> result =
                reader.readTyped(responseFile, ExecuteResponseData.class);

        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(ExecutionStatus.SUCCESS, result.getData().getStatus());
        assertEquals(2, result.getData().getTotalStages());
        assertEquals(2, result.getData().getCompletedStages());
        assertEquals(5, result.getData().getTotalChanges());
        assertEquals(3, result.getData().getAppliedChanges());
        assertEquals(2, result.getData().getSkippedChanges());
        assertEquals(0, result.getData().getFailedChanges());
        assertEquals(1500, result.getDurationMs());
    }

    @Test
    @DisplayName("Should parse valid audit list response JSON")
    void shouldParseValidAuditListResponse() throws IOException {
        // Given
        String json = "{\n" +
                "  \"success\": true,\n" +
                "  \"operation\": \"LIST\",\n" +
                "  \"timestamp\": \"2026-02-09T10:00:00Z\",\n" +
                "  \"durationMs\": 250,\n" +
                "  \"data\": {\n" +
                "    \"@type\": \"audit_list\",\n" +
                "    \"entries\": [\n" +
                "      {\n" +
                "        \"taskId\": \"change-001\",\n" +
                "        \"author\": \"developer\",\n" +
                "        \"state\": \"APPLIED\",\n" +
                "        \"stageId\": \"stage-1\",\n" +
                "        \"executionMillis\": 100\n" +
                "      },\n" +
                "      {\n" +
                "        \"taskId\": \"change-002\",\n" +
                "        \"author\": \"developer\",\n" +
                "        \"state\": \"APPLIED\",\n" +
                "        \"stageId\": \"stage-1\",\n" +
                "        \"executionMillis\": 150\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        Path responseFile = tempDir.resolve("response.json");
        Files.write(responseFile, json.getBytes(StandardCharsets.UTF_8));

        // When
        ResponseResultReader.ResponseResult<AuditListResponseData> result =
                reader.readTyped(responseFile, AuditListResponseData.class);

        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertNotNull(result.getData().getEntries());
        assertEquals(2, result.getData().getEntries().size());
        assertEquals("change-001", result.getData().getEntries().get(0).getTaskId());
        assertEquals("developer", result.getData().getEntries().get(0).getAuthor());
        assertEquals("APPLIED", result.getData().getEntries().get(0).getState());
        assertEquals(250, result.getDurationMs());
    }

    @Test
    @DisplayName("Should return empty when file not found")
    void shouldReturnEmptyWhenFileNotFound() {
        // Given
        Path nonExistentFile = tempDir.resolve("does-not-exist.json");

        // When
        Optional<ResponseEnvelope> result = reader.read(nonExistentFile);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should handle corrupt JSON gracefully")
    void shouldHandleCorruptJson() throws IOException {
        // Given
        String corruptJson = "{ this is not valid json !!!";
        Path responseFile = tempDir.resolve("corrupt.json");
        Files.write(responseFile, corruptJson.getBytes(StandardCharsets.UTF_8));

        // When
        Optional<ResponseEnvelope> result = reader.read(responseFile);

        // Then
        assertFalse(result.isPresent());
    }
}
