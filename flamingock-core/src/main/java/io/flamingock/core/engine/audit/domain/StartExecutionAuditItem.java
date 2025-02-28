package io.flamingock.core.engine.audit.domain;

import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.descriptor.LoadedTask;

public class StartExecutionAuditItem extends AuditItem{

    public StartExecutionAuditItem(LoadedTask taskDescriptor, ExecutionContext executionContext, RuntimeContext runtimeContext) {
        super(Operation.START_EXECUTION, taskDescriptor, executionContext, runtimeContext);
    }
}
