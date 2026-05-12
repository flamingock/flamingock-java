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
import io.flamingock.internal.common.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.common.core.error.PendingChangesException;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.event.model.impl.PipelineCompletedEvent;
import io.flamingock.internal.core.event.model.impl.PipelineFailedEvent;
import io.flamingock.internal.core.event.model.impl.PipelineStartedEvent;
import io.flamingock.internal.core.event.model.impl.StageCompletedEvent;
import io.flamingock.internal.core.event.model.impl.StageFailedEvent;
import io.flamingock.internal.core.event.model.impl.StageStartedEvent;
import io.flamingock.internal.core.operation.execute.ExecuteArgs;
import io.flamingock.internal.core.operation.execute.ExecuteResult;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.run.PipelineRun;
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
 * Common execution flow for Apply and Validate operations
 */
public abstract class AbstractPipelineTraverseOperation implements Operation<ExecuteArgs, ExecuteResult> {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("PipelineRunner");

    private final RunnerId runnerId;

    private final ExecutionPlanner executionPlanner;

    private final EventPublisher eventPublisher;

    private final boolean throwExceptionIfCannotObtainLock;

    private final StageExecutor stageExecutor;

    private final OrphanExecutionContext orphanExecutionContext;

    protected final Runnable finalizer;

    public AbstractPipelineTraverseOperation(RunnerId runnerId,
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

    protected abstract boolean validateOnlyMode();

    @Override
    public ExecuteResult execute(ExecuteArgs args) {
        ExecuteResponseData result;
        try {
            result = this.execute(args.getPipeline());
        } catch (OperationException operationException) {
            throw operationException;
        } catch (Throwable throwable) {
            throw processAndGetFlamingockException(throwable);
        } finally {
            this.finalizer.run();
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
                .mapToLong(stage -> stage.getChanges().size())
                .sum();
        logger.info("Flamingock execution started [stages={} changes={}]", stageCount, changeCount);

        eventPublisher.publish(new PipelineStartedEvent());

        PipelineRun pipelineRun = PipelineRun.of(pipeline);
        pipelineRun.start();

        do {
            validateAndGetExecutableStages(pipeline);
            try (ExecutionPlan execution = executionPlanner.getNextExecution(pipelineRun)) {
                // Validate execution plan for manual intervention requirements
                // This centralized validation ensures both community and cloud paths are validated
                execution.validate();

                if (execution.isExecutionRequired()) {
                    if (validateOnlyMode()) {
                        throw new PendingChangesException();
                    }
                    execution.applyOnEach((executionId, lock, executableStage) ->
                            runStage(executionId, lock, executableStage, pipelineRun));
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
                // Defensive: runStage normally records the failure into pipelineRun before
                // rethrowing. If for some reason this exception arrives here without that
                // having happened (e.g. thrown by something other than runStage), make sure
                // the failure is reflected in the response.
                String stageName = e.getResult() != null ? e.getResult().getStageName() : null;
                if (stageName != null) {
                    io.flamingock.internal.core.pipeline.run.StageRun stageRun = pipelineRun.getStageRun(stageName);
                    if (stageRun != null && !stageRun.getState().isFailed()) {
                        pipelineRun.markStageFailed(stageName, e);
                    }
                }
                pipelineRun.stop();
                throw OperationException.fromExisting(e.getCause(), pipelineRun.toResponse());
            }
        } while (true);

        pipelineRun.stop();
        ExecuteResponseData result = pipelineRun.toResponse();

        logger.info("Flamingock execution completed [duration={}ms applied={} skipped={}]",
                result.getTotalDurationMs(), result.getAppliedChanges(), result.getSkippedChanges());

        eventPublisher.publish(new PipelineCompletedEvent());

        return result;
    }

    private void runStage(String executionId, Lock lock, ExecutableStage executableStage, PipelineRun pipelineRun) {
        try {
            startStage(executionId, lock, executableStage, pipelineRun);
        } catch (StageExecutionException exception) {
            pipelineRun.markStageFailed(executableStage.getName(), exception);
            eventPublisher.publish(new StageFailedEvent(exception));
            eventPublisher.publish(new PipelineFailedEvent(exception));
            throw exception;
        } catch (Throwable generalException) {
            throw processAndGetFlamingockException(generalException);
        }
    }

    private void startStage(String executionId, Lock lock, ExecutableStage executableStage, PipelineRun pipelineRun) throws StageExecutionException {
        pipelineRun.markStageStarted(executableStage.getName());
        eventPublisher.publish(new StageStartedEvent());
        logger.debug("Applied state to process:\n{}", executableStage);

        ExecutionContext executionContext = new ExecutionContext(executionId, orphanExecutionContext.getHostname(), orphanExecutionContext.getMetadata());
        StageExecutor.Output executionOutput = stageExecutor.executeStage(executableStage, executionContext, lock);
        pipelineRun.markStageCompleted(executableStage.getName(), executionOutput.getResult());
        eventPublisher.publish(new StageCompletedEvent(executionOutput));
    }

    private FlamingockException processAndGetFlamingockException(Throwable exception) throws FlamingockException {
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
        if (flamingockException instanceof ManualInterventionRequiredException) {
            ManualInterventionRequiredException miException = (ManualInterventionRequiredException) flamingockException;
            logger.error("ABORTED OPERATION - Manual intervention required for changes: [{}]", miException.getConflictingSummary());
        } else {
            logger.debug("Error executing the process. ABORTED OPERATION", exception);
        }
        eventPublisher.publish(new StageFailedEvent(flamingockException));
        eventPublisher.publish(new PipelineFailedEvent(flamingockException));
        return flamingockException;
    }
}
