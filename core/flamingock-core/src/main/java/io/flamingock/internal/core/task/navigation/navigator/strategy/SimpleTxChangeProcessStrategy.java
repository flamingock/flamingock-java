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
import io.flamingock.internal.core.targets.operations.TransactionalTargetSystemOps;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.FailedChangeProcessResult;
import io.flamingock.internal.core.task.navigation.navigator.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessResult;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.RollableFailedStep;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.FailedAfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.complete.CompletedSuccessStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.time.LocalDateTime;

/**
 * Change process strategy for transactional target systems with separate audit store.
 * 
 * <p>This strategy is used when the target system supports transactions but uses a separate
 * audit store (either a different database in community edition or cloud-based audit in 
 * cloud edition). Changes are applied within a transaction, but audit operations occur
 * in a separate transaction context.
 * 
 * <h3>Execution Flow</h3>
 * <ol>
 * <li>Audit change start in separate audit store</li>
 * <li>Begin transaction in target system</li>
 * <li>Apply change to target system</li>
 * <li>Mark change as applied in target system (if marker supported)</li>
 * <li>Commit target system transaction</li>
 * <li>Audit execution result in separate audit store</li>
 * <li>Clear target system marker on success</li>
 * <li>On failure: audit rollback and execute rollback chain with best effort</li>
 * </ol>
 * 
 * <h3>Target System State Outcomes</h3>
 * <ul>
 * <li><strong>Success:</strong> Change committed, marker cleared</li>
 * <li><strong>Failure:</strong> Transaction rolled back automatically</li>
 * <li><strong>Partial Success:</strong> Change applied but audit failed (marker remains)</li>
 * </ul>
 * 
 * <h3>Audit Store State Outcomes</h3>
 * <ul>
 * <li><strong>STARTED:</strong> Change execution began but process interrupted</li>
 * <li><strong>STARTED → APPLIED:</strong> Change successfully applied and audited</li>
 * <li><strong>STARTED → FAILED:</strong> Change execution failed</li>
 * <li><strong>STARTED → FAILED → ROLLED_BACK:</strong> Change failed and automatic rollback audited</li>
 * </ul>
 * 
 * <h3>Marker Behavior</h3>
 * <p>When the target system supports markers (not NoOpTargetSystemAuditMarker), this strategy
 * creates a marker indicating change application status. This marker aids in recovery scenarios
 * by providing clear indication of whether the change was successfully applied to the target system.</p>
 */
public class SimpleTxChangeProcessStrategy extends AbstractChangeProcessStrategy<TransactionalTargetSystemOps> {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("SimpleTxStrategy");


    public SimpleTxChangeProcessStrategy(ExecutableTask change,
                                         ExecutionContext executionContext,
                                         TransactionalTargetSystemOps targetSystemOps,
                                         AuditStoreStepOperations auditStoreOperations,
                                         TaskSummarizer summarizer,
                                         LockGuardProxyFactory proxyFactory,
                                         ContextResolver baseContext) {
        super(change, executionContext, targetSystemOps, auditStoreOperations, summarizer, proxyFactory, baseContext, LocalDateTime.now());
    }

    @Override
    protected ChangeProcessResult doApplyChange() {
        logger.debug("Executing transactional task [change={}]", change.getId());

        StartStep startStep = new StartStep(change);

        ExecutableStep executableStep = auditAndLogStartExecution(startStep, executionContext);
        
        // Apply change within target system transaction, create marker if supported
        ExecutionStep changeResult = targetSystemOps.applyChangeTransactional(executionRuntime -> {
            ExecutionStep changeAppliedResult = executableStep.execute(executionRuntime);
            if(changeAppliedResult.isSuccessStep()) {
                targetSystemOps.markApplied(change);
            }
            return changeAppliedResult;
        }, buildExecutionRuntime());

        AfterExecutionAuditStep afterAudit = auditAndLogExecution(changeResult);
        if(changeResult.isSuccessStep()) {
            if(afterAudit instanceof FailedAfterExecutionAuditStep) {
                // Change applied but audit failed - leave marker for recovery
                Throwable mainError = ((FailedAfterExecutionAuditStep)afterAudit)
                        .getMainError();
                return new FailedChangeProcessResult(change.getId(), summarizer.setFailed().getSummary(), mainError);
            } else {
                // Success: change applied and audited, clear marker
                targetSystemOps.clearMark(change.getId());
                return new ChangeProcessResult(change.getId(), summarizer.setSuccessful().getSummary());
            }

        } else {
            // Change execution failed - transaction automatically rolled back
            Throwable mainError = ((FailedAfterExecutionAuditStep)afterAudit)
                    .getMainError();
            auditAndLogAutoRollback();
            rollbackChain((RollableFailedStep) afterAudit, executionContext);
            return new FailedChangeProcessResult(change.getId(), summarizer.setFailed().getSummary(), mainError);
        }
    }

    private void rollbackChain(RollableFailedStep rollableFailedStep, ExecutionContext executionContext) {
        // Skip first rollback (main change) as transaction already rolled it back
        rollableFailedStep.getRollbackSteps()
                .stream().skip(1)
                .forEach(rollableStep -> {
                    ManualRolledBackStep rolledBack = targetSystemOps.rollbackChange(rollableStep::rollback, buildExecutionRuntime());
                    stepLogger.logManualRollbackResult(rolledBack);
                    summarizer.add(rolledBack);
                    auditAndLogManualRollback(rolledBack, executionContext);
                });
    }


    protected void auditAndLogAutoRollback() {
        stepLogger.logAutoRollback(change, 0);
        CompleteAutoRolledBackStep rolledBackStep = new CompleteAutoRolledBackStep(change, true);
        auditAndLogAutoRollback(rolledBackStep, executionContext);
    }
}
