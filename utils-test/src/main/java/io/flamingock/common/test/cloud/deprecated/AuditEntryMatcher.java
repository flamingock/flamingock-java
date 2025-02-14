package io.flamingock.common.test.cloud.deprecated;


import io.flamingock.core.cloud.api.audit.AuditEntryRequest;

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
