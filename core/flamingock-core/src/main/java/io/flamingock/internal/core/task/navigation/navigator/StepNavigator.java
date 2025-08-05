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

import io.flamingock.internal.core.targets.TargetSystemOperations;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.audit.domain.ExecutionAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RollbackAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RuntimeContext;
import io.flamingock.internal.core.engine.audit.domain.StartExecutionAuditContextBundle;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.pipeline.execution.TaskSummary;
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.RollableFailedStep;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.TaskStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.RollableStep;
import io.flamingock.internal.core.task.navigation.step.complete.CompletedAlreadyAppliedStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.execution.FailedExecutionStep;
import io.flamingock.internal.core.task.navigation.step.execution.SuccessExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.FailedManualRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class StepNavigator {
    private static final Logger logger = LoggerFactory.getLogger("Flamingock-Navigator");

    private static final String START_DESC = "start";
    private static final String EXECUTION_DESC = "execution";
    private static final String MANUAL_ROLLBACK_DESC = "manual-rollback";
    private static final String AUTO_ROLLBACK_DESC = "auto-rollback";

    private ExecutableTask changeUnit;

    private ExecutionAuditWriter auditWriter;

    private TargetSystemOperations targetSystemOps;

    private ExecutionContext executionContext;

    private TaskSummarizer summarizer;


    private RuntimeManager runtimeManager;


    public StepNavigator() {
        this(null, null, null, null, null, null);
    }

    public StepNavigator(ExecutableTask changeUnit,
                         ExecutionContext executionContext,
                         TargetSystemOperations targetSystemOps,
                         ExecutionAuditWriter auditWriter,
                         TaskSummarizer summarizer,
                         RuntimeManager runtimeManager) {
        this.changeUnit = changeUnit;
        this.executionContext = executionContext;
        this.targetSystemOps = targetSystemOps;
        this.auditWriter = auditWriter;
        this.summarizer = summarizer;
        this.runtimeManager = runtimeManager;
    }

    void clean() {
        changeUnit = null;
        executionContext = null;
        targetSystemOps = null;
        summarizer = null;
        auditWriter = null;
        runtimeManager = null;
    }

    void setChangeUnit(ExecutableTask changeUnit) {
        this.changeUnit = changeUnit;
    }

    void setExecutionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    void setTargetSystemOps(TargetSystemOperations targetSystemOps) {
        this.targetSystemOps = targetSystemOps;
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


    public final TaskSummary execute() {
        if (!changeUnit.isAlreadyExecuted()) {
            logger.info("Starting {}", changeUnit.getId());
            // Main execution
            StartStep startStep = new StartStep(changeUnit);

            //TODO: We can avoid this when, in the cloud, the task is transactional
            ExecutableStep executableStep = auditStartExecution(startStep, executionContext, LocalDateTime.now());


            TaskStep executedStep = targetSystemOps.applyChange(executableStep, this::execute, this::auditExecutionAtNow);

            return executedStep instanceof RollableFailedStep
                    ? rollback((RollableFailedStep) executedStep, executionContext)
                    : summarizer.setSuccessful().getSummary();

        } else {
            //Task already executed, we
            summarizer.add(new CompletedAlreadyAppliedStep(changeUnit));
            return summarizer.setSuccessful().getSummary();
        }
    }

    private ExecutionStep execute(ExecutableStep executableStep) {
        ExecutionStep changeApplied = executableStep.execute(runtimeManager);
        summarizer.add(changeApplied);
        String taskId = changeApplied.getTask().getId();
        if(changeApplied instanceof SuccessExecutionStep) {
            logger.info("change[ {} ] APPLIED[{}] in {}ms ✅", taskId, EXECUTION_DESC, changeApplied.getDuration());
        } else {//FailedExecutionStep
            FailedExecutionStep failed = (FailedExecutionStep) changeApplied;
            logger.info("change[ {} ] FAILED[{}] in {}ms ❌", taskId, EXECUTION_DESC, changeApplied.getDuration());
            String msg = String.format("error execution task[%s] after %d ms", failed.getTask().getId(), failed.getDuration());
            logger.error(msg, failed.getError());

        }
        return changeApplied;
    }

    private TaskSummary rollback(RollableFailedStep rollableFailedStep, ExecutionContext executionContext) {
        if (rollableFailedStep instanceof CompleteAutoRolledBackStep) {
            logger.info("change[ {} ] APPLIED[{}] ✅", rollableFailedStep.getTask().getId(), AUTO_ROLLBACK_DESC);
            //It's autoRollable(handled by the database engine or similar)
            auditAutoRollback((CompleteAutoRolledBackStep) rollableFailedStep, executionContext, LocalDateTime.now());

        }
        rollableFailedStep.getRollbackSteps().forEach(rollableStep -> {
            ManualRolledBackStep rolledBack = targetSystemOps.rollbackChange(rollableStep, this::manualRollback);
            auditManualRollback(rolledBack, executionContext, LocalDateTime.now());
        });

        return summarizer.setFailed().getSummary();
    }

    private ManualRolledBackStep manualRollback(RollableStep rollable) {
        ManualRolledBackStep rolledBack = rollable.rollback(runtimeManager);
        if (rolledBack instanceof FailedManualRolledBackStep) {
            logger.info("change[ {} ] FAILED[{}] in {} ms - ❌", rolledBack.getTask().getId(), MANUAL_ROLLBACK_DESC, rolledBack.getDuration());
            String msg = String.format("error rollback task[%s] in %d ms", rolledBack.getTask().getId(), rolledBack.getDuration());
            logger.error(msg, ((FailedManualRolledBackStep) rolledBack).getError());

        } else {
            logger.info("change[ {} ] APPLIED[{}] in {} ms ✅", rolledBack.getTask().getId(), MANUAL_ROLLBACK_DESC, rolledBack.getDuration());
        }

        summarizer.add(rolledBack);
        return rolledBack;
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

    private AfterExecutionAuditStep auditExecutionAtNow(ExecutionStep executionStep) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setExecutionStep(executionStep).setExecutedAt(LocalDateTime.now()).build();

        Result auditResult = auditWriter.writeExecution(new ExecutionAuditContextBundle(executionStep.getLoadedTask(), executionContext, runtimeContext));
        logAuditResult(auditResult, executionStep.getLoadedTask().getId(), EXECUTION_DESC);
        AfterExecutionAuditStep afterExecutionAudit = executionStep.applyAuditResult(auditResult);
        summarizer.add(afterExecutionAudit);
        return afterExecutionAudit;
    }


    private CompletedFailedManualRollback auditManualRollback(ManualRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setManualRollbackStep(rolledBackStep).setExecutedAt(executedAt).build();
        Result auditResult = auditWriter.writeRollback(new RollbackAuditContextBundle(rolledBackStep.getLoadedTask(), executionContext, runtimeContext));
        logAuditResult(auditResult, rolledBackStep.getLoadedTask().getId(), MANUAL_ROLLBACK_DESC);
        CompletedFailedManualRollback failedStep = rolledBackStep.applyAuditResult(auditResult);
        summarizer.add(failedStep);
        return  failedStep;
    }

    private void auditAutoRollback(CompleteAutoRolledBackStep rolledBackStep, ExecutionContext executionContext, LocalDateTime executedAt) {
        RuntimeContext runtimeContext = RuntimeContext.builder().setAutoRollbackStep(rolledBackStep).setExecutedAt(executedAt).build();
        Result auditResult = auditWriter.writeRollback(new RollbackAuditContextBundle(rolledBackStep.getLoadedTask(), executionContext, runtimeContext));
        logAuditResult(auditResult, rolledBackStep.getLoadedTask().getId(), AUTO_ROLLBACK_DESC);
        summarizer.add(rolledBackStep);
    }

    private static void logAuditResult(Result auditionResult, String id, String description) {

        if (auditionResult instanceof Result.Error) {
            logger.info("change[ {} ] AUDIT FAILED[{}]  ❌ >> {}", id, description, (((Result.Error) auditionResult).getError().getLocalizedMessage()));
        } else {
            logger.info("change[ {} ] AUDITED[{}] ✅", id, description);
        }
    }

}
