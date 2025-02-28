package io.flamingock.core.engine.audit.domain;

import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.descriptor.LoadedTask;

public class ExecutionAuditItem extends AuditItem{

    public ExecutionAuditItem(LoadedTask taskDescriptor, ExecutionContext executionContext, RuntimeContext runtimeContext) {
        super(Operation.EXECUTION, taskDescriptor, executionContext, runtimeContext);
    }
}
