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
package io.flamingock.common.test.cloud.deprecated;


import io.flamingock.internal.common.cloud.audit.AuditEntryRequest;

import java.beans.Transient;

@Deprecated
public class AuditEntryMatcher {
    private final String taskId;
    private final AuditEntryRequest.Status state;
    private final String className;
    private final String methodName;
    private final boolean transactional;

    public AuditEntryMatcher(String taskId, AuditEntryRequest.Status state, String className, String methodName) {
        this(taskId, state, className, methodName, true);
    }

    public AuditEntryMatcher(String taskId, AuditEntryRequest.Status state, String className, String methodName, boolean transactional) {
        this.taskId = taskId;
        this.state = state;
        this.className = className;
        this.methodName = methodName;
        this.transactional = transactional;
    }

    public String getTaskId() {
        return taskId;
    }

    public AuditEntryRequest.Status getState() {
        return state;
    }

    public String getClassName() {
        return className;
    }

    //TODO TO BE DELETED
    @Transient
    public String getMethodName() {
        return methodName;
    }

    @Transient
    public boolean isTransactional() {
        return transactional;
    }

}
