package io.flamingock.core.engine.audit.domain;

import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.TaskDescriptor;

public class StartExecutionAuditItem extends AuditItem{

    public StartExecutionAuditItem(TaskDescriptor loadedTask, ExecutionContext executionContext, RuntimeContext runtimeContext) {
        super(Operation.START_EXECUTION, loadedTask, executionContext, runtimeContext);
    }
}
