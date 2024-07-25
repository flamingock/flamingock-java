package io.flamingock.core.cloud.utils;

import io.flamingock.core.engine.audit.writer.AuditEntryStatus;

public class AuditEntryExpectation {
    private final String taskId;
    private final AuditEntryStatus state;
    private final String className;
    private final String methodName;


    public AuditEntryExpectation(String taskId, AuditEntryStatus state, String className, String methodName) {
        this.taskId = taskId;
        this.state = state;
        this.className = className;
        this.methodName = methodName;
    }

    public String getTaskId() {
        return taskId;
    }

    public AuditEntryStatus getState() {
        return state;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }
}
