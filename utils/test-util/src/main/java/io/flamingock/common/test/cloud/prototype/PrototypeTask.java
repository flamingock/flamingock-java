package io.flamingock.common.test.cloud.prototype;

import io.flamingock.common.test.cloud.deprecated.AuditEntryMatcher;
import io.flamingock.core.cloud.api.audit.AuditEntryRequest;
import io.flamingock.core.cloud.api.planner.request.TaskRequest;
import io.flamingock.core.cloud.api.planner.response.RequiredActionTask;
import io.flamingock.core.cloud.api.planner.response.TaskResponse;
import io.flamingock.core.cloud.api.vo.OngoingStatus;

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
