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
package io.flamingock.importer.mongock.mongodb;

import io.flamingock.internal.common.core.audit.AuditEntry;

import java.util.Date;

public class MongockAuditEntry {

    protected String executionId;
    protected String changeId;
    protected String author;
    protected Date timestamp;
    protected MongockChangeState state;
    protected MongockChangeType type;
    protected String changeLogClass;
    protected String changeSetMethod;
    protected Object metadata;
    protected long executionMillis;
    protected String executionHostname;
    protected String errorTrace;
    protected Boolean systemChange;
    protected Date originalTimestamp;


    public MongockAuditEntry(String executionId,
                             String changeId,
                             String author,
                             Date timestamp,
                             String state,
                             String type,
                             String changeLogClass,
                             String changeSetMethod,
                             Object metadata,
                             long executionMillis,
                             String executionHostname,
                             String errorTrace,
                             Boolean systemChange,
                             Date originalTimestamp) {
        this.executionId = executionId;
        this.changeId = changeId;
        this.author = author;
        this.timestamp = timestamp;
        this.state = MongockAuditEntry.MongockChangeState.valueOf(state);
        this.type = MongockChangeType.valueOf(type);
        this.changeLogClass = changeLogClass;
        this.changeSetMethod = changeSetMethod;
        this.metadata = metadata;
        this.executionMillis = executionMillis;
        this.executionHostname = executionHostname;
        this.errorTrace = errorTrace;
        this.systemChange = systemChange;
        this.originalTimestamp = originalTimestamp;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public AuditEntry.Status getState() {
        return state.toAuditStatus();
    }

    public void setState(String state) {
        this.state = MongockAuditEntry.MongockChangeState.valueOf(state);
    }

    public AuditEntry.ChangeType getType() {
        return type.toAuditType();
    }

    public void setType(String type) {
        this.type = MongockChangeType.valueOf(type);
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

    public Object getMetadata() {
        return metadata;
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    public long getExecutionMillis() {
        return executionMillis;
    }

    public void setExecutionMillis(long executionMillis) {
        this.executionMillis = executionMillis;
    }

    public String getExecutionHostname() {
        return executionHostname;
    }

    public void setExecutionHostname(String executionHostname) {
        this.executionHostname = executionHostname;
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

    public Date getOriginalTimestamp() {
        return originalTimestamp;
    }

    public void setOriginalTimestamp(Date originalTimestamp) {
        this.originalTimestamp = originalTimestamp;
    }

    public boolean shouldBeIgnored() {
        return state == MongockAuditEntry.MongockChangeState.IGNORED;
    }


    public enum MongockChangeState {
        EXECUTED, FAILED, ROLLED_BACK, ROLLBACK_FAILED, IGNORED;

        public AuditEntry.Status toAuditStatus() {
            switch (this) {
                case FAILED: return AuditEntry.Status.FAILED;
                case ROLLED_BACK: return AuditEntry.Status.ROLLED_BACK;
                case ROLLBACK_FAILED: return AuditEntry.Status.ROLLBACK_FAILED;
                case EXECUTED:
                default: return AuditEntry.Status.APPLIED;
            }
        }
    }

    public enum MongockChangeType {
        EXECUTION, BEFORE_EXECUTION;

        public AuditEntry.ChangeType toAuditType() {
            switch (this) {
                case BEFORE_EXECUTION: return AuditEntry.ChangeType.MONGOCK_BEFORE;
                case EXECUTION:
                default: return AuditEntry.ChangeType.MONGOCK_EXECUTION;
            }
        }
    }

}
