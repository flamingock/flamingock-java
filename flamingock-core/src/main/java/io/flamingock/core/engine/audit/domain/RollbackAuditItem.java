package io.flamingock.core.engine.audit.domain;

import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.TaskDescriptor;

public class RollbackAuditItem extends AuditItem{

    public RollbackAuditItem(TaskDescriptor loadedTask, ExecutionContext executionContext, RuntimeContext runtimeContext) {
        super(Operation.ROLLBACK, loadedTask, executionContext, runtimeContext);
    }
}
