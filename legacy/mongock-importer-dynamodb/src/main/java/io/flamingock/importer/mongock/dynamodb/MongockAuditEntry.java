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
package io.flamingock.importer.mongock.dynamodb;

import io.flamingock.importer.mongodb.MongockChangeState;
import io.flamingock.internal.common.core.audit.AuditEntry;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;


@DynamoDbBean
public class MongockAuditEntry {
    private String executionId;
    private String changeId;
    private String author;
    private String timestamp;
    private String state;
    private String type;
    private String changeLogClass;
    private String changeSetMethod;
    private String metadata;
    private Long executionMillis;
    private String executionHostName;
    private String errorTrace;
    private Boolean systemChange;

    public MongockAuditEntry() {
    }

    public MongockAuditEntry(
            String executionId,
            String changeId,
            String author,
            String timestamp,
            String state,
            String type,
            String changeLogClass,
            String changeSetMethod,
            String metadata,
            Long executionMillis,
            String executionHostName,
            String errorTrace,
            Boolean systemChange
    ) {
        this.executionId = executionId;
        this.changeId = changeId;
        this.author = author;
        this.timestamp = timestamp;
        this.state = state;
        this.type = type;
        this.changeLogClass = changeLogClass;
        this.changeSetMethod = changeSetMethod;
        this.metadata = metadata;
        this.executionMillis = executionMillis;
        this.executionHostName = executionHostName;
        this.errorTrace = errorTrace;
        this.systemChange = systemChange;
    }

    @DynamoDbPartitionKey
    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getChangeLogClass() {
        return changeLogClass;
    }

    public void setChangeLogClass(String changeLogClass) {
        this.changeLogClass = changeLogClass;
    }

    public String getChangeSetMethod() {
        return changeSetMethod;
    }

    public void setChangeSetMethod(String changeSetMethod) {
        this.changeSetMethod = changeSetMethod;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Long getExecutionMillis() {
        return executionMillis;
    }

    public void setExecutionMillis(Long executionMillis) {
        this.executionMillis = executionMillis;
    }

    public String getExecutionHostName() {
        return executionHostName;
    }

    public void setExecutionHostName(String executionHostName) {
        this.executionHostName = executionHostName;
    }

    public String getErrorTrace() {
        return errorTrace;
    }

    public void setErrorTrace(String errorTrace) {
        this.errorTrace = errorTrace;
    }

    public Boolean getSystemChange() {
        return systemChange;
    }

    public void setSystemChange(Boolean systemChange) {
        this.systemChange = systemChange;
    }

    public AuditEntry toAuditEntry() {
        long epochMillis;
        try {
            epochMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            String ts = timestamp;
            if (ts.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")) {
                ts = ts + "Z";
            }
            epochMillis = Instant.parse(ts).toEpochMilli();
        }
        LocalDateTime ts = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());

        MongockChangeState stateEnum = MongockChangeState.valueOf(state);
        return new AuditEntry(
                executionId,
                null,
                changeId,
                author,
                ts,
                stateEnum.toAuditStatus(),
                AuditEntry.ExecutionType.valueOf(type),
                changeLogClass,
                changeSetMethod,
                executionMillis,
                executionHostName,
                metadata,
                systemChange != null && systemChange,
                errorTrace
        );
    }
}
