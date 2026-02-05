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
package io.flamingock.internal.core.builder.runner;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.internal.core.runner.apply.ApplyOperation;
import io.flamingock.internal.util.StringUtil;
import io.flamingock.internal.util.id.RunnerId;

import java.util.Set;

public final class RunnerFactory {

    private RunnerFactory() {
    }

    public static Runner getApplyRunner(RunnerId runnerId,
                                        LoadedPipeline pipeline,
                                        AuditPersistence persistence,
                                        ExecutionPlanner executionPlanner,
                                        TargetSystemManager targetSystemManager,
                                        CoreConfigurable coreConfiguration,
                                        EventPublisher eventPublisher,
                                        ContextResolver dependencyContext,
                                        Set<Class<?>> nonGuardedTypes,
                                        boolean isThrowExceptionIfCannotObtainLock,
                                        Runnable finalizer) {

        final StageExecutor stageExecutor = new StageExecutor(dependencyContext, nonGuardedTypes, persistence, targetSystemManager, null);
        ApplyOperation applyOperation = new ApplyOperation(
                runnerId,
                pipeline,
                executionPlanner,
                stageExecutor,
                buildExecutionContext(coreConfiguration),
                eventPublisher,
                isThrowExceptionIfCannotObtainLock,
                finalizer);
        return new DefaultRunner(runnerId, finalizer, applyOperation);
    }


    private static OrphanExecutionContext buildExecutionContext(CoreConfigurable configuration) {
        return new OrphanExecutionContext(StringUtil.hostname(), configuration.getMetadata());
    }

}
