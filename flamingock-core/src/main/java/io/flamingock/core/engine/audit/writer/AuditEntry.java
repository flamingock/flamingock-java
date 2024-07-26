/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.core.engine.audit.writer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.flamingock.core.cloud.api.audit.AuditEntryRequest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static io.flamingock.core.engine.audit.writer.AuditEntry.Status.EXECUTED;
import static io.flamingock.core.engine.audit.writer.AuditEntry.Status.EXECUTION_FAILED;
import static io.flamingock.core.engine.audit.writer.AuditEntry.Status.ROLLBACK_FAILED;
import static io.flamingock.core.engine.audit.writer.AuditEntry.Status.ROLLED_BACK;

public class AuditEntry {

    public static final Set<Status> RELEVANT_STATES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            EXECUTED,
            ROLLED_BACK,
            EXECUTION_FAILED,
            ROLLBACK_FAILED)));


    public enum Status {
        EXECUTED, EXECUTION_FAILED, ROLLED_BACK, ROLLBACK_FAILED;

        public static boolean isRequiredExecution(Status entryStatus) {
            return entryStatus == null || entryStatus == EXECUTION_FAILED || entryStatus == ROLLED_BACK || entryStatus == ROLLBACK_FAILED;
        }
        public AuditEntryRequest.Status toRequestStatus() {
            return AuditEntryRequest.Status.valueOf(name());
        }

    }

    public enum ExecutionType {
        EXECUTION, BEFORE_EXECUTION;

        public AuditEntryRequest.ExecutionType toRequestExecutionType() {
            return AuditEntryRequest.ExecutionType.valueOf(name());
        }
    }

    private final String executionId;

    private final String stageId;

    private final String taskId;

    private final String author;

    private final LocalDateTime createdAt;

    private final Status state;

    private final String className;

    private final String methodName;

    private final Object metadata;

    private final long executionMillis;

    private final String executionHostname;

    private final String errorTrace;

    private final ExecutionType type;

    protected Boolean systemChange;

    public AuditEntry(String executionId,
                      String stageId,
                      String taskId,
                      String author,
                      LocalDateTime timestamp,
                      Status state,
                      ExecutionType type,
                      String className,
                      String methodName,
                      long executionMillis,
                      String executionHostname,
                      Object metadata,
                      boolean systemChange,
                      String errorTrace) {
        this.executionId = executionId;
        this.stageId = stageId;
        this.taskId = taskId;
        this.author = author;
        this.createdAt = timestamp;
        this.state = state;
        this.className = className;
        this.methodName = methodName;
        this.metadata = metadata;
        this.executionMillis = executionMillis;
        this.executionHostname = executionHostname;
        this.errorTrace = errorTrace;
        this.type = type;

        this.systemChange = systemChange;
    }


    public String getExecutionId() {
        return executionId;
    }

    public String getStageId() {
        return stageId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getAuthor() {
        return author;
    }

    @JsonIgnore
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Status getState() {
        return state;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object getMetadata() {
        return metadata;
    }

    public long getExecutionMillis() {
        return executionMillis;
    }

    public String getExecutionHostname() {
        return executionHostname;
    }

    public String getErrorTrace() {
        return errorTrace;
    }

    public Boolean getSystemChange() {
        return systemChange;
    }

    public ExecutionType getType() {
        return type;
    }

    public static AuditEntry getMostRelevant(AuditEntry currentEntry, AuditEntry newEntry) {
        if (newEntry == null) {
            return currentEntry;
        } else if (currentEntry == null) {
            return newEntry;
        } else {
            return currentEntry.shouldBeReplacedBy(newEntry) ? newEntry : currentEntry;
        }
    }


    private boolean shouldBeReplacedBy(AuditEntry newEntry) {
        return RELEVANT_STATES.contains(newEntry.state) && newEntry.getCreatedAt().isAfter(this.getCreatedAt());
    }

}
