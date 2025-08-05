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
package io.flamingock.internal.core.task.navigation.navigator.operations;

import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.audit.domain.ExecutionAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RollbackAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RuntimeContext;
import io.flamingock.internal.core.engine.audit.domain.StartExecutionAuditContextBundle;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.Result;

import java.time.LocalDateTime;

public class AuditStoreStepOperations {

    private final ExecutionAuditWriter auditWriter;

    public AuditStoreStepOperations(ExecutionAuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    public Result auditStartExecution(StartStep startStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setStartStep(startStep).setExecutedAt(executedAt).build();
        return auditWriter.writeStartExecution(new StartExecutionAuditContextBundle(startStep.getLoadedTask(), executionContext, runtimeContext));
    }

    public Result auditExecution(ExecutionStep executionStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setExecutionStep(executionStep).setExecutedAt(executedAt).build();
        return auditWriter.writeExecution(new ExecutionAuditContextBundle(executionStep.getLoadedTask(), executionContext, runtimeContext));
    }

    public Result auditManualRollback(ManualRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setManualRollbackStep(rolledBackStep).setExecutedAt(executedAt).build();
        return auditWriter.writeRollback(new RollbackAuditContextBundle(rolledBackStep.getLoadedTask(), executionContext, runtimeContext));
    }

    public Result auditAutoRollback(CompleteAutoRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setAutoRollbackStep(rolledBackStep).setExecutedAt(executedAt).build();
        return auditWriter.writeRollback(new RollbackAuditContextBundle(rolledBackStep.getLoadedTask(), executionContext, runtimeContext));
    }

}