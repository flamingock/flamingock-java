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
package io.flamingock.internal.core.task.navigation.navigator;

import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.pipeline.execution.TaskSummary;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.operations.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.operations.LegacyTargetSystemStepOperations;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.RollableFailedStep;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.TaskStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.complete.CompletedAlreadyAppliedStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.Result;

import java.time.LocalDateTime;

@Deprecated
public class StepNavigator implements ChangeProcessStrategy {

    private static final ChangeProcessLogger stepLogger = new ChangeProcessLogger();

    private final ExecutableTask changeUnit;

    private final LegacyTargetSystemStepOperations targetSystemOps;

    private final ExecutionContext executionContext;

    private final TaskSummarizer summarizer;

    private final AuditStoreStepOperations auditStoreOperations;

    public static ChangeProcessStrategyFactory builder(TargetSystemManager targetSystemManager) {
        return new ChangeProcessStrategyFactory(targetSystemManager);
    }


    public StepNavigator(ExecutableTask changeUnit,
                         ExecutionContext executionContext,
                         LegacyTargetSystemStepOperations targetSystemOps,
                         AuditStoreStepOperations auditStoreOperations,
                         TaskSummarizer summarizer) {
        this.changeUnit = changeUnit;
        this.executionContext = executionContext;
        this.targetSystemOps = targetSystemOps;
        this.summarizer = summarizer;
        this.auditStoreOperations = auditStoreOperations;
    }


    @Override
    public final TaskSummary applyChange() {
        if (!changeUnit.isAlreadyExecuted()) {
            stepLogger.logExecutionStart(changeUnit);

            StartStep startStep = new StartStep(changeUnit);

            ExecutableStep executableStep = auditAndLogStartExecution(startStep, executionContext, LocalDateTime.now());

            TaskStep executedStep = targetSystemOps.applyChange(executableStep, this::auditAndLogExecution);

            if (executedStep instanceof RollableFailedStep) {
                return rollback((RollableFailedStep) executedStep, executionContext);
            } else {
                return summarizer.setSuccessful().getSummary();
            }


        } else {
            //Task already executed, we
            summarizer.add(new CompletedAlreadyAppliedStep(changeUnit));
            return summarizer.setSuccessful().getSummary();
        }
    }

    private TaskSummary rollback(RollableFailedStep rollableFailedStep, ExecutionContext executionContext) {
        if (rollableFailedStep instanceof CompleteAutoRolledBackStep) {
            stepLogger.logAutoRollback(rollableFailedStep.getTask());
            //It's autoRollable(handled by the database engine or similar)
            auditAndLogAutoRollback((CompleteAutoRolledBackStep) rollableFailedStep, executionContext, LocalDateTime.now());

        }
        rollableFailedStep.getRollbackSteps().forEach(rollableStep -> {
            ManualRolledBackStep rolledBack = targetSystemOps.rollbackChange(rollableStep);
            auditAndLogManualRollback(rolledBack, executionContext, LocalDateTime.now());
            stepLogger.logManualRollbackResult(rolledBack);
            summarizer.add(rolledBack);
        });

        return summarizer.setFailed().getSummary();
    }


    private ExecutableStep auditAndLogStartExecution(StartStep startStep,
                                                     ExecutionContext executionContext,
                                                     LocalDateTime executedAt) {
        Result auditResult = auditStoreOperations.auditStartExecution(startStep, executionContext, executedAt);
        stepLogger.logAuditStartResult(auditResult, startStep.getLoadedTask().getId());
        ExecutableStep executableStep = startStep.start();
        summarizer.add(executableStep);
        return executableStep;
    }

    private AfterExecutionAuditStep auditAndLogExecution(ExecutionStep executionStep) {
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


    private void auditAndLogManualRollback(ManualRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        Result auditResult = auditStoreOperations.auditManualRollback(rolledBackStep, executionContext, executedAt);
        stepLogger.logAuditManualRollbackResult(auditResult, rolledBackStep.getLoadedTask());
        CompletedFailedManualRollback failedStep = rolledBackStep.applyAuditResult(auditResult);
        summarizer.add(failedStep);
    }

    private void auditAndLogAutoRollback(CompleteAutoRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        Result auditResult = auditStoreOperations.auditAutoRollback(rolledBackStep, executionContext, executedAt);
        stepLogger.logAuditAutoRollbackResult(auditResult, rolledBackStep.getLoadedTask());
        summarizer.add(rolledBackStep);
    }


}
