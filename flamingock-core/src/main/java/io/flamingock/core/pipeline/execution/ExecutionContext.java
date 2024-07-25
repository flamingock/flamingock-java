package io.flamingock.core.pipeline.execution;

import java.util.Map;

public class ExecutionContext extends OrphanExecutionContext{
    private final String executionId;

    public ExecutionContext(String executionId, String hostname, String author, Map<String, Object> metadata) {
        super(hostname, author, metadata);
        this.executionId = executionId;
    }

    public String getExecutionId() {
        return executionId;
    }
}
