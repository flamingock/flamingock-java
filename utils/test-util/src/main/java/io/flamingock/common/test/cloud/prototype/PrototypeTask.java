/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
import io.flamingock.internal.common.cloud.audit.AuditEntryRequest;
import io.flamingock.internal.common.cloud.planner.request.TaskRequest;
import io.flamingock.internal.common.cloud.planner.response.RequiredActionTask;
import io.flamingock.internal.common.cloud.planner.response.TaskResponse;
import io.flamingock.internal.common.cloud.vo.OngoingStatus;

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

    public TaskRequest toExecutionPlanTaskRequest(OngoingStatus ongoingStatus) {
        return new TaskRequest(
                taskId,
                ongoingStatus != null ? ongoingStatus : OngoingStatus.NONE,
                transactional
        );
    }

    public TaskRequest toExecutionPlanTaskRequest() {
        return new TaskRequest(
                taskId,
                OngoingStatus.NONE,
                transactional
        );
    }

    public TaskResponse toExecutionPlanTaskResponse(RequiredActionTask state) {
        return new TaskResponse(taskId, state != null ? state: RequiredActionTask.PENDING_EXECUTION);
    }

    public TaskResponse toResponse() {
        return new TaskResponse(taskId, RequiredActionTask.PENDING_EXECUTION);
    }

    public AuditEntryMatcher toAuditExpectation(AuditEntryRequest.Status status) {
        return new AuditEntryMatcher(
                taskId,
                status,
                className,
                null,
                transactional
        );
    }

}
