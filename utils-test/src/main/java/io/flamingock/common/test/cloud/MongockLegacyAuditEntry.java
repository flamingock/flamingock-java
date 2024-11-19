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

package io.flamingock.common.test.cloud;

public class MongockLegacyAuditEntry {
    private final Object _id;
    private final String executionId;
    private final String changeId;
    private final String state;
    private final String type;
    private final String author;
    private final Object timestamp;
    private final String changeLogClass;
    private final String changeSetMethod;
    private final Object metadata;
    private final Long executionMillis;
    private final String executionHostname;
    private final String errorTrace;
    private final boolean systemChange;

    public MongockLegacyAuditEntry(Object id, String executionId, String changeId, String state, String type, String author, Object timestamp, String changeLogClass, String changeSetMethod, Object metadata, Long executionMillis, String executionHostname, String errorTrace, boolean systemChange) {
        this._id = id;
        this.executionId = executionId;
        this.changeId = changeId;
        this.state = state;
        this.type = type;
        this.author = author;
        this.timestamp = timestamp;
        this.changeLogClass = changeLogClass;
        this.changeSetMethod = changeSetMethod;
        this.metadata = metadata;
        this.executionMillis = executionMillis;
        this.executionHostname = executionHostname;
        this.systemChange = systemChange;
        this.errorTrace = errorTrace;
    }


    public String getExecutionId() {
        return executionId;
    }

    public String getChangeId() {
        return changeId;
    }

    public String getState() {
        return state;
    }

    public String getType() {
        return type;
    }

    public String getAuthor() {
        return author;
    }

    public Object getTimestamp() {
        return timestamp;
    }

    public String getChangeLogClass() {
        return changeLogClass;
    }

    public String getChangeSetMethod() {
        return changeSetMethod;
    }

    public Object getMetadata() {
        return metadata;
    }

    public Long getExecutionMillis() {
        return executionMillis;
    }

    public String getExecutionHostname() {
        return executionHostname;
    }

    public String getErrorTrace() {
        return errorTrace;
    }

    public boolean isSystemChange() {
        return systemChange;
    }

    public Object get_id() {
        return _id;
    }
}
