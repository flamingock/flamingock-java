/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.flamingock.internal.common.cloud.audit.AuditEntryRequest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.flamingock.internal.common.core.audit.AuditEntry.Status.EXECUTED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.EXECUTION_FAILED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.ROLLBACK_FAILED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.ROLLED_BACK;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.STARTED;

public class AuditEntry {


    private static final List<Status> STATUS_ORDER = Arrays.asList(
            ROLLBACK_FAILED, ROLLED_BACK, EXECUTION_FAILED, EXECUTED, STARTED

    );

    protected final Boolean systemChange;
    private final String executionId;
    private final String stageId;
    //TODO move to changeId
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
    private final AuditTxType txType;
    private final String targetSystemId;

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
                      String errorTrace,
                      AuditTxType txType,
                      String targetSystemId) {
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
        this.txType = txType != null ? txType : AuditTxType.NON_TX;
        this.targetSystemId = targetSystemId;
        this.systemChange = systemChange;
    }

    /**
     * Backward compatible constructor without targetSystemId.
     * @deprecated Use constructor with targetSystemId parameter instead.
     */
    @Deprecated
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
                      String errorTrace,
                      AuditTxType txType) {
        this(executionId, stageId, taskId, author, timestamp, state, type, className, methodName, 
             executionMillis, executionHostname, metadata, systemChange, errorTrace, txType, null);
    }

    /**
     * Backward compatible constructor without OperationType and targetSystemId.
     * @deprecated Use constructor with OperationType and targetSystemId parameters instead.
     */
    @Deprecated
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
        this(executionId, stageId, taskId, author, timestamp, state, type, className, methodName, 
             executionMillis, executionHostname, metadata, systemChange, errorTrace, null, null);
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

    public AuditTxType getTxType() {
        return txType;
    }

    public String getTargetSystemId() {
        return targetSystemId;
    }


    private boolean shouldBeReplacedBy(AuditEntry newEntry) {
        if(this.getState().equals(newEntry.getState())) {
            return newEntry.getCreatedAt().isAfter(this.createdAt);
        }

        if(!newEntry.getCreatedAt().equals(createdAt)) {
            return newEntry.getCreatedAt().isAfter(this.createdAt);
        } else {
            return newEntry.getState().hasHigherPriorityThan(this.state);
        }
    }

    public AuditEntry copyWithNewIdAndStageId(String id, String stageId) {
        return new AuditEntry(
                getExecutionId(),
                stageId,
                id,
                getAuthor(),
                getCreatedAt(),
                getState(),
                getType(),
                getClassName(),
                getMethodName(),
                getExecutionMillis(),
                getExecutionHostname(),
                getMetadata(),
                getSystemChange(),
                getErrorTrace(),
                getTxType(),
                getTargetSystemId()
        );
    }

    public enum Status {
        STARTED(1), EXECUTED(2), EXECUTION_FAILED(3), ROLLED_BACK(4), ROLLBACK_FAILED(5);

        private final int priority;

        Status(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }

        public boolean hasHigherPriorityThan(Status other) {
            return this.priority > other.priority;
        }



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


}
