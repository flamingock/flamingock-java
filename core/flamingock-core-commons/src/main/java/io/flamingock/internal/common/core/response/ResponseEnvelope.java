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

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Envelope for operation results communicated between the spawned application
 * and the CLI executor.
 */
public class ResponseEnvelope {

    private boolean success;
    private String operation;
    private Instant timestamp;
    private long durationMs;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
    private Object data;

    private ResponseError error;

    public ResponseEnvelope() {
    }

    private ResponseEnvelope(boolean success, String operation, Instant timestamp, long durationMs, Object data, ResponseError error) {
        this.success = success;
        this.operation = operation;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.data = data;
        this.error = error;
    }

    public static ResponseEnvelope success(String operation, Object data, long durationMs) {
        return new ResponseEnvelope(true, operation, Instant.now(), durationMs, data, null);
    }

    public static ResponseEnvelope failure(String operation, ResponseError error, long durationMs) {
        return new ResponseEnvelope(false, operation, Instant.now(), durationMs, null, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public ResponseError getError() {
        return error;
    }

    public void setError(ResponseError error) {
        this.error = error;
    }
}
