package io.flamingock.core.engine.audit.domain;

import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.descriptor.LoadedTask;

public class RollbackAuditItem extends AuditItem{

    public RollbackAuditItem(LoadedTask taskDescriptor, ExecutionContext executionContext, RuntimeContext runtimeContext) {
        super(Operation.ROLLBACK, taskDescriptor, executionContext, runtimeContext);
    }
}
