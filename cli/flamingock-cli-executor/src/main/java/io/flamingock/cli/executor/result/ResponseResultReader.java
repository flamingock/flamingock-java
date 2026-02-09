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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.flamingock.internal.common.core.response.ResponseEnvelope;
import io.flamingock.internal.common.core.response.data.AuditFixResponseData;
import io.flamingock.internal.common.core.response.data.AuditListResponseData;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.IssueGetResponseData;
import io.flamingock.internal.common.core.response.data.IssueListResponseData;
import io.flamingock.internal.util.JsonObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads and parses response files from spawned application processes.
 */
public class ResponseResultReader {

    private final ObjectMapper objectMapper;

    public ResponseResultReader() {
        this.objectMapper = JsonObjectMapper.DEFAULT_INSTANCE.copy();
        this.objectMapper.registerModule(new JavaTimeModule());
        registerSubtypes();
    }

    private void registerSubtypes() {
        objectMapper.registerSubtypes(
                new NamedType(AuditListResponseData.class, "audit_list"),
                new NamedType(AuditFixResponseData.class, "audit_fix"),
                new NamedType(ExecuteResponseData.class, "execute"),
                new NamedType(IssueListResponseData.class, "issue_list"),
                new NamedType(IssueGetResponseData.class, "issue_get")
        );
    }

    /**
     * Reads a response envelope from a file.
     *
     * @param filePath the path to the response file
     * @return the parsed response envelope, or empty if the file doesn't exist or parsing fails
     */
    public Optional<ResponseEnvelope> read(Path filePath) {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        try {
            ResponseEnvelope envelope = objectMapper.readValue(filePath.toFile(), ResponseEnvelope.class);
            return Optional.of(envelope);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads a response envelope from a file and returns it as a typed result.
     *
     * @param filePath the path to the response file
     * @param dataType the expected type of the data field
     * @param <T> the data type
     * @return the typed response result
     */
    public <T> ResponseResult<T> readTyped(Path filePath, Class<T> dataType) {
        Optional<ResponseEnvelope> envelope = read(filePath);

        if (!envelope.isPresent()) {
            return ResponseResult.readError("Response file not found or could not be read: " + filePath);
        }

        ResponseEnvelope env = envelope.get();

        if (!env.isSuccess()) {
            return ResponseResult.fromFailure(env);
        }

        Object data = env.getData();
        if (data == null) {
            return ResponseResult.success(env, null);
        }

        if (dataType.isInstance(data)) {
            return ResponseResult.success(env, dataType.cast(data));
        }

        try {
            T typedData = objectMapper.convertValue(data, dataType);
            return ResponseResult.success(env, typedData);
        } catch (Exception e) {
            return ResponseResult.readError("Failed to convert response data to " + dataType.getSimpleName());
        }
    }

    /**
     * Represents a typed response result.
     *
     * @param <T> the data type
     */
    public static class ResponseResult<T> {
        private final boolean success;
        private final T data;
        private final String errorCode;
        private final String errorMessage;
        private final boolean recoverable;
        private final long durationMs;

        private ResponseResult(boolean success, T data, String errorCode, String errorMessage, boolean recoverable, long durationMs) {
            this.success = success;
            this.data = data;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.recoverable = recoverable;
            this.durationMs = durationMs;
        }

        public static <T> ResponseResult<T> success(ResponseEnvelope envelope, T data) {
            return new ResponseResult<>(true, data, null, null, false, envelope.getDurationMs());
        }

        public static <T> ResponseResult<T> fromFailure(ResponseEnvelope envelope) {
            String code = envelope.getError() != null ? envelope.getError().getCode() : "UNKNOWN_ERROR";
            String message = envelope.getError() != null ? envelope.getError().getMessage() : "Unknown error";
            boolean recoverable = envelope.getError() != null && envelope.getError().isRecoverable();
            return new ResponseResult<>(false, null, code, message, recoverable, envelope.getDurationMs());
        }

        public static <T> ResponseResult<T> readError(String message) {
            return new ResponseResult<>(false, null, "READ_ERROR", message, false, 0);
        }

        public boolean isSuccess() {
            return success;
        }

        public T getData() {
            return data;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isRecoverable() {
            return recoverable;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }
}
