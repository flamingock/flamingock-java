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

package io.flamingock.internal.core.pipeline.execution;

import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.pipeline.ExecutableStage;
import io.flamingock.core.pipeline.StageDescriptor;
import io.flamingock.core.context.Dependency;
import io.flamingock.core.context.ContextResolver;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.ReusableStepNavigatorBuilder;
import io.flamingock.internal.core.task.navigation.navigator.StepNavigatorBuilder;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.Set;
import java.util.stream.Stream;

public class StageExecutor {
    protected final ExecutionAuditWriter auditWriter;

    protected final TransactionWrapper transactionWrapper;
    private final ContextResolver baseDependencyContext;
    private final Set<Class<?>> nonGuardedTypes;

    public StageExecutor(ContextResolver dependencyContext,
                         Set<Class<?>> nonGuardedTypes,
                         ExecutionAuditWriter auditWriter,
                         TransactionWrapper transactionWrapper) {
        this.baseDependencyContext = dependencyContext;
        this.nonGuardedTypes = nonGuardedTypes;
        this.auditWriter = auditWriter;
        this.transactionWrapper = transactionWrapper;
    }

    public Output executeStage(ExecutableStage executableStage,
                               ExecutionContext executionContext,
                               Lock lock) throws StageExecutionException {

        StageSummary summary = new StageSummary(executableStage.getName());

        StepNavigatorBuilder stepNavigatorBuilder = getStepNavigatorBuilder(executableStage.isParallel());

        //TODO think that we can build the StepNavigator sequentially and then execute it in Parallel
        // this would save memory footprint

        PriorityContext dependencyContext = new PriorityContext(baseDependencyContext);
        dependencyContext.addDependency(new Dependency(StageDescriptor.class, executableStage));

        try {
            getTasksStream(executableStage)
                    .map(task -> stepNavigatorBuilder
                            .setAuditWriter(auditWriter)
                            .setDependencyContext(dependencyContext)
                            .setLock(lock)
                            .setNonGuardedTypes(nonGuardedTypes)
                            .setTransactionWrapper(transactionWrapper)
                            .setSummarizer(new TaskSummarizer(task.getId()))
                            .build()
                            .executeTask(task, executionContext)
                    ).peek(summary::addSummary)
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

    protected Stream<? extends ExecutableTask> getTasksStream(ExecutableStage executableStage) {
        return executableStage.isParallel()
                ? executableStage.getTasks().parallelStream()
                : executableStage.getTasks().stream();
    }

    protected StepNavigatorBuilder getStepNavigatorBuilder(boolean parallel) {
        StepNavigatorBuilder immutableStepNavigatorBuilder = null;
        return parallel ? immutableStepNavigatorBuilder //TODO  implement ConcurrentStepNavigatorBuilder
                : new ReusableStepNavigatorBuilder();
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
