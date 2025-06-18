package io.flamingock.common.test.cloud;

import io.flamingock.internal.common.cloud.audit.AuditEntryRequest;

public class AuditRequestExpectation {
    private final String taskId;
    private final AuditEntryRequest.Status state;
    private final String executionId;

    public AuditRequestExpectation(String executionId, String taskId, AuditEntryRequest.Status state) {
        this.executionId = executionId;
        this.taskId = taskId;
        this.state = state;
    }

    public String getTaskId() {
        return taskId;
    }

    public AuditEntryRequest.Status getState() {
        return state;
    }

    public String getExecutionId() {
        return executionId;
    }
}
