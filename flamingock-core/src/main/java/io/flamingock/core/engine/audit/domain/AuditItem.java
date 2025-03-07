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

package io.flamingock.core.engine.audit.domain;

import io.flamingock.core.cloud.api.vo.OngoingStatus;
import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.descriptor.LoadedTask;

public abstract class AuditItem {


    public enum Operation {

        START_EXECUTION, EXECUTION, ROLLBACK;

        public OngoingStatus toOngoingStatusOperation() {
            return OngoingStatus.valueOf(this.name());
        }

        public static AuditItem.Operation fromOngoingStatusOperation(OngoingStatus ongoingOperation) {
            return AuditItem.Operation.valueOf(ongoingOperation.name());
        }

    }

    private final Operation operation;
    private final LoadedTask loadedTask;
    private final ExecutionContext executionContext;
    private final RuntimeContext runtimeContext;

    public AuditItem(Operation operation,
                     LoadedTask loadedTask,
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

    public LoadedTask getLoadedTask() {
        return loadedTask;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

}