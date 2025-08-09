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

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.core.targets.operations.TargetSystemOps;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.operations.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.operations.LegacyTargetSystemStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.strategy.SimpleChangeProcessStrategy;

import java.util.Set;

public class ChangeProcessStrategyFactory {
    private final TargetSystemManager targetSystemManager;

    protected ExecutableTask changeUnit;

    protected ExecutionAuditWriter auditWriter;

    protected Lock lock;

    protected ContextResolver baseContext;

    protected Set<Class<?>> nonGuardedTypes;

    protected ExecutionContext executionContext;


    ChangeProcessStrategyFactory(TargetSystemManager targetSystemManager) {
        this.targetSystemManager = targetSystemManager;
    }

    public ChangeProcessStrategyFactory setChangeUnit(ExecutableTask changeUnit) {
        this.changeUnit = changeUnit;
        return this;
    }

    public ChangeProcessStrategyFactory setAuditWriter(ExecutionAuditWriter auditWriter) {
        this.auditWriter = auditWriter;
        return this;
    }

    public ChangeProcessStrategyFactory setDependencyContext(ContextResolver staticContext) {
        this.baseContext = staticContext;
        return this;
    }

    public ChangeProcessStrategyFactory setNonGuardedTypes(Set<Class<?>> nonGuardedTypes) {
        this.nonGuardedTypes = nonGuardedTypes;
        return this;
    }

    public ChangeProcessStrategyFactory setLock(Lock lock) {
        this.lock = lock;
        return this;
    }

    public ChangeProcessStrategyFactory setExecutionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        return this;
    }


    public ChangeProcessStrategy build() {
        TargetSystemOps targetSystem = targetSystemManager.getValueOrDefault(changeUnit.getTargetSystem());

        LockGuardProxyFactory lockGuardProxyFactory = LockGuardProxyFactory.withLockAndNonGuardedClasses(lock, nonGuardedTypes);

        //TODO will be removed when all strategy implemented and not TargetSystemStepOperations not needed
        LegacyTargetSystemStepOperations targetSystemOps = new LegacyTargetSystemStepOperations(targetSystem, lockGuardProxyFactory, targetSystem.decorateOnTop(baseContext));


        return getStrategy(
                changeUnit,
                targetSystem,
                new AuditStoreStepOperations(auditWriter),
                baseContext,
                executionContext,
                new TaskSummarizer(changeUnit),
                lockGuardProxyFactory,
                targetSystemOps
        );

    }

    public static ChangeProcessStrategy getStrategy(ExecutableTask changeUnit,
                                                    TargetSystemOps targetSystem,
                                                    AuditStoreStepOperations auditStoreOperations,
                                                    ContextResolver baseContext,
                                                    ExecutionContext executionContext,
                                                    TaskSummarizer summarizer,
                                                    LockGuardProxyFactory proxyFactory,
                                                    //THIS WILL BE REMOVED
                                                    LegacyTargetSystemStepOperations targetSystemOps) {
        if (!changeUnit.isTransactional()) {
            return new SimpleChangeProcessStrategy(changeUnit, executionContext, targetSystem, auditStoreOperations, summarizer, proxyFactory, baseContext);
        }
        switch (targetSystem.getOperationType()) {
            case SIMPLE:
                return new SimpleChangeProcessStrategy(changeUnit, executionContext, targetSystem, auditStoreOperations, summarizer, proxyFactory, baseContext);
            case TX_AUDIT_STORE_SHARED:
            case TX_AUDIT_STORE_SYNC:
            case TX_NONSYNC:
            default:
                return new StepNavigator(changeUnit, executionContext, targetSystemOps, auditStoreOperations, summarizer);
        }
    }




}