/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.cloud.audit;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditTxType;

public class AuditEntryRequest {

    private final String stageId;
    private final String taskId;
    private final String author;
    private final long appliedAtEpochMillis;
    private final Status state;
    private final String className;
    private final String methodName;
    private final Object metadata;
    private final long executionMillis;
    private final String executionHostname;
    private final String errorTrace;
    private final ChangeType type;
    private final AuditTxType txStrategy;
    private final String targetSystemId;
    private final String order;
    private final RecoveryStrategy recoveryStrategy;
    private final Boolean transactionFlag;
    protected Boolean systemChange;//TODO not in server

    public AuditEntryRequest(String stageId,
                             String taskId,
                             String author,
                             long appliedAtEpochMillis,
                             Status state,
                             ChangeType type,
                             String className,
                             String methodName,
                             long executionMillis,
                             String executionHostname,
                             Object metadata,
                             boolean systemChange,
                             String errorTrace,
                             AuditTxType txStrategy,
                             String targetSystemId,
                             String order,
                             RecoveryStrategy recoveryStrategy,
                             Boolean transactionFlag) {
        this.stageId = stageId;
        this.taskId = taskId;
        this.author = author;
        this.appliedAtEpochMillis = appliedAtEpochMillis;
        this.state = state;
        this.className = className;
        this.methodName = methodName;
        this.metadata = metadata;
        this.executionMillis = executionMillis;
        this.executionHostname = executionHostname;
        this.errorTrace = errorTrace;
        this.type = type;
        this.txStrategy = txStrategy;
        this.targetSystemId = targetSystemId;
        this.order = order;
        this.recoveryStrategy = recoveryStrategy;
        this.transactionFlag = transactionFlag;

        this.systemChange = systemChange;
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

    public long getAppliedAtEpochMillis() {
        return appliedAtEpochMillis;
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

    public ChangeType getType() {
        return type;
    }

    public AuditTxType getTxStrategy() {
        return txStrategy;
    }

    public String getTargetSystemId() {
        return targetSystemId;
    }

    public String getOrder() {
        return order;
    }

    public RecoveryStrategy getRecoveryStrategy() {
        return recoveryStrategy;
    }

    public Boolean getTransactionFlag() {
        return transactionFlag;
    }

    public enum ChangeType {STANDARD_CODE, STANDARD_TEMPLATE, MONGOCK_EXECUTION, MONGOCK_BEFORE}

    public enum Status {
        STARTED,
        APPLIED,
        FAILED,
        ROLLED_BACK,
        ROLLBACK_FAILED,
        MANUAL_MARKED_AS_APPLIED,
        MANUAL_MARKED_AS_ROLLED_BACK
    }


}



