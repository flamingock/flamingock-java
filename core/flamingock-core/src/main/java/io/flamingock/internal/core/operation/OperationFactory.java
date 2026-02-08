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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.operation.OperationType;
import io.flamingock.internal.core.builder.args.FlamingockArguments;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.util.StringUtil;
import io.flamingock.internal.util.id.RunnerId;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class OperationFactory {

    private final RunnerId runnerId;
    private final FlamingockArguments flamingockArgs;
    private final LoadedPipeline pipeline;
    private final AuditPersistence persistence;
    private final ExecutionPlanner executionPlanner;
    private final TargetSystemManager targetSystemManager;
    private final CoreConfigurable coreConfiguration;
    private final EventPublisher eventPublisher;
    private final ContextResolver dependencyContext;
    private final Set<Class<?>> nonGuardedTypes;
    private final boolean isThrowExceptionIfCannotObtainLock;
    private final Runnable finalizer;

    public OperationFactory(RunnerId runnerId,
                             FlamingockArguments flamingockArgs,
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
        this.runnerId = runnerId;
        this.flamingockArgs = flamingockArgs;
        this.pipeline = pipeline;
        this.persistence = persistence;
        this.executionPlanner = executionPlanner;
        this.targetSystemManager = targetSystemManager;
        this.coreConfiguration = coreConfiguration;
        this.eventPublisher = eventPublisher;
        this.dependencyContext = dependencyContext;
        this.nonGuardedTypes = nonGuardedTypes;
        this.isThrowExceptionIfCannotObtainLock = isThrowExceptionIfCannotObtainLock;
        this.finalizer = finalizer;
    }

    public  RunnableOperation<?, ?> getOperation() {
        switch (flamingockArgs.getOperation()) {
            case EXECUTE:
                return getExecuteOperation();
            case LIST:
                return getAuditListOperation();
            default:
                throw new UnsupportedOperationException(String.format("Operation %s not supported", flamingockArgs.getOperation()));
        }
    }

    private RunnableOperation<AuditListArgs, AuditListResult> getAuditListOperation() {
        AuditListOperation auditListOperation = new AuditListOperation(persistence);
        return new RunnableOperation<>(auditListOperation, new AuditListArgs());
    }

    private  RunnableOperation<ExecuteArgs, ExecuteResult> getExecuteOperation() {
        final StageExecutor stageExecutor = new StageExecutor(dependencyContext, nonGuardedTypes, persistence, targetSystemManager, null);
        ExecuteOperation executeOperation = new ExecuteOperation(
                runnerId,
                executionPlanner,
                stageExecutor,
                buildExecutionContext(coreConfiguration),
                eventPublisher,
                isThrowExceptionIfCannotObtainLock,
                finalizer);
        return new RunnableOperation<>(executeOperation, new ExecuteArgs(pipeline));
    }


    private static OrphanExecutionContext buildExecutionContext(CoreConfigurable configuration) {
        return new OrphanExecutionContext(StringUtil.hostname(), configuration.getMetadata());
    }

}
