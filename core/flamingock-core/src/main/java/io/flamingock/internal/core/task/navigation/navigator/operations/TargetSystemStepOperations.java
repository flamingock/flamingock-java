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
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Handles the execution and rollback of {@link ExecutableStep}s over a {@link TargetSystem},
 * coordinating the use of isolated runtime contexts and transactional boundaries when applicable.
 * <p>
 * Each step execution occurs within a fresh, isolated {@link RuntimeManager} instance,
 * ensuring clean context layering and safe, scoped dependency injection—regardless of whether
 * the execution is transactional or not.
 */
public class TargetSystemStepOperations {
    private static final Logger logger = LoggerFactory.getLogger("TargetSystemOperations");

    private final TargetSystem targetSystem;
    private final ContextResolver baseContext;
    private final LockGuardProxyFactory lockProxyFactory;


    public TargetSystemStepOperations(TargetSystem targetSystem,
                                      LockGuardProxyFactory proxyFactory,
                                      ContextResolver baseContext) {
        this.targetSystem = targetSystem;
        this.baseContext = baseContext;
        this.lockProxyFactory = proxyFactory;
    }

    public TaskStep applyChange(ExecutableStep executableStep,
                                Function<ExecutionStep, AfterExecutionAuditStep> changeAuditor) {
        ExecutableTask changeUnit = executableStep.getTask();
        if (isTransactionalTarget() && isTransactionalChange(changeUnit)) {
            logger.debug("Executing(transactional) task[{}]", changeUnit.getId());

            final TransactionalTargetSystem<?> transactionalTargetSystem = (TransactionalTargetSystem<?>) targetSystem;

            transactionalTargetSystem.getOnGoingTaskStatusRepository().registerAsExecuting(changeUnit);

            // Fresh runtimeManager with isolated context layer to ensure clean, per-execution dependency resolution
            RuntimeManager runtimeManager = buildRuntimeManager();

            return transactionalTargetSystem.getTxWrapper().wrapInTransaction(
                    changeUnit,
                    runtimeManager,
                    contextResolver -> {
                        ExecutionStep changeAppliedStep = executableStep.execute(runtimeManager);
                        AfterExecutionAuditStep executionAuditResult = changeAuditor.apply(changeAppliedStep);
                        if (executionAuditResult instanceof CompletedSuccessStep) {
                            transactionalTargetSystem.getOnGoingTaskStatusRepository().clean(changeUnit.getId(), contextResolver);
                            return executionAuditResult;
                        }
                        return new CompleteAutoRolledBackStep(changeUnit, true);
                    }
            );
        } else {
            logger.debug("Executing(non-transactional) task[{}]", changeUnit.getId());
            ExecutionStep changeAppliedStep = executableStep.execute(buildRuntimeManager());
            return changeAuditor.apply(changeAppliedStep);
        }
    }

    @NotNull
    private RuntimeManager buildRuntimeManager() {
        return RuntimeManager.builder()
                .setDependencyContext(new PriorityContext(new SimpleContext(), baseContext))
                .setLockGuardProxyFactory(lockProxyFactory)
                .build();
    }


    public ManualRolledBackStep rollbackChange(RollableStep rollable) {
        return rollable.rollback(buildRuntimeManager());
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
