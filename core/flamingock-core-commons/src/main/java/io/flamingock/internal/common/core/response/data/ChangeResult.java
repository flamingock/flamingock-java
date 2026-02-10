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

/**
 * Result data for an individual change execution.
 */
public class ChangeResult {

    private String changeId;
    private String author;
    private ChangeStatus status;
    private long durationMs;
    private String targetSystemId;
    private String errorMessage;
    private String errorType;

    public ChangeResult() {
    }

    private ChangeResult(Builder builder) {
        this.changeId = builder.changeId;
        this.author = builder.author;
        this.status = builder.status;
        this.durationMs = builder.durationMs;
        this.targetSystemId = builder.targetSystemId;
        this.errorMessage = builder.errorMessage;
        this.errorType = builder.errorType;
    }

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public ChangeStatus getStatus() {
        return status;
    }

    public void setStatus(ChangeStatus status) {
        this.status = status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getTargetSystemId() {
        return targetSystemId;
    }

    public void setTargetSystemId(String targetSystemId) {
        this.targetSystemId = targetSystemId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public boolean isFailed() {
        return status == ChangeStatus.FAILED;
    }

    public boolean isApplied() {
        return status == ChangeStatus.APPLIED;
    }

    public boolean isAlreadyApplied() {
        return status == ChangeStatus.ALREADY_APPLIED;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String changeId;
        private String author;
        private ChangeStatus status;
        private long durationMs;
        private String targetSystemId;
        private String errorMessage;
        private String errorType;

        public Builder changeId(String changeId) {
            this.changeId = changeId;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder status(ChangeStatus status) {
            this.status = status;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder targetSystemId(String targetSystemId) {
            this.targetSystemId = targetSystemId;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder error(Throwable throwable) {
            if (throwable != null) {
                this.errorType = throwable.getClass().getSimpleName();
                this.errorMessage = throwable.getMessage();
            }
            return this;
        }

        public ChangeResult build() {
            return new ChangeResult(this);
        }
    }
}
