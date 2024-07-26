package io.flamingock.common.test.cloud;


import io.flamingock.core.cloud.api.audit.AuditEntryRequest;

public class AuditEntryExpectation {
    private final String taskId;
    private final AuditEntryRequest.Status state;
    private final String className;
    private final String methodName;


    public AuditEntryExpectation(String taskId, AuditEntryRequest.Status state, String className, String methodName) {
        this.taskId = taskId;
        this.state = state;
        this.className = className;
        this.methodName = methodName;
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

    public String getMethodName() {
        return methodName;
    }
}
