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
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.core.context.PriorityContext;
import io.flamingock.internal.core.external.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.internal.core.operation.result.StageResultBuilder;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.FailedChangeProcessResult;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessResult;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessStrategy;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessStrategyFactory;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Executes stages and returns structured result data.
 */
public class StageExecutor {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("StageExecutor");

    protected final LifecycleAuditWriter auditWriter;

    private final ContextResolver baseDependencyContext;
    private final Set<Class<?>> nonGuardedTypes;
    private final TargetSystemManager targetSystemManager;
    protected final TransactionWrapper auditStoreTxWrapper;

    public StageExecutor(ContextResolver dependencyContext,
                         Set<Class<?>> nonGuardedTypes,
                         LifecycleAuditWriter auditWriter,
                         TargetSystemManager targetSystemManager,
                         TransactionWrapper auditStoreTxWrapper) {
        this.baseDependencyContext = dependencyContext;
        this.nonGuardedTypes = nonGuardedTypes;
        this.auditWriter = auditWriter;
        this.targetSystemManager = targetSystemManager;
        this.auditStoreTxWrapper = auditStoreTxWrapper;
    }

    public Output executeStage(ExecutableStage executableStage,
                               ExecutionContext executionContext,
                               Lock lock) throws StageExecutionException {
        LocalDateTime stageStart = LocalDateTime.now();
        String stageName = executableStage.getName();
        long taskCount = getTasksStream(executableStage).count();

        logger.info("Stage started [stage={}]", stageName);
        logger.debug("Stage execution context [stage={} tasks={} execution_id={}]",
                stageName, taskCount, executionContext.getExecutionId());

        StageResultBuilder resultBuilder = new StageResultBuilder()
                .stageId(stageName)
                .stageName(stageName)
                .startTimer();

        PriorityContext dependencyContext = new PriorityContext(baseDependencyContext);
        dependencyContext.addDependency(new Dependency(StageDescriptor.class, executableStage));
        ChangeProcessStrategyFactory changeProcessFactory = getStepNavigatorBuilder(executionContext, lock, dependencyContext);

        try {
            logger.debug("Processing changes [stage={} context={}]", stageName, executionContext.getExecutionId());

            getTasksStream(executableStage)
                    .map(changeProcessFactory::setChange)
                    .map(ChangeProcessStrategyFactory::build)
                    .map(ChangeProcessStrategy::applyChange)
                    .peek(result -> {
                        resultBuilder.addChange(result.getResult());
                        if (result.isFailed()) {
                            logger.error("Change failed [change={} stage={}]",
                                    result.getChangeId(), stageName);
                        } else {
                            logger.debug("Change completed successfully [change={} stage={}]",
                                    result.getChangeId(), stageName);
                        }
                    })
                    .filter(ChangeProcessResult::isFailed)
                    .findFirst()
                    .map(processResult -> (FailedChangeProcessResult) processResult)
                    .ifPresent(failedResult -> {
                        resultBuilder.stopTimer().failed();
                        Duration stageDuration = Duration.between(stageStart, LocalDateTime.now());
                        logger.debug("Stage execution failed [stage={} duration={} failed_change={}]",
                                stageName, formatDuration(stageDuration), failedResult.getChangeId());
                        throw StageExecutionException.fromResult(
                                failedResult.getException(),
                                resultBuilder.build(),
                                failedResult.getChangeId()
                        );
                    });

            resultBuilder.stopTimer().completed();
            Duration stageDuration = Duration.between(stageStart, LocalDateTime.now());
            StageResult stageResult = resultBuilder.build();
            logger.info("Stage completed [stage={} duration={} applied={} skipped={}]",
                    stageName, formatDuration(stageDuration), stageResult.getAppliedCount(), stageResult.getSkippedCount());
            return new Output(stageResult);

        } catch (StageExecutionException stageExecutionException) {
            throw stageExecutionException;
        } catch (Throwable throwable) {
            resultBuilder.stopTimer().failed();
            Duration stageDuration = Duration.between(stageStart, LocalDateTime.now());
            logger.debug("Stage execution failed with unexpected error [stage={} duration={} error={}]",
                    stageName, formatDuration(stageDuration), throwable.getMessage(), throwable);
            throw StageExecutionException.fromResult(throwable, resultBuilder.build(), null);
        }
    }

    private ChangeProcessStrategyFactory getStepNavigatorBuilder(ExecutionContext executionContext, Lock lock, ContextResolver contextResolver) {
        return new ChangeProcessStrategyFactory(targetSystemManager)
                .setExecutionContext(executionContext)
                .setAuditWriter(auditWriter)
                .setDependencyContext(contextResolver)
                .setLock(lock)
                .setNonGuardedTypes(nonGuardedTypes);
    }

    protected Stream<? extends ExecutableTask> getTasksStream(ExecutableStage executableStage) {
        return executableStage.getTasks().stream();
    }

    public static class Output {

        private final StageResult result;

        public Output(StageResult result) {
            this.result = result;
        }

        public StageResult getResult() {
            return result;
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
