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
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.targets.ContextComposerTargetSystem;
import io.flamingock.internal.core.targets.NoOpOnGoingTaskStatusRepository;
import io.flamingock.internal.core.targets.OngoingTaskStatusRepository;
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.core.targets.TargetSystemOperations;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;

public class ReusableStepNavigatorBuilder extends StepNavigatorBuilder.AbstractStepNavigator {

    private final StepNavigator navigator = new StepNavigator();
    private final TargetSystemManager targetSystemManager;

    public ReusableStepNavigatorBuilder(TargetSystemManager targetSystemManager) {
        this.targetSystemManager = targetSystemManager;
    }


    @Override
    public StepNavigator build() {
        navigator.clean();
        setBaseDependencies();
        return navigator;
    }

    private void setBaseDependencies() {
        navigator.setChangeUnit(changeUnit);
        navigator.setExecutionContext(executionContext);
        navigator.setAuditWriter(auditWriter);

        ContextComposerTargetSystem targetSystem = targetSystemManager.getValueOrDefault(changeUnit.getTargetSystem());


        Context enhancedContext = targetSystem != null
                ? targetSystem.compose(staticContext)
                : new PriorityContext(staticContext);
        RuntimeManager runtimeManager = RuntimeManager.builder()
                .setDependencyContext(enhancedContext)
                .setLock(lock)
                .setNonGuardedTypes(nonGuardedTypes)
                .build();

        navigator.setRuntimeManager(runtimeManager);

        navigator.setSummarizer(new TaskSummarizer(changeUnit.getId()));

        //THIS WILL BE REMOVED and replaced with TargetSystem
        OngoingTaskStatusRepository ongoingTasksRepository = auditStoreTxWrapper != null && CloudTransactioner.class.isAssignableFrom(auditStoreTxWrapper.getClass())
                ? (OngoingTaskStatusRepository) auditStoreTxWrapper : null;
        navigator.setTargetSystemOps(buildTargetSystemOperations(auditStoreTxWrapper, ongoingTasksRepository, runtimeManager));
//        navigator.setTargetSystemOps(new TargetSystemOperations(targetSystem));
    }


    //TODO temporal until we have the TargetSystem for Driver
    public static TargetSystemOperations buildTargetSystemOperations(TransactionWrapper txWrapper, OngoingTaskStatusRepository ongoingTaskStatusRepository, RuntimeManager runtimeManager) {
        return new TargetSystemOperations(new TempTargetSystem("temporal-target-system", txWrapper, ongoingTaskStatusRepository), runtimeManager);
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