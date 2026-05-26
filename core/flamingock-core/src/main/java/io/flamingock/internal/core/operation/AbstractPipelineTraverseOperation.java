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
import io.flamingock.internal.common.core.response.data.ExecutionStatus;
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
import io.flamingock.internal.core.pipeline.run.PipelineRun;
import io.flamingock.internal.core.pipeline.run.StageRun;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.core.external.store.lock.LockException;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;


/**
 * Common execution flow for Apply and Validate operations.
 *
 * <p>Stages are independent: a stage failure (Failed or BlockedForMI) does NOT abort the
 * pipeline. The do-while keeps asking the planner for the next stage; failed stages are
 * excluded from subsequent iterations by the planner. Only pipeline-wide errors
 * ({@link LockException}, {@link PendingChangesException}, or unexpected throwables from
 * outside a stage) break the loop and produce a {@link PipelineExecuteOperationException}.
 * When all stages have been visited and at least one failed, the operation throws
 * {@link StagedExecuteOperationException}. Otherwise it returns the response data.
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
        try {
            return this.execute(args.getPipeline());
        } finally {
            this.finalizer.run();
        }
    }

    private ExecuteResult execute(LoadedPipeline pipeline) {
        // Single transform point: PipelineRun.of(pipeline) validates static structure, partitions
        // stages by type into StageRunBlocks (SYSTEM -> LEGACY -> DEFAULT, sparse), and becomes the
        // single source of truth for the rest of this method. LoadedPipeline is not referenced again.
        PipelineRun pipelineRun = PipelineRun.of(pipeline);

        logger.info(
                "Flamingock execution started [stages={} changes={}]",
                pipelineRun.getStageCount(),
                pipelineRun.getTotalChangeCount());

        eventPublisher.publish(new PipelineStartedEvent());

        pipelineRun.start();

        Throwable pipelineLevelError = null;
        boolean throwPipelineLevelError = true;

        do {
            try (ExecutionPlan execution = executionPlanner.getNextExecution(pipelineRun)) {
                if (execution.isAborted()) {
                    logger.info("Pipeline execution aborted by the planner — earlier block has failures or another structural reason");
                    break;
                }

                if (!execution.isExecutionRequired()) {
                    break;
                }

                if (validateOnlyMode()) {
                    throw new PendingChangesException();
                }

                execution.applyOnEach((executionId, lock, executableStage) ->
                        runStage(executionId, lock, executableStage, pipelineRun));
            } catch (LockException exception) {
                pipelineLevelError = exception;
                if (throwExceptionIfCannotObtainLock) {
                    logger.debug("Required process lock not acquired - ABORTING OPERATION", exception);
                } else {
                    logger.warn("Process lock not acquired but throwExceptionIfCannotObtainLock=false - CONTINUING WITHOUT LOCK", exception);
                    throwPipelineLevelError = false;
                }
                break;
            } catch (Throwable exception) {
                pipelineLevelError = exception;
                break;
            }
        } while (true);

        pipelineRun.stop();
        ExecuteResponseData result = pipelineRun.toResponse();

        if (pipelineLevelError != null) {
            // toResponse() derives status from per-stage state; override to FAILED so the
            // response reflects the pipeline-wide error carried in pipelineLevelError.
            result.setStatus(ExecutionStatus.FAILED);
            logger.debug("Error executing the process. ABORTED OPERATION", pipelineLevelError);
            eventPublisher.publish(new PipelineFailedEvent(toException(pipelineLevelError), result));
            if (throwPipelineLevelError) {
                throw ExecuteOperationException.fromExisting(pipelineLevelError, result);
            }
            return new ExecuteResult(result);
        }

        if (hasAnyFailedStage(pipelineRun)) {
            StagedExecuteOperationException stagedException = new StagedExecuteOperationException(result);
            eventPublisher.publish(new PipelineFailedEvent(stagedException, result));
            throw stagedException;
        }

        eventPublisher.publish(new PipelineCompletedEvent(result));

        return new ExecuteResult(result);
    }

    private void runStage(String executionId, Lock lock, ExecutableStage executableStage, PipelineRun pipelineRun) {
        String stageName = executableStage.getName();
        // Mark the stage as reached the moment we enter — covers all three downstream paths
        // (MI-aborted, normal-completed, failed). The reporter uses this to distinguish stages
        // the executor actually opened from those the planner short-circuited past.
        pipelineRun.markStageReached(stageName);
        try {
            executableStage.validate();
        } catch (ManualInterventionRequiredException miException) {
            logger.warn("ABORTED STAGE '{}' - Manual intervention required for changes: [{}]",
                    stageName, miException.getConflictingSummary());
            pipelineRun.markStageStarted(stageName);
            eventPublisher.publish(new StageStartedEvent());
            pipelineRun.markStageBlockedFromMI(stageName, miException.getConflictingChanges());
            eventPublisher.publish(new StageFailedEvent(miException, pipelineRun.getStageRun(stageName).getResult()));
            return;
        }

        try {
            startStage(executionId, lock, executableStage, pipelineRun);
        } catch (StageExecutionException exception) {
            pipelineRun.markStageFailed(stageName, exception);
            eventPublisher.publish(new StageFailedEvent(exception, pipelineRun.getStageRun(stageName).getResult()));
        } catch (Throwable generalException) {
            pipelineRun.markStageFailed(stageName, generalException);
            eventPublisher.publish(new StageFailedEvent(toException(generalException), pipelineRun.getStageRun(stageName).getResult()));
        }
    }

    private void startStage(String executionId, Lock lock, ExecutableStage executableStage, PipelineRun pipelineRun) throws StageExecutionException {
        pipelineRun.markStageStarted(executableStage.getName());
        eventPublisher.publish(new StageStartedEvent());
        logger.debug("Applied state to process:\n{}", executableStage);

        ExecutionContext executionContext = new ExecutionContext(executionId, orphanExecutionContext.getHostname(), orphanExecutionContext.getMetadata());
        StageExecutor.Output executionOutput = stageExecutor.executeStage(executableStage, executionContext, lock);
        pipelineRun.markStageCompleted(executableStage.getName(), executionOutput.getResult());
        eventPublisher.publish(new StageCompletedEvent(executionOutput.getResult()));
    }

    private static boolean hasAnyFailedStage(PipelineRun pipelineRun) {
        for (StageRun stageRun : pipelineRun.getStageRuns()) {
            if (stageRun.getState().isFailed()) {
                return true;
            }
        }
        return false;
    }

    private static Exception toException(Throwable throwable) {
        return throwable instanceof Exception ? (Exception) throwable : new FlamingockException(throwable);
    }
}
