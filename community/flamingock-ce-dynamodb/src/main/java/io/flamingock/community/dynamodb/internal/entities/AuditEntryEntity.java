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
package io.flamingock.community.dynamodb.internal.entities;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditEntryField;
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
    private Object metadata;
    private Long executionMillis;
    private String executionHostname;
    private Object errorTrace;
    private AuditEntry.ExecutionType type;
    private AuditTxType txType;
    private String targetSystemId;
    private String order;

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
        this.metadata = auditEntry.getMetadata();
        this.executionMillis = auditEntry.getExecutionMillis();
        this.executionHostname = auditEntry.getExecutionHostname();
        this.errorTrace = auditEntry.getErrorTrace();
        this.type = auditEntry.getType();
        this.txType = auditEntry.getTxType();
        this.targetSystemId = auditEntry.getTargetSystemId();
        this.order = auditEntry.getOrder();
        this.systemChange = auditEntry.getSystemChange();
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

    @DynamoDbAttribute(AuditEntryField.KEY_CHANGE_ID)
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_STAGE_ID)
    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_EXECUTION_ID)
    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_AUTHOR)
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_TIMESTAMP)
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_STATE)
    public String getState() {
        return state.name();
    }

    public void setState(String state) {
        this.state = AuditEntry.Status.valueOf(state);
    }

    @DynamoDbAttribute(AuditEntryField.KEY_CHANGEUNIT_CLASS)
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_INVOKED_METHOD)
    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_METADATA)
    public String getMetadata() {
        return metadata.toString();
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_EXECUTION_MILLIS)
    public Long getExecutionMillis() {
        return executionMillis;
    }

    public void setExecutionMillis(Long executionMillis) {
        this.executionMillis = executionMillis;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_EXECUTION_HOSTNAME)
    public String getExecutionHostname() {
        return executionHostname;
    }

    public void setExecutionHostname(String executionHostname) {
        this.executionHostname = executionHostname;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_ERROR_TRACE)
    public String getErrorTrace() {
        return errorTrace.toString();
    }

    public void setErrorTrace(Object errorTrace) {
        this.errorTrace = errorTrace;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_TYPE)
    public String getType() {
        return type.name();
    }

    public void setType(String type) {
        this.type = AuditEntry.ExecutionType.valueOf(type);
    }

    @DynamoDbAttribute(AuditEntryField.KEY_SYSTEM_CHANGE)
    public Boolean getSystemChange() {
        return systemChange;
    }

    public void setSystemChange(Boolean systemChange) {
        this.systemChange = systemChange;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_TX_TYPE)
    public String getTxType() {
        return AuditTxType.safeString(txType);
    }

    public void setTxType(String txType) {
        this.txType = AuditTxType.fromString(txType);
    }

    @DynamoDbAttribute(AuditEntryField.KEY_TARGET_SYSTEM_ID)
    public String getTargetSystemId() {
        return targetSystemId;
    }

    public void setTargetSystemId(String targetSystemId) {
        this.targetSystemId = targetSystemId;
    }

    @DynamoDbAttribute(AuditEntryField.KEY_ORDER)
    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
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
                executionMillis,
                executionHostname,
                metadata,
                systemChange,
                Objects.toString(errorTrace, ""),
                txType,
                targetSystemId,
                order
        );
    }
}
