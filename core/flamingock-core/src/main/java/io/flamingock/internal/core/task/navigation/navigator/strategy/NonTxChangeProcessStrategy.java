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
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.internal.core.targets.operations.TargetSystemOps;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.FailedChangeProcessResult;
import io.flamingock.internal.core.task.navigation.navigator.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessResult;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.RollableFailedStep;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.FailedAfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.time.LocalDateTime;

/**
 * Change process strategy for non-transactional target systems.
 * 
 * <p>This strategy is used for target systems that do not support transactions,
 * such as message queues, REST services, S3 buckets, or other external systems
 * where changes cannot be atomically rolled back.
 * 
 * <h3>Execution Flow</h3>
 * <ol>
 * <li>Audit change start in audit store</li>
 * <li>Apply change to target system</li>
 * <li>Audit execution result in audit store</li>
 * <li>If execution failed, attempt manual rollback of change chain</li>
 * </ol>
 * 
 * <h3>Target System State Outcomes</h3>
 * <ul>
 * <li><strong>Success:</strong> Change applied to target system</li>
 * <li><strong>Failure:</strong> Change not applied, rollback chain applied with best effort</li>
 * </ul>
 * 
 * <h3>Audit Store State Outcomes</h3>
 * <ul>
 * <li><strong>STARTED:</strong> Change execution began but process interrupted</li>
 * <li><strong>STARTED → APPLIED:</strong> Change successfully applied and audited</li>
 * <li><strong>STARTED → FAILED:</strong> Change execution failed</li>
 * <li><strong>STARTED → FAILED → ROLLED_BACK:</strong> Change failed and rollback chain completed</li>
 * </ul>
 * 
 * <h3>Recovery Considerations</h3>
 * <p>Non-transactional target systems require careful recovery handling since changes 
 * cannot be automatically rolled back. The rollback chain mechanism provides best-effort 
 * cleanup, but manual intervention may be required for complete system consistency.</p>
 */
public class NonTxChangeProcessStrategy extends AbstractChangeProcessStrategy<TargetSystemOps> {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("NonTxStrategy");


    public NonTxChangeProcessStrategy(ExecutableTask change,
                                      ExecutionContext executionContext,
                                      TargetSystemOps targetSystem,
                                      AuditStoreStepOperations auditStoreOperations,
                                      TaskSummarizer summarizer,
                                      LockGuardProxyFactory proxyFactory,
                                      ContextResolver baseContext) {
        super(change, executionContext, targetSystem, auditStoreOperations, summarizer, proxyFactory, baseContext, LocalDateTime.now());
    }

    @Override
    protected ChangeProcessResult doApplyChange() {
        StartStep startStep = new StartStep(change);

        ExecutableStep executableStep = auditAndLogStartExecution(startStep, executionContext);

        logger.debug("Executing non-transactional task [change={}]", change.getId());
        
        ExecutionStep changeAppliedStep = targetSystemOps.applyChange(executableStep::execute, buildExecutionRuntime());

        AfterExecutionAuditStep afterAudit = auditAndLogExecution(changeAppliedStep);


        if(afterAudit instanceof FailedAfterExecutionAuditStep) {
            FailedAfterExecutionAuditStep failedAfterExecutionAudit = (FailedAfterExecutionAuditStep)afterAudit;
            rollbackActualChangeAndChain(failedAfterExecutionAudit, executionContext);
            TaskSummary summary = summarizer.setFailed().getSummary();
            return new FailedChangeProcessResult(change.getId(), summary, failedAfterExecutionAudit.getMainError());
        } else {
            return new ChangeProcessResult(change.getId(), summarizer.setSuccessful().getSummary());
        }

    }

    private void rollbackActualChangeAndChain(FailedAfterExecutionAuditStep rollableFailedStep, ExecutionContext executionContext) {
        rollableFailedStep.getRollbackSteps().forEach(rollableStep -> {
            ManualRolledBackStep rolledBack = targetSystemOps.rollbackChange(rollableStep::rollback, buildExecutionRuntime());
            stepLogger.logManualRollbackResult(rolledBack);
            summarizer.add(rolledBack);
            auditAndLogManualRollback(rolledBack, executionContext);
        });
    }
}
