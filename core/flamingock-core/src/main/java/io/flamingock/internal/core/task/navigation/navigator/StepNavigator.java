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

package io.flamingock.internal.core.task.navigation.navigator;

import io.flamingock.internal.util.Result;
import io.flamingock.internal.core.cloud.transaction.OngoingStatusRepository;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.audit.domain.ExecutionAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RollbackAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RuntimeContext;
import io.flamingock.internal.core.engine.audit.domain.StartExecutionAuditContextBundle;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.pipeline.execution.TaskSummary;
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.common.core.context.DependencyInjectable;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.RollableFailedStep;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.TaskStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.RollableStep;
import io.flamingock.internal.core.task.navigation.step.complete.CompletedAlreadyAppliedStep;
import io.flamingock.internal.core.task.navigation.step.complete.CompletedSuccessStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.execution.FailedExecutionStep;
import io.flamingock.internal.core.task.navigation.step.execution.SuccessExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.FailedManualRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class StepNavigator {
    private static final Logger logger = LoggerFactory.getLogger("Flamingock-Navigator");

    private static final String START_DESC = "start";
    private static final String EXECUTION_DESC = "execution";
    private static final String MANUAL_ROLLBACK_DESC = "manual-rollback";
    private static final String AUTO_ROLLBACK_DESC = "auto-rollback";

    private OngoingStatusRepository ongoingTasksRepository;

    private TaskSummarizer summarizer;

    private ExecutionAuditWriter auditWriter;

    private RuntimeManager runtimeManager;

    private TransactionWrapper transactionWrapper;

    public StepNavigator(ExecutionAuditWriter auditWriter,
                         TaskSummarizer summarizer,
                         RuntimeManager runtimeManager,
                         TransactionWrapper transactionWrapper,
                         OngoingStatusRepository ongoingTasksRepository) {
        this.auditWriter = auditWriter;
        this.summarizer = summarizer;
        this.runtimeManager = runtimeManager;
        this.transactionWrapper = transactionWrapper;
        this.ongoingTasksRepository = ongoingTasksRepository;
    }

    void clean() {
        summarizer = null;
        auditWriter = null;
        runtimeManager = null;
    }

    void setSummarizer(TaskSummarizer summarizer) {
        this.summarizer = summarizer;
    }

    void setAuditWriter(ExecutionAuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    void setRuntimeManager(RuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    void setTransactionWrapper(TransactionWrapper transactionWrapper) {
        this.transactionWrapper = transactionWrapper;
    }

    void setOngoingTasksRepository(OngoingStatusRepository ongoingStatusRepository) {
        this.ongoingTasksRepository = ongoingStatusRepository;
    }

    public final TaskSummary executeTask(ExecutableTask task, ExecutionContext executionContext) {
        if (!task.isAlreadyExecuted()) {
            logger.info("Starting {}", task.getId());
            // Main execution
            TaskStep executedStep;
            StartStep startStep = new StartStep(task);

            //TODO: We can avoid this when, in the cloud, the task is transactional
            ExecutableStep executableStep = auditStartExecution(startStep, executionContext, LocalDateTime.now());

            if (transactionWrapper != null && task.getDescriptor().isTransactional()) {
                logger.debug("Executing(transactional, cloud={}) task[{}]", ongoingTasksRepository != null, task.getId());
                executedStep = executeWithinTransaction(executableStep, executionContext, runtimeManager);
            } else {
                logger.debug("Executing(non-transactional) task[{}]", task.getId());
                ExecutionStep executionStep = executeTask(executableStep);
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

    private TaskStep executeWithinTransaction(ExecutableStep executableStep,
                                              ExecutionContext executionContext,
                                              DependencyInjectable dependencyInjectable) {

        //If it's a cloud transaction, it requires to write the status
        if (ongoingTasksRepository != null) {
            ongoingTasksRepository.setOngoingExecution(executableStep.getTask());
        }

        return transactionWrapper.wrapInTransaction(executableStep.getLoadedTask(), dependencyInjectable, () -> {
            ExecutionStep executed = executeTask(executableStep);
            if (executed instanceof SuccessExecutionStep) {
                AfterExecutionAuditStep executionAuditResult = auditExecution(executed, executionContext, LocalDateTime.now());
                if (executionAuditResult instanceof CompletedSuccessStep) {
                    //If it's a cloud transaction, it requires to clean the status
                    if (ongoingTasksRepository != null) {
                        ongoingTasksRepository.cleanOngoingStatus(executableStep.getLoadedTask().getId());
                    }
                    return executionAuditResult;
                }
            } else {
                //it logs the EXECUTION_FAILED audit entry anyway
                auditExecution(executed, executionContext, LocalDateTime.now());

            }
            //if it goes through here, it's failed, and it will be rolled back
            return new CompleteAutoRolledBackStep(executableStep.getTask(), true);
        });
    }

    private ExecutionStep executeTask(ExecutableStep executableStep) {
        ExecutionStep executed = executableStep.execute(runtimeManager);
        summarizer.add(executed);
        String taskId = executed.getTask().getId();
        if (executed instanceof FailedExecutionStep) {
            FailedExecutionStep failed = (FailedExecutionStep) executed;
            logger.info("change[ {} ] FAILED[{}] in {}ms \u274C", taskId, EXECUTION_DESC, executed.getDuration());
            String msg = String.format("error execution task[%s] after %d ms", failed.getTask().getId(), failed.getDuration());
            logger.error(msg, failed.getError());

        } else {
            logger.info("change[ {} ] APPLIED[{}] in {}ms \u2705", taskId, EXECUTION_DESC, executed.getDuration());
        }
        return executed;
    }


    private ExecutableStep auditStartExecution(StartStep startStep,
                                               ExecutionContext executionContext,
                                               LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setStartStep(startStep).setExecutedAt(executedAt).build();
        Result auditResult = auditWriter.writeStartExecution(new StartExecutionAuditContextBundle(startStep.getLoadedTask(), executionContext, runtimeContext));
        logAuditResult(auditResult, startStep.getLoadedTask().getId(), START_DESC);
        ExecutableStep executableStep = startStep.start();
        summarizer.add(executableStep);
        return executableStep;
    }

    private AfterExecutionAuditStep auditExecution(ExecutionStep executionStep,
                                                   ExecutionContext executionContext,
                                                   LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setExecutionStep(executionStep).setExecutedAt(executedAt).build();

        Result auditResult = auditWriter.writeExecution(new ExecutionAuditContextBundle(executionStep.getLoadedTask(), executionContext, runtimeContext));
        logAuditResult(auditResult, executionStep.getLoadedTask().getId(), EXECUTION_DESC);
        AfterExecutionAuditStep afterExecutionAudit = executionStep.applyAuditResult(auditResult);
        summarizer.add(afterExecutionAudit);
        return afterExecutionAudit;
    }

    private static void logAuditResult(Result auditionResult, String id, String description) {

        if (auditionResult instanceof Result.Error) {
            logger.info("change[ {} ] AUDIT FAILED[{}]  \u274C >> {}", id, description, (((Result.Error) auditionResult).getError().getLocalizedMessage()));
        } else {
            logger.info("change[ {} ] AUDITED[{}] \u2705", id, description);
        }
    }

    private TaskSummary rollback(RollableFailedStep rollableFailedStep, ExecutionContext executionContext) {
        if (rollableFailedStep instanceof CompleteAutoRolledBackStep) {
            logger.info("change[ {} ] APPLIED[{}] \u2705", rollableFailedStep.getTask().getId(), AUTO_ROLLBACK_DESC);
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
            logger.info("change[ {} ] FAILED[{}] in {} ms - \u274C", rolledBack.getTask().getId(), MANUAL_ROLLBACK_DESC, rolledBack.getDuration());
            String msg = String.format("error rollback task[%s] in %d ms", rolledBack.getTask().getId(), rolledBack.getDuration());
            logger.error(msg, ((FailedManualRolledBackStep) rolledBack).getError());

        } else {
            logger.info("change[ {} ] APPLIED[{}] in {} ms \u2705", rolledBack.getTask().getId(), MANUAL_ROLLBACK_DESC, rolledBack.getDuration());
        }

        summarizer.add(rolledBack);
        return rolledBack;
    }


    private void auditManualRollback(ManualRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setManualRollbackStep(rolledBackStep).setExecutedAt(executedAt).build();
        Result auditResult = auditWriter.writeRollback(new RollbackAuditContextBundle(rolledBackStep.getLoadedTask(), executionContext, runtimeContext));
        logAuditResult(auditResult, rolledBackStep.getLoadedTask().getId(), MANUAL_ROLLBACK_DESC);
        CompletedFailedManualRollback failedStep = rolledBackStep.applyAuditResult(auditResult);
        summarizer.add(failedStep);
    }

    private void auditAutoRollback(CompleteAutoRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setAutoRollbackStep(rolledBackStep).setExecutedAt(executedAt).build();
        Result auditResult = auditWriter.writeRollback(new RollbackAuditContextBundle(rolledBackStep.getLoadedTask(), executionContext, runtimeContext));
        logAuditResult(auditResult, rolledBackStep.getLoadedTask().getId(), AUTO_ROLLBACK_DESC);
        summarizer.add(rolledBackStep);
    }
}
