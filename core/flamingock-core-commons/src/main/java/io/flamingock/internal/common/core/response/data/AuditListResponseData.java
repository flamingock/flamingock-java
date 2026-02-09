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
import java.util.ArrayList;
import java.util.List;

/**
 * Response data for the LIST operation containing audit entries.
 */
@JsonTypeName("audit_list")
public class AuditListResponseData {

    private List<AuditEntryDto> entries;

    public AuditListResponseData() {
        this.entries = new ArrayList<>();
    }

    public AuditListResponseData(List<AuditEntryDto> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public List<AuditEntryDto> getEntries() {
        return entries;
    }

    public void setEntries(List<AuditEntryDto> entries) {
        this.entries = entries;
    }

    /**
     * DTO representing a single audit entry for CLI response.
     */
    public static class AuditEntryDto {
        private String taskId;
        private String author;
        private String state;
        private String stageId;
        private LocalDateTime createdAt;
        private long executionMillis;

        // Extended fields (nullable for non-extended mode)
        private String executionId;
        private String className;
        private String methodName;
        private String executionHostname;
        private String targetSystemId;

        public AuditEntryDto() {
        }

        public AuditEntryDto(String taskId, String author, String state, String stageId, LocalDateTime createdAt, long executionMillis) {
            this.taskId = taskId;
            this.author = author;
            this.state = state;
            this.stageId = stageId;
            this.createdAt = createdAt;
            this.executionMillis = executionMillis;
        }

        public AuditEntryDto(String taskId, String author, String state, String stageId, LocalDateTime createdAt, long executionMillis,
                             String executionId, String className, String methodName, String executionHostname, String targetSystemId) {
            this.taskId = taskId;
            this.author = author;
            this.state = state;
            this.stageId = stageId;
            this.createdAt = createdAt;
            this.executionMillis = executionMillis;
            this.executionId = executionId;
            this.className = className;
            this.methodName = methodName;
            this.executionHostname = executionHostname;
            this.targetSystemId = targetSystemId;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getStageId() {
            return stageId;
        }

        public void setStageId(String stageId) {
            this.stageId = stageId;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public long getExecutionMillis() {
            return executionMillis;
        }

        public void setExecutionMillis(long executionMillis) {
            this.executionMillis = executionMillis;
        }

        public String getExecutionId() {
            return executionId;
        }

        public void setExecutionId(String executionId) {
            this.executionId = executionId;
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

        public String getTargetSystemId() {
            return targetSystemId;
        }

        public void setTargetSystemId(String targetSystemId) {
            this.targetSystemId = targetSystemId;
        }
    }
}
