/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.internal.core.external.store.audit.domain;

import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.ThrowableUtil;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.common.core.task.TaskDescriptor;

public abstract class AuditContextBundle {


    public enum Operation {

        START_EXECUTION, EXECUTION, ROLLBACK;

        public TargetSystemAuditMarkType toOngoingStatusOperation() {
            return TargetSystemAuditMarkType.valueOf(this.name());
        }

        public static AuditContextBundle.Operation fromOngoingStatusOperation(TargetSystemAuditMarkType ongoingOperation) {
            return AuditContextBundle.Operation.valueOf(ongoingOperation.name());
        }

    }

    private final Operation operation;
    private final TaskDescriptor loadedTask;
    private final ExecutionContext executionContext;
    private final RuntimeContext runtimeContext;
    private final AuditTxType operationType;
    private final String targetSystemId;

    public AuditContextBundle(Operation operation,
                              TaskDescriptor loadedTask,
                              ExecutionContext executionContext,
                              RuntimeContext runtimeContext,
                              AuditTxType auditTxType,
                              String targetSystemId) {
        this.operation = operation;
        this.loadedTask = loadedTask;
        this.executionContext = executionContext;
        this.runtimeContext = runtimeContext;
        this.operationType = auditTxType;
        this.targetSystemId = targetSystemId;
    }

    public Operation getOperation() {
        return operation;
    }

    public TaskDescriptor getLoadedTask() {
        return loadedTask;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public AuditTxType getAuditTxType() {
        return operationType;
    }

    public String getTargetSystemId() {
        return targetSystemId;
    }


    public AuditEntry toAuditEntry() {
        TaskDescriptor loadedChange = getLoadedTask();
        ExecutionContext stageExecutionContext = getExecutionContext();
        RuntimeContext runtimeContext = getRuntimeContext();
        
        return new AuditEntry(
                stageExecutionContext.getExecutionId(),
                runtimeContext.getStageName(),
                loadedChange.getId(),
                loadedChange.getAuthor(),
                runtimeContext.getAppliedAt(),
                getAuditStatus(),
                getExecutionType(),
                loadedChange.getSource(),
                runtimeContext.getMethodExecutor(),
                null, //TODO: set sourceFile
                runtimeContext.getDuration(),
                stageExecutionContext.getHostname(),
                stageExecutionContext.getMetadata(),
                loadedChange.isSystem(),
                ThrowableUtil.serialize(runtimeContext.getError().orElse(null)),
                getAuditTxType(),
                getTargetSystemId(),
                loadedChange.getOrder().orElseThrow(() -> new IllegalStateException("Order is required but not present")),
                loadedChange.getRecovery().getStrategy(),
                loadedChange.isTransactional()
        );
    }

    private AuditEntry.Status getAuditStatus() {
        switch (getOperation()) {
            case START_EXECUTION:
                return AuditEntry.Status.STARTED;
            case EXECUTION:
                return getRuntimeContext().isSuccess() ? AuditEntry.Status.APPLIED : AuditEntry.Status.FAILED;
            case ROLLBACK:
            default:
                return getRuntimeContext().isSuccess() ? AuditEntry.Status.ROLLED_BACK : AuditEntry.Status.ROLLBACK_FAILED;
        }
    }

    private AuditEntry.ExecutionType getExecutionType() {
        return AuditEntry.ExecutionType.EXECUTION;
    }



}