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
import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.core.operation.result.ChangeResultBuilder;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.internal.core.external.targets.operations.TransactionalTargetSystemOps;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.FailedChangeProcessResult;
import io.flamingock.internal.core.task.navigation.navigator.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessResult;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.RollableFailedStep;
import io.flamingock.internal.core.task.navigation.step.StartStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.FailedAfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.RollableStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.Wrapper;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

/**
 * Change process strategy for transactional target systems with shared audit store.
 *
 * <p>This strategy is used when the target system and audit store share the same database
 * and can participate in the same transaction.
 *
 * <h3>Execution Flow</h3>
 * <ol>
 * <li>Begin shared transaction</li>
 * <li>Audit change start within transaction</li>
 * <li>Apply change to target system within same transaction</li>
 * <li>Audit execution result within same transaction</li>
 * <li>Commit transaction (both change and audit atomically)</li>
 * <li>On failure: attempt separate transaction to audit failure details</li>
 * </ol>
 *
 * <h3>Target System State Outcomes</h3>
 * <ul>
 * <li><strong>Success:</strong> Change applied atomically with audit</li>
 * <li><strong>Failure:</strong> No changes persisted (transaction rolled back)</li>
 * </ul>
 *
 * <h3>Audit Store State Outcomes</h3>
 * <ul>
 * <li><strong>STARTED → APPLIED:</strong> Successful atomic execution and audit</li>
 * <li><strong>STARTED → EXECUTION_FAILED → ROLLED_BACK:</strong> Failed execution with detailed failure audit</li>
 * <li><strong>No audit trail:</strong> Complete transaction failure (safe to retry)</li>
 * </ul>
 *
 * <h3>Failure Handling</h3>
 * <p>When execution fails, this strategy attempts to create a detailed failure audit trail
 * in a separate transaction: STARTED → EXECUTION_FAILED → ROLLED_BACK. This provides
 * comprehensive failure information while maintaining the safety guarantee that failed
 * changes can be safely retried since nothing was committed in the original transaction.</p>
 *
 */
public class SharedTxChangeProcessStrategy extends AbstractChangeProcessStrategy<TransactionalTargetSystemOps> {
    private static final Void UNUSED = null;
    private static final Logger logger = FlamingockLoggerFactory.getLogger("SharedTxStrategy");

    public SharedTxChangeProcessStrategy(ExecutableTask change,
                                         ExecutionContext executionContext,
                                         TransactionalTargetSystemOps targetSystemOps,
                                         AuditStoreStepOperations auditStoreOperations,
                                         ChangeResultBuilder resultBuilder,
                                         LockGuardProxyFactory proxyFactory,
                                         ContextResolver baseContext,
                                         TimeService timeService) {
        super(change, executionContext, targetSystemOps, auditStoreOperations, resultBuilder, proxyFactory, baseContext, timeService);
    }

    @Override
    protected ChangeProcessResult doApplyChange() {
        resultBuilder.startTimer();

        logger.debug("Executing shared-transactional task [change={}]", change.getId());

        Wrapper<ExecutionStep> executionStep = new Wrapper<>(null);
        // Execute change and audit within single transaction
        AfterExecutionAuditStep changeExecutionAndAudit = targetSystemOps.applyChangeTransactional(executionRuntime -> {
            ExecutableStep executableStep = auditAndLogStartExecution(new StartStep(change), executionContext);
            executionStep.setValue(executableStep.execute(executionRuntime));
            return auditAndLogExecution(executionStep.getValue());
        }, buildExecutionRuntime());

        resultBuilder.stopTimer();

        if(changeExecutionAndAudit instanceof FailedAfterExecutionAuditStep) {
            // Failure:this means nothing was persisted(all or nothing)
            auditIfExecutionFailure(executionStep);
            Throwable mainError = ((FailedAfterExecutionAuditStep)changeExecutionAndAudit)
                    .getMainError();
            rollbackChain((RollableFailedStep) changeExecutionAndAudit, executionContext);
            ChangeResult result = resultBuilder
                    .rolledBack()
                    .error(mainError)
                    .build();
            return new FailedChangeProcessResult(change.getId(), result, mainError);
        } else {
            // Success: both change and audit committed atomically
            ChangeResult result = resultBuilder
                    .applied()
                    .build();
            return new ChangeProcessResult(change.getId(), result);
        }

    }


    /**
     * Creates detailed failure audit when execution fails.
     *
     * <p>Since the main transaction was rolled back, this method attempts to create
     * a comprehensive failure audit trail in a separate transaction. This provides
     * valuable diagnostic information while maintaining system safety.
     */
    private void auditIfExecutionFailure(Wrapper<ExecutionStep> executionStep) {
        ExecutionStep changeExecution = executionStep.getValue();
        if(!changeExecution.isSuccessStep()) {
            targetSystemOps.<Void>applyChangeTransactional(executionRuntime -> {
                auditAndLogStartExecution(new StartStep(change), executionContext);
                auditAndLogExecution(changeExecution);
                auditAndLogAutoRollback();
                return UNUSED;
            }, buildExecutionRuntime());
        }
    }

    private void rollbackChain(RollableFailedStep rollableFailedStep, ExecutionContext executionContext) {
        // Skip first rollback (main change) as transaction already rolled it back
        RollableStep rollableStep = rollableFailedStep.getRollbackStep();
        ManualRolledBackStep rolledBack = targetSystemOps.rollbackChange(rollableStep::rollback, buildExecutionRuntime());
        stepLogger.logManualRollbackResult(rolledBack);
        auditAndLogManualRollback(rolledBack, executionContext);
    }


    protected void auditAndLogAutoRollback() {
        stepLogger.logAutoRollback(change, 0);
        CompleteAutoRolledBackStep rolledBackStep = new CompleteAutoRolledBackStep(change, true);
        auditAndLogAutoRollback(rolledBackStep, executionContext);
    }
}
