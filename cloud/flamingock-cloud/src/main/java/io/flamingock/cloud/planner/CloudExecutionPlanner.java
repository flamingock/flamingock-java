/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.cloud.planner;

import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.StopWatch;
import io.flamingock.internal.util.ThreadSleeper;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.cloud.planner.request.ExecutionPlanRequest;
import io.flamingock.internal.common.cloud.planner.response.ExecutionPlanResponse;
import io.flamingock.internal.common.cloud.vo.OngoingStatus;
import io.flamingock.internal.core.cloud.transaction.TaskWithOngoingStatus;
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.cloud.planner.client.ExecutionPlannerClient;
import io.flamingock.internal.core.cloud.transaction.OngoingStatusRepository;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.engine.execution.ExecutionPlan;
import io.flamingock.internal.core.engine.execution.ExecutionPlanner;
import io.flamingock.internal.core.engine.lock.LockException;
import io.flamingock.internal.core.pipeline.ExecutableStage;
import io.flamingock.internal.core.pipeline.LoadedStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CloudExecutionPlanner extends ExecutionPlanner {

    private static final Logger logger = LoggerFactory.getLogger(CloudExecutionPlanner.class);

    private final CoreConfigurable coreConfiguration;

    private final CloudLockService lockService;

    private final TimeService timeService;

    private final RunnerId runnerId;

    private final ExecutionPlannerClient client;

    private final OngoingStatusRepository ongoingStatusRepository;

    public CloudExecutionPlanner(RunnerId runnerId,
                                 ExecutionPlannerClient client,
                                 CoreConfigurable coreConfiguration,
                                 CloudLockService lockService,
                                 OngoingStatusRepository ongoingStatusRepository,
                                 TimeService timeService) {
        this.client = client;
        this.runnerId = runnerId;
        this.coreConfiguration = coreConfiguration;
        this.lockService = lockService;
        this.ongoingStatusRepository = ongoingStatusRepository;
        this.timeService = timeService;
    }

    @Override
    public ExecutionPlan getNextExecution(List<LoadedStage> loadedStages) throws LockException {


        //In every execution, as it start a stopwatch
        ThreadSleeper lockThreadSleeper = new ThreadSleeper(
                coreConfiguration.getLockQuitTryingAfterMillis(),
                LockException::new
        );
        String lastOwnerGuid = null;
        StopWatch counterPerGuid = StopWatch.getNoStarted();
        do {
            try {
                logger.info("Requesting cloud execution plan - elapsed[{}ms]", counterPerGuid.getElapsed());
                ExecutionPlanResponse response = createExecution(loadedStages, lastOwnerGuid, counterPerGuid.getElapsed());
                logger.info("Obtained cloud execution plan: {}", response.getAction());
                if (response.isContinue()) {
                    List<ExecutableStage> executableStages = ExecutionPlanMapper.getExecutableStages(response, loadedStages);
                    return ExecutionPlan.CONTINUE(executableStages);

                } else if (response.isExecute()) {
                    return buildNextExecutionPlan(loadedStages, response);

                } else if (response.isAwait()) {
                    if (lastOwnerGuid == null || !lastOwnerGuid.equals(response.getLock().getAcquisitionId())) {
                        //if the lock's guid has been changed, the stopwatch needs to be reset
                        logger.info(
                                "counter per lock GUID {}: lastOwnerGuid[{}] and response guid[{}] - elapsed[{}ms]",
                                lastOwnerGuid == null ? "started" : "reset",
                                lastOwnerGuid != null ? lastOwnerGuid : "not-initialised",
                                response.getLock().getAcquisitionId(),
                                counterPerGuid.getElapsed());
                        counterPerGuid.reset();
                    }
                    lastOwnerGuid = response.getLock().getAcquisitionId();
                    long remainingTimeForSameGuid = response.getLock().getAcquiredForMillis() - counterPerGuid.getElapsed();
                    logger.info("AWAIT response from server - acquired by other process for[{}ms] and elapsed[{}ms]",
                            response.getLock().getAcquiredForMillis(),
                            counterPerGuid.getElapsed()
                    );
                    lockThreadSleeper.checkThresholdAndWait(
                            Math.min(remainingTimeForSameGuid, coreConfiguration.getLockTryFrequencyMillis())
                    );

                } else {
                    throw new RuntimeException("Unrecognized action from response. Not within(CONTINUE, EXECUTE, AWAIT)");
                }

            } catch (FlamingockException ex) {
                logger.warn("Error after elapsed[{}ms]", counterPerGuid.getElapsed());
                throw ex;
            } catch (Throwable exception) {
                throw new FlamingockException(exception);
            }
        } while (true);
    }

    private ExecutionPlanResponse createExecution(List<LoadedStage> loadedStages, String lastAcquisitionId, long elapsedMillis) {

        Map<String, OngoingStatus> ongoingStatusesMap = getOngoingStatuses()
                .stream()
                .collect(Collectors.toMap(TaskWithOngoingStatus::getTaskId, TaskWithOngoingStatus::getOperation));

        ExecutionPlanRequest requestBody = ExecutionPlanMapper.toRequest(
                loadedStages,
                coreConfiguration.getLockAcquiredForMillis(),
                ongoingStatusesMap);

        ExecutionPlanResponse responsePlan = client.createExecution(requestBody, lastAcquisitionId, elapsedMillis);
        responsePlan.validate();
        return responsePlan;
    }

    private Collection<TaskWithOngoingStatus> getOngoingStatuses() {
        return ongoingStatusRepository != null ? ongoingStatusRepository.getOngoingStatuses() : Collections.emptySet();
    }

    private ExecutionPlan buildNextExecutionPlan(List<LoadedStage> loadedStages, ExecutionPlanResponse response) {
        return ExecutionPlan.newExecution(
                response.getExecutionId(),
                ExecutionPlanMapper.extractLockFromResponse(response, coreConfiguration, runnerId, lockService, timeService),
                ExecutionPlanMapper.getExecutableStages(response, loadedStages)
        );
    }

}
