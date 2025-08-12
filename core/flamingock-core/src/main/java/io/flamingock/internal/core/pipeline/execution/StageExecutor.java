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
package io.flamingock.internal.core.pipeline.execution;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.pipeline.StageDescriptor;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessStrategy;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessStrategyFactory;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.Set;
import java.util.stream.Stream;

public class StageExecutor {
    protected final ExecutionAuditWriter auditWriter;

    private final ContextResolver baseDependencyContext;
    private final Set<Class<?>> nonGuardedTypes;
    private final TargetSystemManager targetSystemManager;
    protected final TransactionWrapper auditStoreTxWrapper;
    private final boolean relaxTargetSystemValidation;

    public StageExecutor(ContextResolver dependencyContext,
                         Set<Class<?>> nonGuardedTypes,
                         ExecutionAuditWriter auditWriter,
                         TargetSystemManager targetSystemManager,
                         TransactionWrapper auditStoreTxWrapper,
                         boolean relaxTargetSystemValidation) {
        this.baseDependencyContext = dependencyContext;
        this.nonGuardedTypes = nonGuardedTypes;
        this.auditWriter = auditWriter;
        this.targetSystemManager = targetSystemManager;
        this.auditStoreTxWrapper = auditStoreTxWrapper;
        this.relaxTargetSystemValidation = relaxTargetSystemValidation;
    }

    public Output executeStage(ExecutableStage executableStage,
                               ExecutionContext executionContext,
                               Lock lock) throws StageExecutionException {

        StageSummary summary = new StageSummary(executableStage.getName());
        PriorityContext dependencyContext = new PriorityContext(baseDependencyContext);
        dependencyContext.addDependency(new Dependency(StageDescriptor.class, executableStage));
        ChangeProcessStrategyFactory changeProcessFactory = getStepNavigatorBuilder(executionContext, lock, dependencyContext);

        try {
            getTasksStream(executableStage)
                    .map(changeProcessFactory::setChangeUnit)
                    .map(ChangeProcessStrategyFactory::build)
                    .map(ChangeProcessStrategy::applyChange)
                    .peek(summary::addSummary)
                    .filter(TaskSummary::isFailed)
                    .findFirst()
                    .ifPresent(failed -> {
                        throw new StageExecutionException(summary);
                    });

        } catch (StageExecutionException stageExecutionException) {
            throw stageExecutionException;
        } catch (Throwable throwable) {
            throw new StageExecutionException(throwable, summary);
        }

        return new Output(summary);
    }

    private ChangeProcessStrategyFactory getStepNavigatorBuilder(ExecutionContext executionContext, Lock lock, ContextResolver contextResolver) {
        return new ChangeProcessStrategyFactory(targetSystemManager)
                .setExecutionContext(executionContext)
                .setAuditWriter(auditWriter)
                .setDependencyContext(contextResolver)
                .setLock(lock)
                .setNonGuardedTypes(nonGuardedTypes)
                .setRelaxTargetSystemValidation(relaxTargetSystemValidation);
    }

    protected Stream<? extends ExecutableTask> getTasksStream(ExecutableStage executableStage) {
        return executableStage.getTasks().stream();
    }

    public static class Output {

        private final StageSummary summary;

        public Output(StageSummary summary) {
            this.summary = summary;
        }

        public StageSummary getSummary() {
            return summary;
        }
    }
}
