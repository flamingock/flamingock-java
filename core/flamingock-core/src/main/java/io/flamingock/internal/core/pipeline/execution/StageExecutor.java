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
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.store.lock.Lock;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessStrategy;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessStrategyFactory;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Stream;

public class StageExecutor {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("StageExecutor");
    
    protected final LifecycleAuditWriter auditWriter;

    private final ContextResolver baseDependencyContext;
    private final Set<Class<?>> nonGuardedTypes;
    private final TargetSystemManager targetSystemManager;
    protected final TransactionWrapper auditStoreTxWrapper;
    private final boolean relaxTargetSystemValidation;

    public StageExecutor(ContextResolver dependencyContext,
                         Set<Class<?>> nonGuardedTypes,
                         LifecycleAuditWriter auditWriter,
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
        LocalDateTime stageStart = LocalDateTime.now();
        String stageName = executableStage.getName();
        long taskCount = getTasksStream(executableStage).count();
        
        logger.info("Starting stage execution [stage={} tasks={} execution_id={}]", 
                   stageName, taskCount, executionContext.getExecutionId());

        StageSummary summary = new StageSummary(stageName);
        PriorityContext dependencyContext = new PriorityContext(baseDependencyContext);
        dependencyContext.addDependency(new Dependency(StageDescriptor.class, executableStage));
        ChangeProcessStrategyFactory changeProcessFactory = getStepNavigatorBuilder(executionContext, lock, dependencyContext);

        try {
            logger.debug("Processing change units [stage={} context={}]", stageName, executionContext.getExecutionId());
            
            getTasksStream(executableStage)
                    .map(changeProcessFactory::setChangeUnit)
                    .map(ChangeProcessStrategyFactory::build)
                    .map(ChangeProcessStrategy::applyChange)
                    .peek(taskSummary -> {
                        summary.addSummary(taskSummary);
                        if (taskSummary.isFailed()) {
                            logger.error("Change unit failed [change={} stage={}]", 
                                       taskSummary.getId(), stageName);
                        } else {
                            logger.debug("Change unit completed successfully [change={} stage={}]", 
                                       taskSummary.getId(), stageName);
                        }
                    })
                    .filter(TaskSummary::isFailed)
                    .findFirst()
                    .ifPresent(failed -> {
                        Duration stageDuration = Duration.between(stageStart, LocalDateTime.now());
                        logger.error("Stage execution failed [stage={} duration={} failed_change={}]", 
                                   stageName, formatDuration(stageDuration), failed.getId());
                        throw new StageExecutionException(summary);
                    });

            Duration stageDuration = Duration.between(stageStart, LocalDateTime.now());
            logger.info("Stage execution completed successfully [stage={} duration={} tasks={}]", 
                       stageName, formatDuration(stageDuration), taskCount);

        } catch (StageExecutionException stageExecutionException) {
            Duration stageDuration = Duration.between(stageStart, LocalDateTime.now());
            logger.error("Stage execution failed [stage={} duration={}]", 
                       stageName, formatDuration(stageDuration));
            throw stageExecutionException;
        } catch (Throwable throwable) {
            Duration stageDuration = Duration.between(stageStart, LocalDateTime.now());
            logger.error("Stage execution failed with unexpected error [stage={} duration={} error={}]", 
                       stageName, formatDuration(stageDuration), throwable.getMessage(), throwable);
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
    
    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%.1fm", millis / 60000.0);
        }
    }
}
