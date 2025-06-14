/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.internal.core.engine.audit.domain;

import io.flamingock.commons.utils.ThrowableUtil;
import io.flamingock.core.audit.AuditEntry;
import io.flamingock.core.cloud.api.vo.OngoingStatus;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.TaskDescriptor;

public abstract class AuditContextBundle {


    public enum Operation {

        START_EXECUTION, EXECUTION, ROLLBACK;

        public OngoingStatus toOngoingStatusOperation() {
            return OngoingStatus.valueOf(this.name());
        }

        public static AuditContextBundle.Operation fromOngoingStatusOperation(OngoingStatus ongoingOperation) {
            return AuditContextBundle.Operation.valueOf(ongoingOperation.name());
        }

    }

    private final Operation operation;
    private final TaskDescriptor loadedTask;
    private final ExecutionContext executionContext;
    private final RuntimeContext runtimeContext;

    public AuditContextBundle(Operation operation,
                              TaskDescriptor loadedTask,
                              ExecutionContext executionContext,
                              RuntimeContext runtimeContext) {
        this.operation = operation;
        this.loadedTask = loadedTask;
        this.executionContext = executionContext;
        this.runtimeContext = runtimeContext;
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




    public AuditEntry toAuditEntry() {
        TaskDescriptor loadedTask = getLoadedTask();
        ExecutionContext stageExecutionContext = getExecutionContext();
        RuntimeContext runtimeContext = getRuntimeContext();
        return new AuditEntry(
                stageExecutionContext.getExecutionId(),
                runtimeContext.getStageName(),
                loadedTask.getId(),
                stageExecutionContext.getAuthor(),
                runtimeContext.getExecutedAt(),
                getAuditStatus(),
                getExecutionType(),
                loadedTask.getSource(),
                runtimeContext.getMethodExecutor(),
                runtimeContext.getDuration(),
                stageExecutionContext.getHostname(),
                stageExecutionContext.getMetadata(),
                getSystemChange(),
                ThrowableUtil.serialize(runtimeContext.getError().orElse(null))
        );
    }

    private AuditEntry.Status getAuditStatus() {
        switch (getOperation()) {
            case START_EXECUTION:
                return AuditEntry.Status.STARTED;
            case EXECUTION:
                return getRuntimeContext().isSuccess() ? AuditEntry.Status.EXECUTED : AuditEntry.Status.EXECUTION_FAILED;
            case ROLLBACK:
            default:
                return getRuntimeContext().isSuccess() ? AuditEntry.Status.ROLLED_BACK : AuditEntry.Status.ROLLBACK_FAILED;
        }
    }

    private AuditEntry.ExecutionType getExecutionType() {
        return AuditEntry.ExecutionType.EXECUTION;
    }

    private boolean getSystemChange() {
        return false;
    }


}