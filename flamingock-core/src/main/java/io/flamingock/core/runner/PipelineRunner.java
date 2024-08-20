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

package io.flamingock.core.runner;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.engine.execution.ExecutionPlan;
import io.flamingock.core.engine.execution.ExecutionPlanner;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.engine.lock.LockException;
import io.flamingock.core.event.EventPublisher;
import io.flamingock.core.event.model.impl.PipelineCompletedEvent;
import io.flamingock.core.event.model.impl.PipelineFailedEvent;
import io.flamingock.core.event.model.impl.PipelineStartedEvent;
import io.flamingock.core.event.model.impl.StageCompletedEvent;
import io.flamingock.core.event.model.impl.StageFailedEvent;
import io.flamingock.core.event.model.impl.StageStartedEvent;
import io.flamingock.core.pipeline.ExecutableStage;
import io.flamingock.core.pipeline.LoadedStage;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.core.pipeline.execution.StageExecutionException;
import io.flamingock.core.pipeline.execution.StageExecutor;
import io.flamingock.core.pipeline.execution.StageSummary;
import io.flamingock.core.pipeline.execution.TaskSummarizer;
import io.flamingock.core.task.navigation.summary.StepSummaryLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    public PipelineRunner(RunnerId runnerId, Pipeline pipeline, ExecutionPlanner executionPlanner, StageExecutor stageExecutor, OrphanExecutionContext orphanExecutionContext, EventPublisher eventPublisher, boolean throwExceptionIfCannotObtainLock, Runnable finalizer) {
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
            try (ExecutionPlan execution = executionPlanner.getNextExecution(pipeline)) {
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

//                List<LoadedStage> pipelineStages = pipeline.getLoadedStages();
//                StageSummary stageSummaryWithNotReachedTasks = getStageSummaryWithNotReachedTasks(pipelineStages, e.getSummary());
//                stageSummaryMap.put(e.getSummary().getId(), stageSummaryWithNotReachedTasks);
//                pipelineSummary.add(stageSummaryWithNotReachedTasks);

//                Set<String> processedStages = pipelineSummary.getLines()
//                        .stream()
//                        .map(StageSummary::getId)
//                        .collect(Collectors.toSet());
//
//                pipelineStages.stream()
//                        .filter(stage -> !processedStages.contains(stage.getName()))
//                        .forEach(stage -> {
//                            pipelineSummary.add(getStageSummaryWithNotReachedTasks(pipelineStages, new StageSummary(stage.getName())));
//                        });


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
