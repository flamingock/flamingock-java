/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.task.navigation.navigator.strategy;

import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.pipeline.execution.TaskSummary;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.internal.core.targets.operations.TargetSystemOps;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessStrategy;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessLogger;
import io.flamingock.internal.core.task.navigation.navigator.operations.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.complete.CompletedAlreadyAppliedStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.Result;

import java.time.LocalDateTime;

public abstract class AbstractChangeProcessStrategy implements ChangeProcessStrategy {
    protected static final ChangeProcessLogger stepLogger = new ChangeProcessLogger();

    protected final ExecutableTask changeUnit;

    protected final TargetSystemOps targetSystem;

    protected final ExecutionContext executionContext;

    protected final TaskSummarizer summarizer;

    protected final AuditStoreStepOperations auditStoreOperations;

    private final ContextResolver baseContext;
    private final LockGuardProxyFactory lockProxyFactory;


    protected AbstractChangeProcessStrategy(ExecutableTask changeUnit,
                                            ExecutionContext executionContext,
                                            TargetSystemOps targetSystem,
                                            AuditStoreStepOperations auditStoreOperations,
                                            TaskSummarizer summarizer,
                                            LockGuardProxyFactory proxyFactory,
                                            ContextResolver baseContext) {
        this.changeUnit = changeUnit;
        this.executionContext = executionContext;
        this.targetSystem = targetSystem;
        this.summarizer = summarizer;
        this.auditStoreOperations = auditStoreOperations;
        this.lockProxyFactory = proxyFactory;
        this.baseContext = baseContext;
    }


    public final TaskSummary applyChange() {
        if (!changeUnit.isAlreadyExecuted()) {
            return doApplyChange();
        } else {
            return summarizer
                    .add(new CompletedAlreadyAppliedStep(changeUnit))
                    .setSuccessful()
                    .getSummary();
        }
    }

    abstract protected TaskSummary doApplyChange();

    protected ExecutableStep auditAndLogStartExecution(StartStep startStep,
                                                       ExecutionContext executionContext,
                                                       LocalDateTime executedAt) {
        Result auditResult = auditStoreOperations.auditStartExecution(startStep, executionContext, executedAt);
        stepLogger.logAuditStartResult(auditResult, startStep.getLoadedTask().getId());
        ExecutableStep executableStep = startStep.start();
        summarizer.add(executableStep);
        return executableStep;
    }

    protected AfterExecutionAuditStep auditAndLogExecution(ExecutionStep executionStep) {
        //adds to the summarizer and logs the execution
        summarizer.add(executionStep);
        stepLogger.logExecutionResult(executionStep);

        //writes execution result to audit store
        Result auditResult = auditStoreOperations.auditExecution(executionStep, executionContext, LocalDateTime.now());

        //adds to the summarizer and logs the audit result
        stepLogger.logAuditExecutionResult(auditResult, executionStep.getLoadedTask());
        AfterExecutionAuditStep afterExecutionAudit = executionStep.applyAuditResult(auditResult);
        summarizer.add(afterExecutionAudit);
        return afterExecutionAudit;
    }


    protected void auditAndLogManualRollback(ManualRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        Result auditResult = auditStoreOperations.auditManualRollback(rolledBackStep, executionContext, executedAt);
        stepLogger.logAuditManualRollbackResult(auditResult, rolledBackStep.getLoadedTask());
        CompletedFailedManualRollback failedStep = rolledBackStep.applyAuditResult(auditResult);
        summarizer.add(failedStep);
    }

    protected void auditAndLogAutoRollback(CompleteAutoRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        Result auditResult = auditStoreOperations.auditAutoRollback(rolledBackStep, executionContext, executedAt);
        stepLogger.logAuditAutoRollbackResult(auditResult, rolledBackStep.getLoadedTask());
        summarizer.add(rolledBackStep);
    }


    protected ExecutionRuntime buildExecutionRuntime() {
        Context changeUnitSessionContext = new PriorityContext(new SimpleContext(), baseContext);
        return ExecutionRuntime.builder()
                .setDependencyContext(changeUnitSessionContext)
                .setLockGuardProxyFactory(lockProxyFactory)
                .build();
    }

}
