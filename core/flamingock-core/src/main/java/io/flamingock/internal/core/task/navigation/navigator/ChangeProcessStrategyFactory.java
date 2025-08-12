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
import io.flamingock.internal.core.targets.operations.TransactionalTargetSystemOps;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.strategy.NonTxChangeProcessStrategy;
import io.flamingock.internal.core.task.navigation.navigator.strategy.SharedTxChangeProcessStrategy;
import io.flamingock.internal.core.task.navigation.navigator.strategy.SimpleTxChangeProcessStrategy;

import java.util.Set;

/**
 * Factory for creating appropriate change process strategies based on target system characteristics.
 *
 * <p>This factory determines the optimal execution strategy by analyzing the target system's
 * transactional capabilities and relationship to the audit store. The strategy selection
 * ensures optimal consistency guarantees and performance characteristics for each scenario.
 *
 * <h3>Strategy Selection Logic</h3>
 * <ul>
 * <li><strong>Non-transactional changes:</strong> Always use {@link NonTxChangeProcessStrategy}</li>
 * <li><strong>NON_TX operation type:</strong> Use {@link NonTxChangeProcessStrategy}</li>
 * <li><strong>TX_NON_SYNC or TX_AUDIT_STORE_SYNC:</strong> Use {@link SimpleTxChangeProcessStrategy}</li>
 * <li><strong>TX_AUDIT_STORE_SHARED:</strong> Use {@link SharedTxChangeProcessStrategy}</li>
 * </ul>
 *
 * <h3>Builder Pattern</h3>
 * <p>The factory uses a builder pattern to collect all necessary components before
 * strategy creation, ensuring all dependencies are properly configured.
 */
public class ChangeProcessStrategyFactory {
    private final TargetSystemManager targetSystemManager;

    protected ExecutableTask changeUnit;

    protected ExecutionAuditWriter auditWriter;

    protected Lock lock;

    protected ContextResolver baseContext;

    protected Set<Class<?>> nonGuardedTypes;

    protected ExecutionContext executionContext;

    protected boolean relaxTargetSystemValidation = true;


    public ChangeProcessStrategyFactory(TargetSystemManager targetSystemManager) {
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

    public ChangeProcessStrategyFactory setRelaxTargetSystemValidation(boolean relaxTargetSystemValidation) {
        this.relaxTargetSystemValidation = relaxTargetSystemValidation;
        return this;
    }


    public ChangeProcessStrategy build() {
        TargetSystemOps targetSystem = targetSystemManager.getTargetSystem(changeUnit.getTargetSystem(), relaxTargetSystemValidation);

        LockGuardProxyFactory lockGuardProxyFactory = LockGuardProxyFactory.withLockAndNonGuardedClasses(lock, nonGuardedTypes);

        // Legacy wrapper - will be removed when all strategies are fully implemented

        return getStrategy(
                changeUnit,
                targetSystem,
                new AuditStoreStepOperations(auditWriter),
                baseContext,
                executionContext,
                new TaskSummarizer(changeUnit),
                lockGuardProxyFactory
        );

    }

    /**
     * Creates the appropriate change process strategy based on target system characteristics.
     *
     * <p>Strategy selection considers both the change unit's transactional requirements
     * and the target system's operation type to ensure optimal execution patterns.
     *
     * @param changeUnit           The change to be executed
     * @param targetSystemOps      Target system operations interface
     * @param auditStoreOperations Audit store operations interface
     * @param baseContext          Base dependency resolution context
     * @param executionContext     Execution context for the change
     * @param summarizer           Task summarizer for execution tracking
     * @param proxyFactory         Lock guard proxy factory
     * @return Configured strategy instance appropriate for the target system
     */
    public static ChangeProcessStrategy getStrategy(ExecutableTask changeUnit,
                                                    TargetSystemOps targetSystemOps,
                                                    AuditStoreStepOperations auditStoreOperations,
                                                    ContextResolver baseContext,
                                                    ExecutionContext executionContext,
                                                    TaskSummarizer summarizer,
                                                    LockGuardProxyFactory proxyFactory) {
        if (!changeUnit.isTransactional()) {
            return new NonTxChangeProcessStrategy(changeUnit, executionContext, targetSystemOps, auditStoreOperations, summarizer, proxyFactory, baseContext);
        }
        switch (targetSystemOps.getOperationType()) {
            case NON_TX:
                return new NonTxChangeProcessStrategy(changeUnit, executionContext, targetSystemOps, auditStoreOperations, summarizer, proxyFactory, baseContext);

            case TX_NON_SYNC:
            case TX_AUDIT_STORE_SYNC:
                TransactionalTargetSystemOps txTargetSystemOps = (TransactionalTargetSystemOps) targetSystemOps;
                return new SimpleTxChangeProcessStrategy(changeUnit, executionContext, txTargetSystemOps, auditStoreOperations, summarizer, proxyFactory, baseContext);

            case TX_AUDIT_STORE_SHARED:
            default:
                TransactionalTargetSystemOps sharedTxTargetSystemOps = (TransactionalTargetSystemOps) targetSystemOps;
                return new SharedTxChangeProcessStrategy(changeUnit, executionContext, sharedTxTargetSystemOps, auditStoreOperations, summarizer, proxyFactory, baseContext);
        }
    }


}