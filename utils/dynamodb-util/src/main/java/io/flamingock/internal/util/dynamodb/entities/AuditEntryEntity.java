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
package io.flamingock.internal.util.dynamodb.entities;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.util.constants.AuditEntryFieldConstants;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.dynamodb.DynamoDBConstants;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.LocalDateTime;
import java.util.Objects;


@DynamoDbBean
public class AuditEntryEntity implements Comparable<AuditEntryEntity> {

    protected Boolean systemChange;
    private String partitionKey;
    private String taskId;
    private String stageId;
    private String executionId;
    private String author;
    private LocalDateTime createdAt;
    private AuditEntry.Status state;
    private String className;
    private String methodName;
    private String sourceFile;
    private Object metadata;
    private Long executionMillis;
    private String executionHostname;
    private Object errorTrace;
    private AuditEntry.ExecutionType type;
    private AuditTxType txStrategy;
    private String targetSystemId;
    private String order;
    private String recoveryStrategy;
    private Boolean transactionFlag;

    public static AuditEntryEntity fromAuditEntry(AuditEntry auditEntry) {
        return new AuditEntryEntity(auditEntry);
    }

    public AuditEntryEntity(AuditEntry auditEntry) {
        this.partitionKey = partitionKey(auditEntry.getExecutionId(), auditEntry.getTaskId(), auditEntry.getState());
        this.taskId = auditEntry.getTaskId();
        this.stageId = auditEntry.getStageId();
        this.executionId = auditEntry.getExecutionId();
        this.author = auditEntry.getAuthor();
        this.createdAt = auditEntry.getCreatedAt();
        this.state = auditEntry.getState();
        this.className = auditEntry.getClassName();
        this.methodName = auditEntry.getMethodName();
        this.sourceFile = auditEntry.getSourceFile();
        this.metadata = auditEntry.getMetadata();
        this.executionMillis = auditEntry.getExecutionMillis();
        this.executionHostname = auditEntry.getExecutionHostname();
        this.errorTrace = auditEntry.getErrorTrace();
        this.type = auditEntry.getType();
        this.txStrategy = auditEntry.getTxType();
        this.targetSystemId = auditEntry.getTargetSystemId();
        this.order = auditEntry.getOrder();
        this.recoveryStrategy = auditEntry.getRecoveryStrategy().name();
        this.systemChange = auditEntry.getSystemChange();
        this.transactionFlag = auditEntry.getTransactionFlag();
    }

    public AuditEntryEntity() {
    }

    public static String partitionKey(String executionId, String taskId, AuditEntry.Status state) {
        return executionId + '#' + taskId + '#' + state.name();
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute(DynamoDBConstants.AUDIT_LOG_PK)
    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_CHANGE_ID)
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_STAGE_ID)
    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_EXECUTION_ID)
    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_AUTHOR)
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_CREATED_AT)
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_STATE)
    public String getState() {
        return state.name();
    }

    public void setState(String state) {
        this.state = AuditEntry.Status.valueOf(state);
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_INVOKED_CLASS)
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_INVOKED_METHOD)
    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_SOURCE_FILE)
    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_METADATA)
    public String getMetadata() {
        return metadata.toString();
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_EXECUTION_MILLIS)
    public Long getExecutionMillis() {
        return executionMillis;
    }

    public void setExecutionMillis(Long executionMillis) {
        this.executionMillis = executionMillis;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_EXECUTION_HOSTNAME)
    public String getExecutionHostname() {
        return executionHostname;
    }

    public void setExecutionHostname(String executionHostname) {
        this.executionHostname = executionHostname;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_ERROR_TRACE)
    public String getErrorTrace() {
        return errorTrace.toString();
    }

    public void setErrorTrace(Object errorTrace) {
        this.errorTrace = errorTrace;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_TYPE)
    public String getType() {
        return type.name();
    }

    public void setType(String type) {
        this.type = AuditEntry.ExecutionType.valueOf(type);
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_SYSTEM_CHANGE)
    public Boolean getSystemChange() {
        return systemChange;
    }

    public void setSystemChange(Boolean systemChange) {
        this.systemChange = systemChange;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_TX_STRATEGY)
    public String getTxType() {
        return AuditTxType.safeString(txStrategy);
    }

    public void setTxType(String txStrategy) {
        this.txStrategy = AuditTxType.fromString(txStrategy);
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_TARGET_SYSTEM_ID)
    public String getTargetSystemId() {
        return targetSystemId;
    }

    public void setTargetSystemId(String targetSystemId) {
        this.targetSystemId = targetSystemId;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_CHANGE_ORDER)
    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_RECOVERY_STRATEGY)
    public String getRecoveryStrategy() {
        return recoveryStrategy;
    }

    public void setRecoveryStrategy(String recoveryStrategy) {
        this.recoveryStrategy = recoveryStrategy;
    }

    @DynamoDbAttribute(AuditEntryFieldConstants.KEY_TRANSACTION_FLAG)
    public Boolean getTransactionFlag() {
        return transactionFlag;
    }

    public void setTransactionFlag(Boolean transactionFlag) {
        this.transactionFlag = transactionFlag;
    }

    @Override
    public int compareTo(AuditEntryEntity other) {
        if (other == null) {
            return 1;
        }
        
        // Level 1: Sort by createdAt timestamp
        int timeComparison = this.createdAt.compareTo(other.createdAt);
        if (timeComparison != 0) {
            return timeComparison;
        }
        
        // Level 2: Sort by order (if timestamps are equal)
        if (this.order != null && other.order != null) {
            int orderComparison = this.order.compareTo(other.order);
            if (orderComparison != 0) {
                return orderComparison;
            }
        }
        
        // Level 3: Sort by state priority (if timestamps and order are equal)
        return Integer.compare(this.state.getPriority(), other.state.getPriority());
    }

    public AuditEntry toAuditEntry() {
        return new AuditEntry(
                executionId,
                stageId,
                taskId,
                author,
                createdAt,
                state,
                type,
                className,
                methodName,
                sourceFile,
                executionMillis,
                executionHostname,
                metadata,
                systemChange,
                Objects.toString(errorTrace, ""),
                txStrategy,
                targetSystemId,
                order,
                recoveryStrategy != null ? RecoveryStrategy.valueOf(recoveryStrategy) : RecoveryStrategy.MANUAL_INTERVENTION,
                transactionFlag
        );
    }
}
