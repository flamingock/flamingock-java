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
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.internal.core.targets.operations.TargetSystemOps;
import io.flamingock.internal.core.targets.operations.TransactionalTargetSystemOps;
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
 * Each step execution occurs within a fresh, isolated {@link ExecutionRuntime} instance,
 * ensuring clean context layering and safe, scoped dependency injection—regardless of whether
 * the execution is transactional or not.
 */
@Deprecated
public class LegacyTargetSystemStepOperations {
    private static final Logger logger = LoggerFactory.getLogger("TargetSystemOperations");

    private final TargetSystemOps targetSystem;
    private final ContextResolver baseContext;
    private final LockGuardProxyFactory lockProxyFactory;


    public LegacyTargetSystemStepOperations(TargetSystemOps targetSystem,
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

            final TransactionalTargetSystemOps transactionalTargetSystem = (TransactionalTargetSystemOps) targetSystem;

            transactionalTargetSystem.registerAsExecuting(changeUnit);

            // Fresh executionRuntime with isolated context layer to ensure clean, per-execution dependency resolution

            ExecutionRuntime executionRuntime = buildExecutionRuntime(changeUnit.getId());

            return transactionalTargetSystem.applyChangeTransactional(
                    rm -> {
                        ExecutionStep changeAppliedStep = executableStep.execute(rm);
                        AfterExecutionAuditStep executionAuditResult = changeAuditor.apply(changeAppliedStep);
                        transactionalTargetSystem.clean(changeUnit.getId(), rm.getContext());
                        return executionAuditResult instanceof CompletedSuccessStep
                                ? executionAuditResult
                                : new CompleteAutoRolledBackStep(changeUnit, true);
                    },
                    executionRuntime
            );
        } else {
            logger.debug("Executing(non-transactional) task[{}]", changeUnit.getId());
            ExecutionRuntime executionRuntime = buildExecutionRuntime(changeUnit.getId());
            ExecutionStep changeAppliedStep = targetSystem.applyChange(executableStep::execute, executionRuntime);
            return changeAuditor.apply(changeAppliedStep);
        }
    }


    public ManualRolledBackStep rollbackChange(RollableStep rollable) {
        ExecutionRuntime runtimeHelper = buildExecutionRuntime(rollable.getTask().getId());
        return targetSystem.applyChange(rollable::rollback, runtimeHelper);
    }


    @NotNull
    private ExecutionRuntime buildExecutionRuntime(String sessionId) {
        Context changeUnitSessionContext = new PriorityContext(new SimpleContext(), baseContext);
        return ExecutionRuntime.builder()
                .setSessionId(sessionId)
                .setDependencyContext(changeUnitSessionContext)
                .setLockGuardProxyFactory(lockProxyFactory)
                .build();
    }


    //TODO temporally until we remove disableTransaction in builder
    private boolean isTransactionalTarget() {
        return targetSystem instanceof TransactionalTargetSystemOps;
    }

    private boolean isTransactionalChange(ExecutableTask changeUnit) {
        return changeUnit.isTransactional();
    }
}
