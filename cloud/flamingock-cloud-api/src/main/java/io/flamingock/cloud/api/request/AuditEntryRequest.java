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
package io.flamingock.cloud.api.request;

import io.flamingock.cloud.api.vo.CloudRecoveryStrategy;
import io.flamingock.cloud.api.vo.CloudAuditStatus;
import io.flamingock.cloud.api.vo.CloudChangeType;
import io.flamingock.cloud.api.vo.CloudTxStrategy;

public class AuditEntryRequest {

    private String changeId;
    private String stageId;
    private String author;
    private long appliedAtEpochMillis;
    private CloudAuditStatus state;
    private String className;
    private String methodName;
    private Object metadata;
    private long executionMillis;
    private String executionHostname;
    private String errorTrace;
    private CloudChangeType type;
    private CloudTxStrategy txStrategy;
    private String targetSystemId;
    private String order;
    private CloudRecoveryStrategy recoveryStrategy;
    private Boolean transactionFlag;
    protected Boolean systemChange;//TODO not in server

    public AuditEntryRequest() {
    }

    public AuditEntryRequest(String stageId,
                             String changeId,
                             String author,
                             long appliedAtEpochMillis,
                             CloudAuditStatus state,
                             CloudChangeType type,
                             String className,
                             String methodName,
                             long executionMillis,
                             String executionHostname,
                             Object metadata,
                             boolean systemChange,
                             String errorTrace,
                             CloudTxStrategy txStrategy,
                             String targetSystemId,
                             String order,
                             CloudRecoveryStrategy recoveryStrategy,
                             Boolean transactionFlag) {
        this.stageId = stageId;
        this.changeId = changeId;
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

    public String getChangeId() {
        return changeId;
    }

    public String getAuthor() {
        return author;
    }

    public long getAppliedAtEpochMillis() {
        return appliedAtEpochMillis;
    }

    public CloudAuditStatus getState() {
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

    public CloudChangeType getType() {
        return type;
    }

    public CloudTxStrategy getTxStrategy() {
        return txStrategy;
    }

    public String getTargetSystemId() {
        return targetSystemId;
    }

    public String getOrder() {
        return order;
    }

    public CloudRecoveryStrategy getRecoveryStrategy() {
        return recoveryStrategy;
    }

    public Boolean getTransactionFlag() {
        return transactionFlag;
    }

}
