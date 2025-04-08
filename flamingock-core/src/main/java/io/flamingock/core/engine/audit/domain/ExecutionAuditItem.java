package io.flamingock.core.engine.audit.domain;

import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.TaskDescriptor;

public class ExecutionAuditItem extends AuditItem{

    public ExecutionAuditItem(TaskDescriptor loadedTask, ExecutionContext executionContext, RuntimeContext runtimeContext) {
        super(Operation.EXECUTION, loadedTask, executionContext, runtimeContext);
    }
}
