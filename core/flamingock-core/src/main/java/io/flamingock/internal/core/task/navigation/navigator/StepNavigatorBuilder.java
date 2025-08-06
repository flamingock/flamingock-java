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

import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.cloud.transaction.CloudTransactioner;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.targets.ContextDecoratorTargetSystem;
import io.flamingock.internal.core.targets.NoOpOnGoingTaskStatusRepository;
import io.flamingock.internal.core.targets.OngoingTaskStatusRepository;
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.operations.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.operations.TargetSystemStepOperations;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.Set;

public class StepNavigatorBuilder {
    private final TargetSystemManager targetSystemManager;

    protected ExecutableTask changeUnit;

    protected ExecutionAuditWriter auditWriter = null;

    protected Lock lock = null;

    protected ContextResolver staticContext;

    protected Set<Class<?>> nonGuardedTypes;

    protected ExecutionContext executionContext;

    protected TransactionWrapper auditStoreTxWrapper = null;

    StepNavigatorBuilder(TargetSystemManager targetSystemManager) {
        this.targetSystemManager = targetSystemManager;
    }

    public StepNavigatorBuilder setChangeUnit(ExecutableTask changeUnit) {
        this.changeUnit = changeUnit;
        return this;
    }

    public StepNavigatorBuilder setAuditWriter(ExecutionAuditWriter auditWriter) {
        this.auditWriter = auditWriter;
        return this;
    }

    public StepNavigatorBuilder setDependencyContext(ContextResolver staticContext) {
        this.staticContext = staticContext;
        return this;
    }

    public StepNavigatorBuilder setNonGuardedTypes(Set<Class<?>> nonGuardedTypes) {
        this.nonGuardedTypes = nonGuardedTypes;
        return this;
    }

    public StepNavigatorBuilder setLock(Lock lock) {
        this.lock = lock;
        return this;
    }

    public StepNavigatorBuilder setExecutionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        return this;
    }

    public StepNavigatorBuilder setAuditStoreTxWrapper(TransactionWrapper auditStoreTxWrapper) {
        this.auditStoreTxWrapper = auditStoreTxWrapper;
        return this;
    }

    public StepNavigator build() {
        ContextDecoratorTargetSystem targetSystem = targetSystemManager.getValueOrDefault(changeUnit.getTargetSystem());

        //TODO after ensuring DefaultTargetSystem, it will never be null
        ContextResolver contextWithTsContextDecorated = targetSystem != null
                ? targetSystem.decorateOnTop(staticContext)
                : new PriorityContext(staticContext);
        Context changeUnitSessionContext = new PriorityContext(new SimpleContext(), contextWithTsContextDecorated);

        RuntimeManager runtimeManager = RuntimeManager.builder()
                .setDependencyContext(changeUnitSessionContext)
                .setLock(lock)
                .setNonGuardedTypes(nonGuardedTypes)
                .build();

        return new StepNavigator(
                changeUnit,
                executionContext,
                buildTargetSystemOperations(auditStoreTxWrapper, runtimeManager),//TODO new TargetSystemOperations(targetSystem, runtimeManager)
                new AuditStoreStepOperations(auditWriter),
                new TaskSummarizer(changeUnit));
    }


    //TODO temporal until we have the TargetSystem for Driver
    public static TargetSystemStepOperations buildTargetSystemOperations(TransactionWrapper txWrapper, RuntimeManager runtimeManager) {

        OngoingTaskStatusRepository ongoingTasksRepository = txWrapper != null && CloudTransactioner.class.isAssignableFrom(txWrapper.getClass())
                ? (OngoingTaskStatusRepository) txWrapper : null;
        return new TargetSystemStepOperations(new TempTargetSystem("temporal-target-system", txWrapper, ongoingTasksRepository), runtimeManager);
    }

    private static class TempTargetSystem extends TransactionalTargetSystem<TempTargetSystem> {

        private final TransactionWrapper txWrapper;
        private final OngoingTaskStatusRepository onGoingTaskStatusRepository;

        public TempTargetSystem(String id, TransactionWrapper txWrapper, OngoingTaskStatusRepository ongoingTaskStatusRepository) {
            super(id);
            this.txWrapper = txWrapper;
            this.onGoingTaskStatusRepository = ongoingTaskStatusRepository != null ? ongoingTaskStatusRepository : new NoOpOnGoingTaskStatusRepository(id);
        }

        @Override
        public OngoingTaskStatusRepository getOnGoingTaskStatusRepository() {
            return onGoingTaskStatusRepository;
        }

        @Override
        public TransactionWrapper getTxWrapper() {
            return txWrapper;
        }

        @Override
        public void initialize(ContextResolver baseContext) {

        }

        @Override
        protected TempTargetSystem getSelf() {
            return this;
        }
    }
}