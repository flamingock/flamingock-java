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
package io.flamingock.internal.core.task.navigation.navigator;

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.internal.core.targets.AbstractTargetSystem;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.operations.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.operations.TargetSystemStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.strategy.NonTxChangeProcessStrategy;

public class ChangeProcessStrategyFactory {

    public static ChangeProcessStrategy getStrategy(ExecutableTask changeUnit,
                                                    AbstractTargetSystem<?> targetSystem,
                                                    AuditStoreStepOperations auditStoreOperations,
                                                    ContextResolver baseContext,
                                                    ExecutionContext executionContext,
                                                    TaskSummarizer summarizer,
                                                    LockGuardProxyFactory proxyFactory,
                                                    //THIS WILL BE REMOVED
                                                    TargetSystemStepOperations targetSystemOps) {

        ChangeType changeType = getChangeType(targetSystem, changeUnit);
        switch (changeType) {
            case NON_TRANSACTIONAL:
                return new NonTxChangeProcessStrategy(changeUnit, executionContext, targetSystem, auditStoreOperations, summarizer, proxyFactory, baseContext);
            case TRANSACTIONAL_NONSYNC:
            case TRANSACTIONAL_SYNC:
            case TRANSACTIONAL_SHARED:
            default:
                return new StepNavigator(changeUnit, executionContext, targetSystemOps, auditStoreOperations, summarizer);
        }
    }

    private static ChangeType getChangeType(AbstractTargetSystem<?> targetSystem, ExecutableTask changeUnit) {
        if(!changeUnit.isTransactional() || !(targetSystem instanceof TransactionalTargetSystem)) {
            return ChangeType.NON_TRANSACTIONAL;
        }

        TransactionalTargetSystem<?> txTargetSystem = (TransactionalTargetSystem<?>) targetSystem;

        if(txTargetSystem.inSyncWithAuditStore()) {
            return ChangeType.TRANSACTIONAL_NONSYNC;
        } else {
            return ChangeType.TRANSACTIONAL_SYNC;
        }
    }


    private enum ChangeType {
        NON_TRANSACTIONAL,
        TRANSACTIONAL_NONSYNC,
        TRANSACTIONAL_SYNC,
        TRANSACTIONAL_SHARED
    }
}
