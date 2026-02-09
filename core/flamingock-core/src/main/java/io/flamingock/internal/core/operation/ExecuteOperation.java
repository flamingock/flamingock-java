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

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.response.data.ErrorInfo;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.event.model.impl.PipelineCompletedEvent;
import io.flamingock.internal.core.event.model.impl.PipelineFailedEvent;
import io.flamingock.internal.core.event.model.impl.PipelineStartedEvent;
import io.flamingock.internal.core.event.model.impl.StageCompletedEvent;
import io.flamingock.internal.core.event.model.impl.StageFailedEvent;
import io.flamingock.internal.core.event.model.impl.StageStartedEvent;
import io.flamingock.internal.core.operation.result.ExecutionResultBuilder;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.core.external.store.lock.LockException;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the pipeline and returns structured result data.
 */
public class ExecuteOperation implements Operation<ExecuteArgs, ExecuteResult> {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("PipelineRunner");

    private final RunnerId runnerId;

    private final ExecutionPlanner executionPlanner;

    private final EventPublisher eventPublisher;

    private final boolean throwExceptionIfCannotObtainLock;

    private final StageExecutor stageExecutor;

    private final OrphanExecutionContext orphanExecutionContext;

    private final Runnable finalizer;

    public ExecuteOperation(RunnerId runnerId,
                            ExecutionPlanner executionPlanner,
                            StageExecutor stageExecutor,
                            OrphanExecutionContext orphanExecutionContext,
                            EventPublisher eventPublisher,
                            boolean throwExceptionIfCannotObtainLock,
                            Runnable finalizer) {
        this.runnerId = runnerId;
        this.executionPlanner = executionPlanner;
        this.stageExecutor = stageExecutor;
        this.orphanExecutionContext = orphanExecutionContext;
        this.eventPublisher = eventPublisher;
        this.throwExceptionIfCannotObtainLock = throwExceptionIfCannotObtainLock;
        this.finalizer = finalizer;
    }


    @Override
    public ExecuteResult execute(ExecuteArgs args) {
        ExecuteResponseData result;
        try {
            result = this.execute(args.getPipeline());
        } catch (OperationException operationException) {
            result = operationException.getResult();
            throw operationException;
        } catch (Throwable throwable) {
            throw processAndGetFlamingockException(throwable, null);
        } finally {
            finalizer.run();
        }
        return new ExecuteResult(result);
    }

    private static List<AbstractLoadedStage> validateAndGetExecutableStages(LoadedPipeline pipeline) {
        pipeline.validate();
        List<AbstractLoadedStage> stages = new ArrayList<>();
        if (pipeline.getSystemStage().isPresent()) {
            stages.add(pipeline.getSystemStage().get());
        }
        stages.addAll(pipeline.getStages());
        return stages;
    }

    private ExecuteResponseData execute(LoadedPipeline pipeline) throws FlamingockException {
        List<AbstractLoadedStage> allStages = validateAndGetExecutableStages(pipeline);
        int stageCount = allStages.size();
        long changeCount = allStages.stream()
                .mapToLong(stage -> stage.getTasks().size())
                .sum();
        logger.info("Flamingock execution started [stages={} changes={}]", stageCount, changeCount);

        eventPublisher.publish(new PipelineStartedEvent());
        ExecutionResultBuilder resultBuilder = new ExecutionResultBuilder().startTimer();

        do {
            List<AbstractLoadedStage> stages = validateAndGetExecutableStages(pipeline);
            try (ExecutionPlan execution = executionPlanner.getNextExecution(stages)) {
                // Validate execution plan for manual intervention requirements
                // This centralized validation ensures both community and cloud paths are validated
                execution.validate();

                if (execution.isExecutionRequired()) {
                    execution.applyOnEach((executionId, lock, executableStage) -> {
                        StageResult stageResult = runStage(executionId, lock, executableStage);
                        resultBuilder.addStage(stageResult);
                    });
                } else {
                    break;
                }
            } catch (LockException exception) {

                eventPublisher.publish(new StageFailedEvent(exception));
                eventPublisher.publish(new PipelineFailedEvent(exception));
                if (throwExceptionIfCannotObtainLock) {
                    logger.debug("Required process lock not acquired - ABORTING OPERATION", exception);
                    throw exception;
                } else {
                    logger.warn("Process lock not acquired but throwExceptionIfCannotObtainLock=false - CONTINUING WITHOUT LOCK", exception);
                }
                break;
            } catch (StageExecutionException e) {
                resultBuilder.addStage(e.getResult());
                resultBuilder.stopTimer().failed();
                resultBuilder.error(ErrorInfo.fromThrowable(e.getCause(), e.getFailedChangeId(), e.getResult().getStageId()));
                throw OperationException.fromExisting(e.getCause(), resultBuilder.build());
            }
        } while (true);

        resultBuilder.stopTimer().success();
        ExecuteResponseData result = resultBuilder.build();

        logger.info("Flamingock execution completed [duration={}ms applied={} skipped={}]",
                result.getTotalDurationMs(), result.getAppliedChanges(), result.getSkippedChanges());

        eventPublisher.publish(new PipelineCompletedEvent());

        return result;
    }

    private StageResult runStage(String executionId, Lock lock, ExecutableStage executableStage) {
        try {
            return startStage(executionId, lock, executableStage);
        } catch (StageExecutionException exception) {
            eventPublisher.publish(new StageFailedEvent(exception));
            eventPublisher.publish(new PipelineFailedEvent(exception));
            throw exception;
        } catch (Throwable generalException) {
            throw processAndGetFlamingockException(generalException, null);
        }
    }

    private StageResult startStage(String executionId, Lock lock, ExecutableStage executableStage) throws StageExecutionException {
        eventPublisher.publish(new StageStartedEvent());
        logger.debug("Applied state to process:\n{}", executableStage);

        ExecutionContext executionContext = new ExecutionContext(executionId, orphanExecutionContext.getHostname(), orphanExecutionContext.getMetadata());
        StageExecutor.Output executionOutput = stageExecutor.executeStage(executableStage, executionContext, lock);
        eventPublisher.publish(new StageCompletedEvent(executionOutput));
        return executionOutput.getResult();
    }

    private FlamingockException processAndGetFlamingockException(Throwable exception, ExecutionResultBuilder resultBuilder) throws FlamingockException {
        FlamingockException flamingockException;
        if (exception instanceof OperationException) {
            OperationException pipelineException = (OperationException) exception;
            if (pipelineException.getCause() instanceof FlamingockException) {
                flamingockException = (FlamingockException) pipelineException.getCause();
            } else {
                flamingockException = (OperationException) exception;
            }
        } else if (exception instanceof FlamingockException) {
            flamingockException = (FlamingockException) exception;
        } else {
            flamingockException = new FlamingockException(exception);
        }
        logger.debug("Error executing the process. ABORTED OPERATION", exception);
        eventPublisher.publish(new StageFailedEvent(flamingockException));
        eventPublisher.publish(new PipelineFailedEvent(flamingockException));
        return flamingockException;
    }

}
