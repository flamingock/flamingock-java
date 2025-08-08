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

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.pipeline.execution.TaskSummary;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.internal.core.targets.AbstractTargetSystem;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.operations.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.RollableFailedStep;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class NonTxChangeProcessStrategy extends AbstractChangeProcessStrategy {
    private static final Logger logger = LoggerFactory.getLogger("NonTxChangeStrategy");


    public NonTxChangeProcessStrategy(ExecutableTask changeUnit,
                                      ExecutionContext executionContext,
                                      AbstractTargetSystem<?> targetSystem,
                                      AuditStoreStepOperations auditStoreOperations,
                                      TaskSummarizer summarizer,
                                      LockGuardProxyFactory proxyFactory,
                                      ContextResolver baseContext) {
        super(changeUnit, executionContext, targetSystem, auditStoreOperations, summarizer, proxyFactory, baseContext);
    }

    @Override
    protected TaskSummary doApplyChange() {
        stepLogger.logExecutionStart(changeUnit);

        StartStep startStep = new StartStep(changeUnit);

        ExecutableStep executableStep = auditAndLogStartExecution(startStep, executionContext, LocalDateTime.now());

        logger.debug("Executing(non-transactional) task[{}]", changeUnit.getId());
        
        ExecutionStep changeAppliedStep = targetSystem.applyChange(executableStep::execute, buildExecutionRuntime());

        AfterExecutionAuditStep afterAudit = auditAndLogExecution(changeAppliedStep);

        if (afterAudit instanceof RollableFailedStep) {
            return rollback((RollableFailedStep) afterAudit, executionContext);
        } else {
            return summarizer.setSuccessful().getSummary();
        }
    }

    private TaskSummary rollback(RollableFailedStep rollableFailedStep, ExecutionContext executionContext) {
        rollableFailedStep.getRollbackSteps().forEach(rollableStep -> {
            ManualRolledBackStep rolledBack = rollableStep.rollback(buildExecutionRuntime());
            stepLogger.logManualRollbackResult(rolledBack);
            summarizer.add(rolledBack);
            auditAndLogManualRollback(rolledBack, executionContext, LocalDateTime.now());
        });

        return summarizer.setFailed().getSummary();
    }
}
