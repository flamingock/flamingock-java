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

package io.flamingock.community.dynamodb.internal.entities;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.community.Constants;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.LocalDateTime;
import java.util.Objects;


@DynamoDbBean
public class AuditEntryEntity {

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
        this.systemChange = auditEntry.getSystemChange();
    }

    public AuditEntryEntity() {
    }

    public static String partitionKey(String executionId, String taskId, AuditEntry.Status state) {
        return executionId + '#' + taskId + '#' + state.name();
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute(Constants.AUDIT_LOG_PK)
    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    @DynamoDbAttribute(Constants.KEY_CHANGE_ID)
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @DynamoDbAttribute(Constants.AUDIT_LOG_STAGE_ID)
    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    @DynamoDbAttribute(Constants.KEY_EXECUTION_ID)
    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    @DynamoDbAttribute(Constants.KEY_AUTHOR)
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @DynamoDbAttribute(Constants.KEY_TIMESTAMP)
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute(Constants.KEY_STATE)
    public String getState() {
        return state.name();
    }

    public void setState(String state) {
        this.state = AuditEntry.Status.valueOf(state);
    }

    @DynamoDbAttribute(Constants.KEY_CHANGEUNIT_CLASS)
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @DynamoDbAttribute(Constants.KEY_INVOKED_METHOD)
    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    @DynamoDbAttribute(Constants.KEY_METADATA)
    public String getMetadata() {
        return metadata.toString();
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    @DynamoDbAttribute(Constants.KEY_EXECUTION_MILLIS)
    public Long getExecutionMillis() {
        return executionMillis;
    }

    public void setExecutionMillis(Long executionMillis) {
        this.executionMillis = executionMillis;
    }

    @DynamoDbAttribute(Constants.KEY_EXECUTION_HOSTNAME)
    public String getExecutionHostname() {
        return executionHostname;
    }

    public void setExecutionHostname(String executionHostname) {
        this.executionHostname = executionHostname;
    }

    @DynamoDbAttribute(Constants.KEY_ERROR_TRACE)
    public String getErrorTrace() {
        return errorTrace.toString();
    }

    public void setErrorTrace(Object errorTrace) {
        this.errorTrace = errorTrace;
    }

    @DynamoDbAttribute(Constants.KEY_TYPE)
    public String getType() {
        return type.name();
    }

    public void setType(String type) {
        this.type = AuditEntry.ExecutionType.valueOf(type);
    }

    @DynamoDbAttribute(Constants.KEY_SYSTEM_CHANGE)
    public Boolean getSystemChange() {
        return systemChange;
    }

    public void setSystemChange(Boolean systemChange) {
        this.systemChange = systemChange;
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
                Objects.toString(errorTrace, "")
        );
    }
}
