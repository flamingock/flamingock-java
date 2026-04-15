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


import io.flamingock.cloud.api.vo.CloudAuditStatus;

import java.beans.Transient;

@Deprecated
public class AuditEntryMatcher {
    private final String changeId;
    private final CloudAuditStatus state;
    private final String className;
    private final String methodName;
    private final boolean transactional;

    public AuditEntryMatcher(String changeId, CloudAuditStatus state, String className, String methodName) {
        this(changeId, state, className, methodName, true);
    }

    public AuditEntryMatcher(String changeId, CloudAuditStatus state, String className, String methodName, boolean transactional) {
        this.changeId = changeId;
        this.state = state;
        this.className = className;
        this.methodName = methodName;
        this.transactional = transactional;
    }

    public String getChangeId() {
        return changeId;
    }

    public CloudAuditStatus getState() {
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
