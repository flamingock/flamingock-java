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

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.time.LocalDateTime;

/**
 * Response data for the ISSUE_GET operation containing full issue details.
 */
@JsonTypeName("issue_get")
public class IssueGetResponseData {

    private String changeId;
    private String state;
    private String author;
    private LocalDateTime createdAt;
    private String errorTrace;
    private String targetSystemId;
    private String executionId;
    private long executionMillis;
    private String className;
    private String methodName;
    private String executionHostname;
    private String recoveryStrategy;
    private boolean found;

    public IssueGetResponseData() {
    }

    private IssueGetResponseData(Builder builder) {
        this.changeId = builder.changeId;
        this.state = builder.state;
        this.author = builder.author;
        this.createdAt = builder.createdAt;
        this.errorTrace = builder.errorTrace;
        this.targetSystemId = builder.targetSystemId;
        this.executionId = builder.executionId;
        this.executionMillis = builder.executionMillis;
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.executionHostname = builder.executionHostname;
        this.recoveryStrategy = builder.recoveryStrategy;
        this.found = builder.found;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static IssueGetResponseData notFound() {
        return new IssueGetResponseData.Builder().found(false).build();
    }

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getErrorTrace() {
        return errorTrace;
    }

    public void setErrorTrace(String errorTrace) {
        this.errorTrace = errorTrace;
    }

    public String getTargetSystemId() {
        return targetSystemId;
    }

    public void setTargetSystemId(String targetSystemId) {
        this.targetSystemId = targetSystemId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public long getExecutionMillis() {
        return executionMillis;
    }

    public void setExecutionMillis(long executionMillis) {
        this.executionMillis = executionMillis;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getExecutionHostname() {
        return executionHostname;
    }

    public void setExecutionHostname(String executionHostname) {
        this.executionHostname = executionHostname;
    }

    public String getRecoveryStrategy() {
        return recoveryStrategy;
    }

    public void setRecoveryStrategy(String recoveryStrategy) {
        this.recoveryStrategy = recoveryStrategy;
    }

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public static class Builder {
        private String changeId;
        private String state;
        private String author;
        private LocalDateTime createdAt;
        private String errorTrace;
        private String targetSystemId;
        private String executionId;
        private long executionMillis;
        private String className;
        private String methodName;
        private String executionHostname;
        private String recoveryStrategy;
        private boolean found = true;

        public Builder changeId(String changeId) {
            this.changeId = changeId;
            return this;
        }

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder errorTrace(String errorTrace) {
            this.errorTrace = errorTrace;
            return this;
        }

        public Builder targetSystemId(String targetSystemId) {
            this.targetSystemId = targetSystemId;
            return this;
        }

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder executionMillis(long executionMillis) {
            this.executionMillis = executionMillis;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public Builder executionHostname(String executionHostname) {
            this.executionHostname = executionHostname;
            return this;
        }

        public Builder recoveryStrategy(String recoveryStrategy) {
            this.recoveryStrategy = recoveryStrategy;
            return this;
        }

        public Builder found(boolean found) {
            this.found = found;
            return this;
        }

        public IssueGetResponseData build() {
            return new IssueGetResponseData(this);
        }
    }
}
