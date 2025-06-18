package io.flamingock.internal.common.cloud.audit;

public class AuditEntryRequest {

    public enum ExecutionType {EXECUTION, BEFORE_EXECUTION}

    public enum Status {
        STARTED, EXECUTED, EXECUTION_FAILED, ROLLED_BACK, ROLLBACK_FAILED;
    }

    private final String stageId;

    private final String taskId;

    private final String author;

    private final long executedAtEpochMillis;

    private final Status state;

    private final String className;

    private final String methodName;

    private final Object metadata;

    private final long executionMillis;

    private final String executionHostname;

    private final String errorTrace;

    private final ExecutionType type;

    protected Boolean systemChange;//TODO not in server

    public AuditEntryRequest(String stageId,
                             String taskId,
                             String author,
                             long executedAtEpochMillis,
                             Status state,
                             ExecutionType type,
                             String className,
                             String methodName,
                             long executionMillis,
                             String executionHostname,
                             Object metadata,
                             boolean systemChange,
                             String errorTrace) {
        this.stageId = stageId;
        this.taskId = taskId;
        this.author = author;
        this.executedAtEpochMillis = executedAtEpochMillis;
        this.state = state;
        this.className = className;
        this.methodName = methodName;
        this.metadata = metadata;
        this.executionMillis = executionMillis;
        this.executionHostname = executionHostname;
        this.errorTrace = errorTrace;
        this.type = type;

        this.systemChange = systemChange;
    }

    public String getStageId() {
        return stageId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getAuthor() {
        return author;
    }

    public long getExecutedAtEpochMillis() {
        return executedAtEpochMillis;
    }

    public Status getState() {
        return state;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object getMetadata() {
        return metadata;
    }

    public long getExecutionMillis() {
        return executionMillis;
    }

    public String getExecutionHostname() {
        return executionHostname;
    }

    public String getErrorTrace() {
        return errorTrace;
    }

    public Boolean getSystemChange() {
        return systemChange;
    }

    public ExecutionType getType() {
        return type;
    }


}



