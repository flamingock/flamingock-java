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

package io.flamingock.internal.core.runner;

import io.flamingock.commons.utils.id.RunnerId;
import io.flamingock.core.error.FlamingockException;
import io.flamingock.internal.core.engine.execution.ExecutionPlan;
import io.flamingock.internal.core.engine.execution.ExecutionPlanner;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.engine.lock.LockException;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.event.model.impl.PipelineCompletedEvent;
import io.flamingock.internal.core.event.model.impl.PipelineFailedEvent;
import io.flamingock.internal.core.event.model.impl.PipelineStartedEvent;
import io.flamingock.internal.core.event.model.impl.StageCompletedEvent;
import io.flamingock.internal.core.event.model.impl.StageFailedEvent;
import io.flamingock.internal.core.event.model.impl.StageStartedEvent;
import io.flamingock.internal.core.pipeline.ExecutableStage;
import io.flamingock.internal.core.pipeline.LoadedStage;
import io.flamingock.internal.core.pipeline.Pipeline;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutionException;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.execution.StageSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.flamingock.commons.utils.ObjectUtils.requireNonNull;

public class PipelineRunner implements Runner {

    private static final Logger logger = LoggerFactory.getLogger(PipelineRunner.class);

    private final RunnerId runnerId;

    private final Pipeline pipeline;

    private final ExecutionPlanner executionPlanner;

    private final EventPublisher eventPublisher;

    private final boolean throwExceptionIfCannotObtainLock;

    private final StageExecutor stageExecutor;

    private final OrphanExecutionContext orphanExecutionContext;

    private final Runnable finalizer;

    public PipelineRunner(RunnerId runnerId,
                          Pipeline pipeline,
                          ExecutionPlanner executionPlanner,
                          StageExecutor stageExecutor,
                          OrphanExecutionContext orphanExecutionContext,
                          EventPublisher eventPublisher,
                          boolean throwExceptionIfCannotObtainLock,
                          Runnable finalizer) {
        this.runnerId = runnerId;
        this.pipeline = pipeline;
        this.executionPlanner = executionPlanner;
        this.stageExecutor = stageExecutor;
        this.orphanExecutionContext = orphanExecutionContext;
        this.eventPublisher = eventPublisher;
        this.throwExceptionIfCannotObtainLock = throwExceptionIfCannotObtainLock;
        this.finalizer = finalizer;
    }


    private void run(Pipeline pipeline) throws FlamingockException {

        eventPublisher.publish(new PipelineStartedEvent());
        PipelineSummary pipelineSummary = null;
        do {
            List<LoadedStage> loadedStages = pipeline.validateAndGetLoadedStages();
            try (ExecutionPlan execution = executionPlanner.getNextExecution(loadedStages)) {
                if (pipelineSummary == null) {
                    pipelineSummary = new PipelineSummary(execution.getPipeline());
                }
                final PipelineSummary pipelineSummaryTemp = pipelineSummary;
                if (execution.isExecutionRequired()) {
                    execution.applyOnEach((executionId, lock, executableStage) -> {
                        StageSummary stageSummary = runStage(executionId, lock, executableStage);
                        pipelineSummaryTemp.merge(stageSummary);
                    });
                } else {
                    break;
                }
            } catch (LockException exception) {

                eventPublisher.publish(new StageFailedEvent(exception));
                eventPublisher.publish(new PipelineFailedEvent(exception));
                if (throwExceptionIfCannotObtainLock) {
                    logger.error("Required process lock not acquired. ABORTED OPERATION", exception);
                    throw exception;

                } else {
                    logger.warn("Process lock not acquired and `throwExceptionIfCannotObtainLock == false`.\n" + "If the application should abort, make `throwExceptionIfCannotObtainLock == true`\n" + "CONTINUING THE APPLICATION WITHOUT FINISHING THE PROCESS", exception);
                }
                break;
            } catch (StageExecutionException e) {
                //if it's a StageExecutionException, we can safely assume the stage started its
                //execution, therefor the pipelinesSummary is initialised
                requireNonNull(pipelineSummary).merge(e.getSummary());
                throw new PipelineExecutionException(pipelineSummary);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable throwable) {
                throw processAndGetFlamingockException(throwable);
            }
        } while (true);

        String summary = pipelineSummary != null ? pipelineSummary.getPretty() : "";
        logger.info("Finished Flamingock process successfully\n{}", summary);

        eventPublisher.publish(new PipelineCompletedEvent());
    }

    private StageSummary runStage(String executionId, Lock lock, ExecutableStage executableStage) {
        try {
            return startStage(executionId, lock, executableStage);
        } catch (StageExecutionException exception) {
            eventPublisher.publish(new StageFailedEvent(exception));
            eventPublisher.publish(new PipelineFailedEvent(exception));
            throw exception;
        } catch (Throwable generalException) {
            throw processAndGetFlamingockException(generalException);
        }
    }

    private StageSummary startStage(String executionId, Lock lock, ExecutableStage executableStage) throws StageExecutionException {
        eventPublisher.publish(new StageStartedEvent());
        logger.debug("Applied state to process:\n{}", executableStage);

        ExecutionContext executionContext = new ExecutionContext(executionId, orphanExecutionContext.getHostname(), orphanExecutionContext.getAuthor(), orphanExecutionContext.getMetadata());
        StageExecutor.Output executionOutput = stageExecutor.executeStage(executableStage, executionContext, lock);
        eventPublisher.publish(new StageCompletedEvent(executionOutput));
        return executionOutput.getSummary();
    }

    private FlamingockException processAndGetFlamingockException(Throwable generalException) throws FlamingockException {
        FlamingockException exception = generalException instanceof FlamingockException ? (FlamingockException) generalException : new FlamingockException(generalException);
        logger.error("Error executing the process. ABORTED OPERATION", exception);
        eventPublisher.publish(new StageFailedEvent(exception));
        eventPublisher.publish(new PipelineFailedEvent(exception));
        return exception;
    }

    @Override
    public void run() {
        try {
            this.run(pipeline);
        } finally {
            finalizer.run();
        }
    }
}
