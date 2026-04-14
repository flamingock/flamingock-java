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

public class PrototypeTask {
    private final String taskId;
    private final String className;
    private final boolean transactional;

    public PrototypeTask(String taskId, String className, String methodName, boolean transactional) {
        this.taskId = taskId;
        this.className = className;
        this.transactional = transactional;
    }

    public String getTaskId() {
        return taskId;
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
        return new ChangeRequest(taskId, cloudStatus, transactional);
    }

    public ChangeRequest toExecutionPlanChangeRequest() {
        return new ChangeRequest(taskId, CloudTargetSystemAuditMarkType.NONE, transactional);
    }

    public ChangeResponse toExecutionPlanChangeResponse(CloudChangeAction state) {
        return new ChangeResponse(taskId, state != null ? state: CloudChangeAction.APPLY);
    }

    public ChangeResponse toResponse() {
        return new ChangeResponse(taskId, CloudChangeAction.APPLY);
    }

    public AuditEntryMatcher toAuditExpectation(CloudAuditStatus status) {
        return new AuditEntryMatcher(
                taskId,
                status,
                className,
                null,
                transactional
        );
    }

}
