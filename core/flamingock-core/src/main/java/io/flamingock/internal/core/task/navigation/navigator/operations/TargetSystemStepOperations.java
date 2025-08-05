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
package io.flamingock.internal.core.task.navigation.navigator.operations;

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.step.ExecutableStep;
import io.flamingock.internal.core.task.navigation.step.TaskStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.internal.core.task.navigation.step.afteraudit.RollableStep;
import io.flamingock.internal.core.task.navigation.step.complete.CompletedSuccessStep;
import io.flamingock.internal.core.task.navigation.step.complete.failed.CompleteAutoRolledBackStep;
import io.flamingock.internal.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.internal.core.task.navigation.step.rolledback.ManualRolledBackStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class TargetSystemStepOperations {
    private static final Logger logger = LoggerFactory.getLogger("TargetSystemOperations");

    private final TargetSystem targetSystem;
    private final RuntimeManager runtimeManager;

    public TargetSystemStepOperations(TargetSystem targetSystem, RuntimeManager runtimeManager) {
        this.targetSystem = targetSystem;
        this.runtimeManager = runtimeManager;
    }

    public TaskStep applyChange(ExecutableStep executableStep,
                                Function<ExecutionStep, AfterExecutionAuditStep> changeAuditor) {
        ExecutableTask changeUnit = executableStep.getTask();
        if (isTransactionalTarget() && isTransactionalChange(changeUnit)) {
            logger.debug("Executing(transactional) task[{}]", changeUnit.getId());

            final TransactionalTargetSystem<?> transactionalTargetSystem = (TransactionalTargetSystem<?>) targetSystem;

            transactionalTargetSystem.getOnGoingTaskStatusRepository().registerAsExecuting(changeUnit);

            return transactionalTargetSystem.getTxWrapper().wrapInTransaction(
                    changeUnit,
                    runtimeManager,
                    () -> {
                        ExecutionStep changeAppliedStep = executableStep.execute(runtimeManager);
                        AfterExecutionAuditStep executionAuditResult = changeAuditor.apply(changeAppliedStep);
                        if (executionAuditResult instanceof CompletedSuccessStep) {
                            transactionalTargetSystem.getOnGoingTaskStatusRepository().clean(changeUnit.getId());
                            return executionAuditResult;
                        }
                        return new CompleteAutoRolledBackStep(changeUnit, true);
                    }
            );
        } else {
            logger.debug("Executing(non-transactional) task[{}]", changeUnit.getId());
            ExecutionStep changeAppliedStep = executableStep.execute(runtimeManager);
            return changeAuditor.apply(changeAppliedStep);
        }
    }


    public ManualRolledBackStep rollbackChange(RollableStep rollable) {
        return rollable.rollback(runtimeManager);
    }

    //TODO temporally until we remove disableTransaction in builder
    private boolean isTransactionalTarget() {
        return targetSystem instanceof TransactionalTargetSystem
                && ((TransactionalTargetSystem<?>) targetSystem).getTxWrapper() != null;
    }

    private boolean isTransactionalChange(ExecutableTask changeUnit) {
        return changeUnit.isTransactional();
    }
}
