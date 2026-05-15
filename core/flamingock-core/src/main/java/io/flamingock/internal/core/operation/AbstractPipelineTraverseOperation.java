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
import io.flamingock.internal.core.pipeline.run.StageRun;
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

    private static List<AbstractLoadedStage> validateAndGetExecutableStages(LoadedPipeline pipeline) {
        pipeline.validate();
        List<AbstractLoadedStage> stages = new ArrayList<>();
        if (pipeline.getSystemStage().isPresent()) {
            stages.add(pipeline.getSystemStage().get());
        }
        stages.addAll(pipeline.getStages());
        return stages;
    }

    private ExecuteResult execute(LoadedPipeline pipeline) {
        List<AbstractLoadedStage> allStages = validateAndGetExecutableStages(pipeline);
        int stageCount = allStages.size();
        long changeCount = allStages.stream()
                .mapToLong(stage -> stage.getChanges().size())
                .sum();
        logger.info("Flamingock execution started [stages={} changes={}]", stageCount, changeCount);

        eventPublisher.publish(new PipelineStartedEvent());

        PipelineRun pipelineRun = PipelineRun.of(pipeline);
        pipelineRun.start();

        Throwable pipelineLevelError = null;
        boolean throwPipelineLevelError = true;

        do {
            validateAndGetExecutableStages(pipeline);
            try (ExecutionPlan execution = executionPlanner.getNextExecution(pipelineRun)) {
                execution.validate();

                if (!execution.isExecutionRequired()) {
                    break;
                }

                if (validateOnlyMode()) {
                    throw new PendingChangesException();
                }

                execution.applyOnEach((executionId, lock, executableStage) ->
                        runStage(executionId, lock, executableStage, pipelineRun));
            } catch (LockException exception) {
                pipelineRun.markPipelineFailed(exception);
                pipelineLevelError = exception;
                if (throwExceptionIfCannotObtainLock) {
                    logger.debug("Required process lock not acquired - ABORTING OPERATION", exception);
                } else {
                    logger.warn("Process lock not acquired but throwExceptionIfCannotObtainLock=false - CONTINUING WITHOUT LOCK", exception);
                    throwPipelineLevelError = false;
                }
                break;
            } catch (Throwable  exception) {
                pipelineRun.markPipelineFailed(exception);
                pipelineLevelError = exception;
                break;
            }//FlamingockException
        } while (true);

        pipelineRun.stop();
        ExecuteResponseData result = pipelineRun.toResponse();

        if (pipelineLevelError != null) {
            logger.debug("Error executing the process. ABORTED OPERATION", pipelineLevelError);
            eventPublisher.publish(new PipelineFailedEvent(toException(pipelineLevelError)));
            if (throwPipelineLevelError) {
                throw ExecuteOperationException.fromExisting(pipelineLevelError, result);
            }
            return new ExecuteResult(result);
        }

        if (hasAnyFailedStage(pipelineRun)) {
            logger.info("Flamingock execution finished with stage failures [duration={}ms applied={} skipped={} failed={}]",
                    result.getTotalDurationMs(), result.getAppliedChanges(), result.getSkippedChanges(), result.getFailedChanges());
            StagedExecuteOperationException stagedException = new StagedExecuteOperationException(result);
            eventPublisher.publish(new PipelineFailedEvent(stagedException));
            throw stagedException;
        }

        logger.info("Flamingock execution completed [duration={}ms applied={} skipped={}]",
                result.getTotalDurationMs(), result.getAppliedChanges(), result.getSkippedChanges());

        eventPublisher.publish(new PipelineCompletedEvent());

        return new ExecuteResult(result);
    }

    private void runStage(String executionId, Lock lock, ExecutableStage executableStage, PipelineRun pipelineRun) {
        String stageName = executableStage.getName();
        try {
            executableStage.validate();
        } catch (ManualInterventionRequiredException miException) {
            logger.warn("ABORTED STAGE '{}' - Manual intervention required for changes: [{}]",
                    stageName, miException.getConflictingSummary());
            pipelineRun.markStageStarted(stageName);
            eventPublisher.publish(new StageStartedEvent());
            pipelineRun.markStageBlockedFromMI(stageName, miException.getConflictingChanges());
            eventPublisher.publish(new StageFailedEvent(miException));
            return;
        }

        try {
            startStage(executionId, lock, executableStage, pipelineRun);
        } catch (StageExecutionException exception) {
            pipelineRun.markStageFailed(stageName, exception);
            eventPublisher.publish(new StageFailedEvent(exception));
        } catch (Throwable generalException) {
            pipelineRun.markStageFailed(stageName, generalException);
            eventPublisher.publish(new StageFailedEvent(toException(generalException)));
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
