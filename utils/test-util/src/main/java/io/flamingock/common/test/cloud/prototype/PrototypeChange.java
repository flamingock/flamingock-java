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
package io.flamingock.common.test.cloud.prototype;

import io.flamingock.common.test.cloud.deprecated.AuditEntryMatcher;
import io.flamingock.cloud.api.vo.CloudAuditStatus;
import io.flamingock.cloud.api.request.ChangeRequest;
import io.flamingock.cloud.api.vo.CloudChangeAction;
import io.flamingock.cloud.api.response.ChangeResponse;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;

public class PrototypeChange {
    private final String changeId;
    private final String className;
    private final boolean transactional;

    public PrototypeChange(String changeId, String className, String methodName, boolean transactional) {
        this.changeId = changeId;
        this.className = className;
        this.transactional = transactional;
    }

    public String getChangeId() {
        return changeId;
    }


    public String getClassName() {
        return className;
    }


    public boolean isTransactional() {
        return transactional;
    }

    public ChangeRequest toExecutionPlanChangeRequest(TargetSystemAuditMarkType ongoingStatus) {
        CloudTargetSystemAuditMarkType cloudStatus = ongoingStatus != null
                ? CloudTargetSystemAuditMarkType.valueOf(ongoingStatus.name())
                : CloudTargetSystemAuditMarkType.NONE;
        return new ChangeRequest(changeId, cloudStatus, transactional);
    }

    public ChangeRequest toExecutionPlanChangeRequest() {
        return new ChangeRequest(changeId, CloudTargetSystemAuditMarkType.NONE, transactional);
    }

    public ChangeResponse toExecutionPlanChangeResponse(CloudChangeAction state) {
        return new ChangeResponse(changeId, state != null ? state: CloudChangeAction.APPLY);
    }

    public ChangeResponse toResponse() {
        return new ChangeResponse(changeId, CloudChangeAction.APPLY);
    }

    public AuditEntryMatcher toAuditExpectation(CloudAuditStatus status) {
        return new AuditEntryMatcher(
                changeId,
                status,
                className,
                null,
                transactional
        );
    }

}
