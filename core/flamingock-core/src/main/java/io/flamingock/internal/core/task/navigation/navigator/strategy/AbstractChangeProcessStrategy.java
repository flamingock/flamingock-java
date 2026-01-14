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
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessResult;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessStrategy;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessLogger;
import io.flamingock.internal.core.task.navigation.navigator.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.complete.CompletedAlreadyAppliedStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.TimeService;

import java.time.LocalDateTime;

/**
 * Abstract base class for change process strategies implementing common audit and execution patterns.
 * 
 * <p>This class provides the foundational infrastructure for executing changes across different
 * target system types while maintaining consistent audit trails and execution summaries.
 * Concrete implementations define the specific transaction handling and execution flow patterns
 * appropriate for their target system characteristics.
 * 
 * <h3>Common Execution Pattern</h3>
 * <ol>
 * <li>Check if change is already applied (skip if so)</li>
 * <li>Audit change start in audit store</li>
 * <li>Execute change using strategy-specific approach</li>
 * <li>Audit execution result</li>
 * <li>Handle rollbacks and cleanup as needed</li>
 * <li>Return execution summary</li>
 * </ol>
 * 
 * <h3>Audit Operations</h3>
 * <p>All strategies use consistent audit operations provided by this base class:
 * <ul>
 * <li>{@code auditAndLogStartExecution} - Records change initiation</li>
 * <li>{@code auditAndLogExecution} - Records execution results</li>
 * <li>{@code auditAndLogManualRollback} - Records manual rollback operations</li>
 * <li>{@code auditAndLogAutoRollback} - Records automatic transaction rollbacks</li>
 * </ul>
 * 
 * <h3>Execution Runtime</h3>
 * <p>The execution runtime provides dependency injection and security context for change execution,
 * ensuring changes have access to required dependencies while maintaining proper lock management.</p>
 * 
 * @param <TS_OPS> The type of target system operations supported by this strategy
 */
public abstract class AbstractChangeProcessStrategy<TS_OPS extends TargetSystemOps> implements ChangeProcessStrategy {
    protected static final ChangeProcessLogger stepLogger = new ChangeProcessLogger();

    protected final ExecutableTask change;

    protected final TS_OPS targetSystemOps;

    protected final ExecutionContext executionContext;

    protected final TaskSummarizer summarizer;

    protected final AuditStoreStepOperations auditStoreOperations;

    private final ContextResolver baseContext;
    
    private final LockGuardProxyFactory lockProxyFactory;

    protected final TimeService timeService;

    protected AbstractChangeProcessStrategy(ExecutableTask change,
                                            ExecutionContext executionContext,
                                            TS_OPS targetSystemOps,
                                            AuditStoreStepOperations auditStoreOperations,
                                            TaskSummarizer summarizer,
                                            LockGuardProxyFactory proxyFactory,
                                            ContextResolver baseContext,
                                            TimeService timeService) {
        this.change = change;
        this.executionContext = executionContext;
        this.targetSystemOps = targetSystemOps;
        this.summarizer = summarizer;
        this.auditStoreOperations = auditStoreOperations;
        this.lockProxyFactory = proxyFactory;
        this.baseContext = baseContext;
        this.timeService = timeService;
    }


    public final ChangeProcessResult applyChange() {
        if (!change.isAlreadyApplied()) {
            return doApplyChange();
        } else {
            stepLogger.logSkippedExecution(change.getId());
            TaskSummary summary = summarizer
                    .add(new CompletedAlreadyAppliedStep(change))
                    .setSuccessful()
                    .getSummary();
            return new ChangeProcessResult(change.getId(), summary);
        }
    }

    /**
     * Strategy-specific implementation of change application.
     * 
     * <p>Concrete strategies must implement this method to define their specific
     * transaction handling, execution flow, and error recovery patterns.
     * 
     * @return Task execution summary with success/failure status and step details
     */
    abstract protected ChangeProcessResult doApplyChange();

    /**
     * Audits and logs the start of change execution.
     * 
     * @param startStep The initial step for the change
     * @param executionContext The execution context
     * @return The executable step ready for execution
     */
    protected ExecutableStep auditAndLogStartExecution(StartStep startStep,
                                                       ExecutionContext executionContext) {
        Result auditResult = auditStoreOperations.auditStartExecution(startStep, executionContext, timeService.currentDateTime());
        stepLogger.logAuditStartResult(auditResult, startStep.getLoadedTask().getId());
        ExecutableStep executableStep = startStep.start();
        summarizer.add(executableStep);
        return executableStep;
    }

    /**
     * Audits and logs the execution result of a change.
     * 
     * @param executionStep The execution step with results
     * @return The after-execution audit step
     */
    protected AfterExecutionAuditStep auditAndLogExecution(ExecutionStep executionStep) {
        summarizer.add(executionStep);
        stepLogger.logExecutionResult(executionStep);

        Result auditResult = auditStoreOperations.auditExecution(executionStep, executionContext, timeService.currentDateTime());

        stepLogger.logAuditExecutionResult(auditResult, executionStep.getLoadedTask());
        AfterExecutionAuditStep afterExecutionAudit = executionStep.withAuditResult(auditResult);
        summarizer.add(afterExecutionAudit);
        return afterExecutionAudit;
    }


    protected void auditAndLogManualRollback(ManualRolledBackStep rolledBackStep, ExecutionContext executionContext) {
        Result auditResult = auditStoreOperations.auditManualRollback(rolledBackStep, executionContext, timeService.currentDateTime());
        stepLogger.logAuditManualRollbackResult(auditResult, rolledBackStep.getLoadedTask());
        CompletedFailedManualRollback failedStep = rolledBackStep.applyAuditResult(auditResult);
        summarizer.add(failedStep);
    }

    protected void auditAndLogAutoRollback(CompleteAutoRolledBackStep rolledBackStep, ExecutionContext executionContext) {
        Result auditResult = auditStoreOperations.auditAutoRollback(rolledBackStep, executionContext, timeService.currentDateTime());
        stepLogger.logAuditAutoRollbackResult(auditResult, rolledBackStep.getLoadedTask());
        summarizer.add(rolledBackStep);
    }


    /**
     * Builds the execution runtime for change execution.
     * 
     * <p>The runtime provides dependency injection context and security proxies
     * needed for safe change execution with proper lock management.
     * 
     * @return Configured execution runtime
     */
    protected ExecutionRuntime buildExecutionRuntime() {
        Context changeSessionContext = new PriorityContext(new SimpleContext(), baseContext);
        return ExecutionRuntime.builder()
                .setSessionId(change.getId())
                .setDependencyContext(changeSessionContext)
                .setLockGuardProxyFactory(lockProxyFactory)
                .build();
    }

}
