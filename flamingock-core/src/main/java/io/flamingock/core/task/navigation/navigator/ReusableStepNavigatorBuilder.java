/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.core.task.navigation.navigator;

import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.cloud.transaction.OngoingStatusRepository;
import io.flamingock.core.runtime.RuntimeManager;
import io.flamingock.core.runtime.dependency.PriorityDependencyInjectableContext;

public class ReusableStepNavigatorBuilder extends StepNavigatorBuilder.AbstractStepNavigator {

    private final StepNavigator instance = new StepNavigator(null, null, null, null, null);


    public ReusableStepNavigatorBuilder() {
    }


    @Override
    public StepNavigator build() {
        instance.clean();
        if (summarizer != null) {
            summarizer.clear();
        }
        setBaseDependencies();
        return instance;
    }

    private void setBaseDependencies() {
        instance.setSummarizer(summarizer);
        instance.setAuditWriter(auditWriter);

        RuntimeManager runtimeManager = RuntimeManager.builder()
                .setDependencyContext(new PriorityDependencyInjectableContext(staticContext))
                .setLock(lock)
                .build();
        instance.setRuntimeManager(runtimeManager);
        instance.setTransactionWrapper(transactionWrapper);
        OngoingStatusRepository ongoingTasksRepository = transactionWrapper != null && CloudTransactioner.class.isAssignableFrom(transactionWrapper.getClass())
                ? (OngoingStatusRepository) transactionWrapper : null;
        instance.setOngoingTasksRepository(ongoingTasksRepository);
    }
}