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
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
import io.flamingock.internal.util.JsonObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FileResponseChannel - writes JSON response to file atomically.
 */
class FileResponseChannelTest {

    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // JsonObjectMapper.DEFAULT_INSTANCE already includes JavaTimeModule
        objectMapper = JsonObjectMapper.DEFAULT_INSTANCE.copy();
        objectMapper.registerSubtypes(
                new NamedType(ExecuteResponseData.class, "execute")
        );
    }

    @Test
    @DisplayName("Should write valid JSON to file")
    void shouldWriteValidJsonToFile() throws Exception {
        // Given
        Path outputPath = tempDir.resolve("response.json");
        FileResponseChannel channel = new FileResponseChannel(outputPath.toString(), objectMapper);

        ExecuteResponseData data = ExecuteResponseData.builder()
                .status(ExecutionStatus.SUCCESS)
                .totalStages(1)
                .completedStages(1)
                .totalChanges(3)
                .appliedChanges(2)
                .skippedChanges(1)
                .failedChanges(0)
                .totalDurationMs(500)
                .build();

        ResponseEnvelope envelope = ResponseEnvelope.success("EXECUTE", data, 500);

        // When
        channel.write(envelope);

        // Then
        assertTrue(Files.exists(outputPath));
        String content = new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8);
        assertNotNull(content);
        assertFalse(content.isEmpty());

        // Verify the JSON can be parsed back
        ResponseEnvelope parsed = objectMapper.readValue(content, ResponseEnvelope.class);
        assertTrue(parsed.isSuccess());
        assertEquals("EXECUTE", parsed.getOperation());
        assertEquals(500, parsed.getDurationMs());
    }

    @Test
    @DisplayName("Should create parent directories if needed")
    void shouldCreateParentDirectoriesIfNeeded() throws Exception {
        // Given
        Path nestedPath = tempDir.resolve("nested/deeply/response.json");
        assertFalse(Files.exists(nestedPath.getParent()));

        FileResponseChannel channel = new FileResponseChannel(nestedPath.toString(), objectMapper);

        ResponseEnvelope envelope = ResponseEnvelope.success("EXECUTE", null, 100);

        // When
        channel.write(envelope);

        // Then
        assertTrue(Files.exists(nestedPath));
        assertTrue(Files.exists(nestedPath.getParent()));
        String content = new String(Files.readAllBytes(nestedPath), StandardCharsets.UTF_8);
        assertNotNull(content);

        // Verify the JSON is valid
        ResponseEnvelope parsed = objectMapper.readValue(content, ResponseEnvelope.class);
        assertTrue(parsed.isSuccess());
    }
}
