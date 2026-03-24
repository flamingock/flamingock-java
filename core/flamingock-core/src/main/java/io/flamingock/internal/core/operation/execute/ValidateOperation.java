/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.operation.execute;

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.error.PendingChangesException;
import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.event.model.impl.PipelineCompletedEvent;
import io.flamingock.internal.core.event.model.impl.PipelineFailedEvent;
import io.flamingock.internal.core.event.model.impl.PipelineStartedEvent;
import io.flamingock.internal.core.operation.Operation;
import io.flamingock.internal.core.operation.result.ExecutionResultBuilder;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.external.store.lock.LockException;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the pipeline without executing any changes.
 * If pending changes exist, throws {@link PendingChangesException}.
 */
public class ValidateOperation implements Operation<ExecuteArgs, ExecuteResult> {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("PipelineRunner");

    private final RunnerId runnerId;

    private final ExecutionPlanner executionPlanner;

    private final EventPublisher eventPublisher;

    private final boolean throwExceptionIfCannotObtainLock;

    private final Runnable finalizer;

    public ValidateOperation(RunnerId runnerId,
                             ExecutionPlanner executionPlanner,
                             EventPublisher eventPublisher,
                             boolean throwExceptionIfCannotObtainLock,
                             Runnable finalizer) {
        this.runnerId = runnerId;
        this.executionPlanner = executionPlanner;
        this.eventPublisher = eventPublisher;
        this.throwExceptionIfCannotObtainLock = throwExceptionIfCannotObtainLock;
        this.finalizer = finalizer;
    }

    @Override
    public ExecuteResult execute(ExecuteArgs args) {
        ExecuteResponseData result;
        try {
            result = this.validate(args.getPipeline());
        } catch (FlamingockException flamingockException) {
            throw flamingockException;
        } catch (Throwable throwable) {
            throw new FlamingockException(throwable);
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

    private ExecuteResponseData validate(LoadedPipeline pipeline) throws FlamingockException {
        List<AbstractLoadedStage> allStages = validateAndGetExecutableStages(pipeline);
        int stageCount = allStages.size();
        long changeCount = allStages.stream()
                .mapToLong(stage -> stage.getTasks().size())
                .sum();
        logger.info("Flamingock validation started [stages={} changes={}]", stageCount, changeCount);

        eventPublisher.publish(new PipelineStartedEvent());
        ExecutionResultBuilder resultBuilder = new ExecutionResultBuilder().startTimer();

        do {
            List<AbstractLoadedStage> stages = validateAndGetExecutableStages(pipeline);
            try (ExecutionPlan execution = executionPlanner.getNextExecution(stages)) {
                execution.validate();

                if (execution.isExecutionRequired()) {
                    int pendingCount = countPendingTasks(execution);
                    throw new PendingChangesException(pendingCount);
                } else {
                    break;
                }
            } catch (LockException exception) {
                eventPublisher.publish(new PipelineFailedEvent(exception));
                if (throwExceptionIfCannotObtainLock) {
                    logger.debug("Required process lock not acquired - ABORTING VALIDATION", exception);
                    throw exception;
                } else {
                    logger.warn("Process lock not acquired but throwExceptionIfCannotObtainLock=false - CONTINUING WITHOUT LOCK", exception);
                }
                break;
            }
        } while (true);

        resultBuilder.stopTimer().noChanges();
        ExecuteResponseData result = resultBuilder.build();

        logger.info("Flamingock validation completed — no pending changes detected");
        eventPublisher.publish(new PipelineCompletedEvent());

        return result;
    }

    private static int countPendingTasks(ExecutionPlan execution) {
        int count = 0;
        for (ExecutableStage stage : execution.getPipeline().getExecutableStages()) {
            for (ExecutableTask task : stage.getTasks()) {
                if (!task.isAlreadyApplied()) {
                    count++;
                }
            }
        }
        return count;
    }
}
