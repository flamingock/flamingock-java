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

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.core.cloud.transaction.CloudTransactioner;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.targets.OngoingTaskStatusRepository;
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.targets.TargetSystemManager;

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

        //TODO fix targetSystem.id
        TargetSystem targetSystem = targetSystemManager.getValueOrDefault("changeUnit.getTargetSystem");
        navigator.setTargetSystem(targetSystem);

        RuntimeManager runtimeManager = RuntimeManager.builder()
                .setDependencyContext(new PriorityContext(staticContext))
                .setLock(lock)
                .setNonGuardedTypes(nonGuardedTypes)
                .build();
        navigator.setRuntimeManager(runtimeManager);

        navigator.setSummarizer(new TaskSummarizer(changeUnit.getId()));


        //THIS WILL BE REMOVED and replaced with TargetSystem
        navigator.setTransactionWrapper(auditStoreTxWrapper);
        OngoingTaskStatusRepository ongoingTasksRepository = auditStoreTxWrapper != null && CloudTransactioner.class.isAssignableFrom(auditStoreTxWrapper.getClass())
                ? (OngoingTaskStatusRepository) auditStoreTxWrapper : null;
        navigator.setOngoingTasksRepository(ongoingTasksRepository);
    }
}