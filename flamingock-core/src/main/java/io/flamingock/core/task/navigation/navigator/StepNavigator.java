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

package io.flamingock.core.task.navigation.navigator;

import io.flamingock.commons.utils.Result;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.cloud.transaction.OngoingStatusRepository;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.audit.domain.AuditItem;
import io.flamingock.core.engine.audit.domain.RuntimeContext;
import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.pipeline.execution.TaskSummarizer;
import io.flamingock.core.pipeline.execution.TaskSummary;
import io.flamingock.core.runtime.RuntimeManager;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.executable.ExecutableTask;
import io.flamingock.core.task.navigation.step.ExecutableStep;
import io.flamingock.core.task.navigation.step.RollableFailedStep;
import io.flamingock.core.task.navigation.step.TaskStep;
import io.flamingock.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.core.task.navigation.step.afteraudit.RollableStep;
import io.flamingock.core.task.navigation.step.complete.CompletedAlreadyAppliedStep;
import io.flamingock.core.task.navigation.step.complete.CompletedSuccessStep;
import io.flamingock.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.core.task.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.core.task.navigation.step.execution.FailedExecutionStep;
import io.flamingock.core.task.navigation.step.execution.SuccessExecutionStep;
import io.flamingock.core.task.navigation.step.rolledback.FailedManualRolledBackStep;
import io.flamingock.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.core.transaction.TransactionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class StepNavigator {
    private static final Logger logger = LoggerFactory.getLogger(StepNavigator.class);

    private OngoingStatusRepository ongoingTasksRepository;

    private TaskSummarizer summarizer;

    private AuditWriter auditWriter;

    private RuntimeManager runtimeManager;

    private TransactionWrapper transactionWrapper;

    public StepNavigator(AuditWriter auditWriter, TaskSummarizer summarizer, RuntimeManager runtimeManager, TransactionWrapper transactionWrapper) {
        this.auditWriter = auditWriter;
        this.summarizer = summarizer;
        this.runtimeManager = runtimeManager;
        this.transactionWrapper = transactionWrapper;
        this.ongoingTasksRepository = transactionWrapper != null && CloudTransactioner.class.isAssignableFrom(transactionWrapper.getClass())
                ? (OngoingStatusRepository) transactionWrapper : null;
    }

    private static void logAuditResult(Result auditionResult, String id) {

        if (auditionResult instanceof Result.Error) {
            logger.info("change[ {} ] AUDIT FAILED  \u274C >> {}", id, (((Result.Error) auditionResult).getError().getLocalizedMessage()));
        } else {
            logger.info("change[ {} ] AUDITED \u2705", id);
        }
    }


    void clean() {
        summarizer = null;
        auditWriter = null;
        runtimeManager = null;
    }

    void setSummarizer(TaskSummarizer summarizer) {
        this.summarizer = summarizer;
    }

    void setAuditWriter(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    void setRuntimeManager(RuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    void setTransactionWrapper(TransactionWrapper transactionWrapper) {
        this.transactionWrapper = transactionWrapper;
        this.ongoingTasksRepository = transactionWrapper != null && CloudTransactioner.class.isAssignableFrom(transactionWrapper.getClass())
                ? (OngoingStatusRepository) transactionWrapper : null;
    }

    public final TaskSummary executeTask(ExecutableTask task, ExecutionContext executionContext) {
        if (!task.isAlreadyExecuted()) {
            logger.info("Starting {}", task.getDescriptor().getId());
            // Main execution
            TaskStep executedStep;
            if (transactionWrapper != null && task.getDescriptor().isTransactional()) {
                logger.debug("Executing(transactional, cloud={}) task[{}]", ongoingTasksRepository != null, task.getDescriptor().getId());
                executedStep = executeWithinTransaction(task, executionContext, runtimeManager);
            } else {
                logger.debug("Executing(non-transactional) task[{}]", task.getDescriptor().getId());
                ExecutionStep executionStep = executeTask(task);
                executedStep = auditExecution(executionStep, executionContext, LocalDateTime.now());
            }


            return executedStep instanceof RollableFailedStep
                    ? rollback((RollableFailedStep) executedStep, executionContext)
                    : summarizer.setSuccessful().getSummary();

        } else {
            //Task already executed, we
            summarizer.add(new CompletedAlreadyAppliedStep(task));
            return summarizer.setSuccessful().getSummary();
        }
    }

    private TaskStep executeWithinTransaction(ExecutableTask task,
                                              ExecutionContext executionContext,
                                              DependencyInjectable dependencyInjectable) {

        //If it's a cloud transaction, it requires to write the status
        if (ongoingTasksRepository != null) {
            ongoingTasksRepository.setOngoingExecution(task);
        }

        return transactionWrapper.wrapInTransaction(task.getDescriptor(), dependencyInjectable, () -> {
            ExecutionStep executed = executeTask(task);
            if (executed instanceof SuccessExecutionStep) {
                AfterExecutionAuditStep executionAuditResult = auditExecution(executed, executionContext, LocalDateTime.now());
                if (executionAuditResult instanceof CompletedSuccessStep) {
                    //If it's a cloud transaction, it requires to clean the status
                    if (ongoingTasksRepository != null) {
                        ongoingTasksRepository.cleanOngoingStatus(task.getDescriptor().getId());
                    }
                    return executionAuditResult;
                }
            } else {
                //it logs the EXECUTION_FAILED audit entry anyway
                auditExecution(executed, executionContext, LocalDateTime.now());

            }
            //if it goes through here, it's failed, and it will be rolled back
            return new CompleteAutoRolledBackStep(task, true);
        });
    }

    private ExecutionStep executeTask(ExecutableTask task) {
        ExecutionStep executed = new ExecutableStep(task).execute(runtimeManager);
        summarizer.add(executed);
        String taskId = executed.getTask().getDescriptor().getId();
        if (executed instanceof FailedExecutionStep) {
            FailedExecutionStep failed = (FailedExecutionStep) executed;
            logger.info("change[ {} ] FAILED in {}ms \u274C", taskId, executed.getDuration());
            String msg = String.format("error execution task[%s] after %d ms", failed.getTask().getDescriptor().getId(), failed.getDuration());
            logger.error(msg, failed.getError());

        } else {
            logger.info("change[ {} ] APPLIED in {}ms \u2705", taskId, executed.getDuration());
        }
        return executed;
    }

    private AfterExecutionAuditStep auditExecution(ExecutionStep executionStep,
                                                   ExecutionContext executionContext,
                                                   LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setTaskStep(executionStep).setExecutedAt(executedAt).build();

        Result auditResult = auditWriter.writeStep(new AuditItem(AuditItem.Operation.EXECUTION, executionStep.getTaskDescriptor(), executionContext, runtimeContext));
        logAuditResult(auditResult, executionStep.getTaskDescriptor().getId());
        AfterExecutionAuditStep afterExecutionAudit = executionStep.applyAuditResult(auditResult);
        summarizer.add(afterExecutionAudit);
        return afterExecutionAudit;
    }

    private TaskSummary rollback(RollableFailedStep rollableFailedStep, ExecutionContext executionContext) {
        if (rollableFailedStep instanceof CompleteAutoRolledBackStep) {
            logger.info("change[ {} ] AUTO-ROLLBACK APPLIED \u2705", rollableFailedStep.getTask().getDescriptor().getId());
            //It's autoRollable(handled by the database engine or similar)
            auditAutoRollback((CompleteAutoRolledBackStep) rollableFailedStep, executionContext, LocalDateTime.now());

        }
        rollableFailedStep.getRollbackSteps().forEach(rollableStep -> {
            ManualRolledBackStep rolledBack = manualRollback(rollableStep);
            auditManualRollback(rolledBack, executionContext, LocalDateTime.now());
        });

        return summarizer.setFailed().getSummary();
    }

    private ManualRolledBackStep manualRollback(RollableStep rollable) {
        ManualRolledBackStep rolledBack = rollable.rollback(runtimeManager);
        if (rolledBack instanceof FailedManualRolledBackStep) {
            logger.info("change[ {} ] MANUAL-ROLLBACK FAILED in {} ms - \u274C", rolledBack.getTask().getDescriptor().getId(), rolledBack.getDuration());
            String msg = String.format("error rollback task[%s] in %d ms", rolledBack.getTask().getDescriptor().getId(), rolledBack.getDuration());
            logger.error(msg, ((FailedManualRolledBackStep) rolledBack).getError());

        } else {
            logger.info("change[ {} ] MANUAL-ROLLBACK APPLIED in {} ms \u2705", rolledBack.getTask().getDescriptor().getId(), rolledBack.getDuration());
        }

        summarizer.add(rolledBack);
        return rolledBack;
    }


    private void auditManualRollback(ManualRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setTaskStep(rolledBackStep).setExecutedAt(executedAt).build();
        Result auditResult = auditWriter.writeStep(new AuditItem(AuditItem.Operation.ROLLBACK, rolledBackStep.getTaskDescriptor(), executionContext, runtimeContext));
        logAuditResult(auditResult, rolledBackStep.getTaskDescriptor().getId());
        CompletedFailedManualRollback failedStep = rolledBackStep.applyAuditResult(auditResult);
        summarizer.add(failedStep);
    }

    private void auditAutoRollback(CompleteAutoRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setTaskStep(rolledBackStep).setExecutedAt(executedAt).build();
        Result auditResult = auditWriter.writeStep(new AuditItem(AuditItem.Operation.ROLLBACK, rolledBackStep.getTaskDescriptor(), executionContext, runtimeContext));
        logAuditResult(auditResult, rolledBackStep.getTaskDescriptor().getId());
        summarizer.add(rolledBackStep);

    }
}
