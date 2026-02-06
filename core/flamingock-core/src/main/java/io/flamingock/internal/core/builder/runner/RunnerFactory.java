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
import io.flamingock.internal.core.builder.args.FlamingockArguments;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;
import io.flamingock.internal.core.operation.AuditListArgs;
import io.flamingock.internal.core.operation.AuditListOperation;
import io.flamingock.internal.core.operation.ExecuteArgs;
import io.flamingock.internal.core.operation.OperationType;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.internal.core.operation.ExecuteOperation;
import io.flamingock.internal.util.StringUtil;
import io.flamingock.internal.util.id.RunnerId;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class RunnerFactory {

    private RunnerFactory() {
    }

    public static Runner getRunner(RunnerId runnerId,
                                   FlamingockArguments args,
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
        switch (args.getOperation()) {
            case EXECUTE:
                return getExecuteRunner(runnerId, pipeline, persistence, executionPlanner, targetSystemManager, coreConfiguration, eventPublisher, dependencyContext, nonGuardedTypes, isThrowExceptionIfCannotObtainLock, finalizer);
            case LIST:
                return getListRunner(runnerId, persistence, finalizer);
            default:
                throw new UnsupportedOperationException(String.format("Operation %s not supported", args.getOperation()));
        }


    }

    private static Runner getListRunner(RunnerId runnerId, AuditPersistence persistence, Runnable finalizer) {
        AuditListArgs args = new AuditListArgs();
        AuditListOperation operation = new AuditListOperation(persistence);
        return new DefaultRunner(runnerId, operation, args, finalizer);
    }

    private static DefaultRunner getExecuteRunner(RunnerId runnerId, LoadedPipeline pipeline, AuditPersistence persistence, ExecutionPlanner executionPlanner, TargetSystemManager targetSystemManager, CoreConfigurable coreConfiguration, EventPublisher eventPublisher, ContextResolver dependencyContext, Set<Class<?>> nonGuardedTypes, boolean isThrowExceptionIfCannotObtainLock, Runnable finalizer) {
        final StageExecutor stageExecutor = new StageExecutor(dependencyContext, nonGuardedTypes, persistence, targetSystemManager, null);
        ExecuteOperation operation = new ExecuteOperation(
                runnerId,
                pipeline,
                executionPlanner,
                stageExecutor,
                buildExecutionContext(coreConfiguration),
                eventPublisher,
                isThrowExceptionIfCannotObtainLock,
                finalizer);
        ExecuteArgs args = new ExecuteArgs(pipeline);
        return new DefaultRunner(runnerId, operation, args, finalizer);
    }


    private static OrphanExecutionContext buildExecutionContext(CoreConfigurable configuration) {
        return new OrphanExecutionContext(StringUtil.hostname(), configuration.getMetadata());
    }

}
