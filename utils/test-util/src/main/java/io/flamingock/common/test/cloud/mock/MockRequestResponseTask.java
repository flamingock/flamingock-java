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
package io.flamingock.common.test.cloud.mock;

import io.flamingock.internal.common.cloud.planner.response.RequiredActionTask;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;

public class MockRequestResponseTask {
    private final String taskId;
    private final TargetSystemAuditMarkType ongoingStatus;
    private final RequiredActionTask requiredAction;


    public MockRequestResponseTask(String taskId,
                                   TargetSystemAuditMarkType ongoingStatus) {
        this(taskId, ongoingStatus,  RequiredActionTask.PENDING_EXECUTION);
    }

    public MockRequestResponseTask(String taskId,
                                   RequiredActionTask requiredAction) {
        this(taskId, TargetSystemAuditMarkType.NONE, requiredAction);
    }

    public MockRequestResponseTask(String taskId,
                                   TargetSystemAuditMarkType ongoingStatus,
                                   RequiredActionTask requiredAction) {
        this.taskId = taskId;
        this.ongoingStatus = ongoingStatus;
        this.requiredAction = requiredAction;
    }

    public String getTaskId() {
        return taskId;
    }

    public TargetSystemAuditMarkType getOngoingStatus() {
        return ongoingStatus;
    }

    public RequiredActionTask getRequiredAction() {
        return requiredAction;
    }

}
