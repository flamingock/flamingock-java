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

package io.flamingock.core.pipeline.execution;

import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.pipeline.ExecutableStage;
import io.flamingock.core.pipeline.StageDescriptor;
import io.flamingock.core.context.Dependency;
import io.flamingock.core.context.DependencyContext;
import io.flamingock.core.context.PriorityDependencyInjectableContext;
import io.flamingock.core.task.executable.ExecutableTask;
import io.flamingock.core.task.navigation.navigator.ReusableStepNavigatorBuilder;
import io.flamingock.core.task.navigation.navigator.StepNavigatorBuilder;
import io.flamingock.core.transaction.TransactionWrapper;

import java.util.stream.Stream;

public class StageExecutor {
    protected final AuditWriter auditWriter;

    protected final TransactionWrapper transactionWrapper;
    private final DependencyContext baseDependencyContext;

    private StageExecutor(DependencyContext dependencyContext, AuditWriter auditWriter) {
        this(dependencyContext, auditWriter, null);
    }

    public StageExecutor(DependencyContext dependencyContext,
                         AuditWriter auditWriter,
                         TransactionWrapper transactionWrapper) {
        this.baseDependencyContext = dependencyContext;
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

        PriorityDependencyInjectableContext dependencyContext = new PriorityDependencyInjectableContext(baseDependencyContext);
        dependencyContext.addDependency(new Dependency(StageDescriptor.class, executableStage));

        try {
            getTasksStream(executableStage)
                    .map(task -> stepNavigatorBuilder
                            .setAuditWriter(auditWriter)
                            .setDependencyContext(dependencyContext)
                            .setLock(lock)
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
