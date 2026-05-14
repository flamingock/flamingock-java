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
package io.flamingock.cloud.planner;

import io.flamingock.cloud.lock.CloudLock;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.StopWatch;
import io.flamingock.internal.util.ThreadSleeper;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.cloud.api.request.ExecutionPlanRequest;
import io.flamingock.cloud.api.response.ExecutionPlanResponse;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.cloud.planner.client.ExecutionPlannerClient;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.external.store.lock.LockException;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.run.PipelineRun;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CloudExecutionPlanner extends ExecutionPlanner {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("CloudExecution");

    private final CoreConfigurable coreConfiguration;

    private final CloudLockService lockService;

    private final TimeService timeService;

    private final RunnerId runnerId;

    private final ExecutionPlannerClient client;

    private final List<TargetSystemAuditMarker> auditMarkers;

    public CloudExecutionPlanner(RunnerId runnerId,
                                 ExecutionPlannerClient client,
                                 CoreConfigurable coreConfiguration,
                                 CloudLockService lockService,
                                 List<TargetSystemAuditMarker> auditMarkers,
                                 TimeService timeService) {
        this.client = client;
        this.runnerId = runnerId;
        this.coreConfiguration = coreConfiguration;
        this.lockService = lockService;
        this.auditMarkers = auditMarkers;
        this.timeService = timeService;
    }

    @Override
    public ExecutionPlan getNextExecution(PipelineRun pipelineRun) throws LockException {
        List<AbstractLoadedStage> loadedStages = pipelineRun.getLoadedStages();

        AuditMarkSnapshot snapshot = buildAuditMarkSnapshot();

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
                ExecutionPlanResponse response = createExecution(pipelineRun, snapshot.getMarks(), lastOwnerGuid, counterPerGuid.getElapsed());
                logger.info("Obtained cloud execution plan: {}", response.getAction());

                //TODO should check if it has the lock?
                if (response.isSynchronizedMarks()) {
                    snapshot = clearSynchronizedMarks(snapshot);
                }

                if (response.isContinue()) {
                    List<ExecutableStage> executableStages = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);
                    return ExecutionPlan.CONTINUE(executableStages);

                } else if (response.isExecute()) {
                    Lock lock = CloudLock.initialiseLocal(response.getLock(), coreConfiguration, runnerId, lockService, timeService);
                    return buildNextExecutionPlan(loadedStages, response, lock);

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

                } else if (response.isAbort()) {
                    List<ExecutableStage> stages = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);
                    return ExecutionPlan.ABORT(stages);

                } else {
                    throw new RuntimeException("Unrecognized action from response. Not within(CONTINUE, EXECUTE, AWAIT, ABORT)");
                }

            } catch (FlamingockException ex) {
                logger.warn("Error after elapsed[{}ms]", counterPerGuid.getElapsed());
                throw ex;
            } catch (Throwable exception) {
                throw new FlamingockException(exception);
            }
        } while (true);
    }

    private ExecutionPlanResponse createExecution(PipelineRun pipelineRun,
                                                  Collection<TargetSystemAuditMark> auditMarks,
                                                  String lastAcquisitionId,
                                                  long elapsedMillis) {

        Map<String, TargetSystemAuditMarkType> auditMarksMap = auditMarks
                .stream()
                .collect(Collectors.toMap(TargetSystemAuditMark::getChangeId, TargetSystemAuditMark::getOperation));

        ExecutionPlanRequest requestBody = CloudExecutionPlanMapper.toRequest(
                pipelineRun,
                coreConfiguration.getLockAcquiredForMillis(),
                auditMarksMap);

        ExecutionPlanResponse responsePlan = client.createExecution(requestBody, lastAcquisitionId, elapsedMillis);
        responsePlan.validate();
        return responsePlan;
    }

    private AuditMarkSnapshot buildAuditMarkSnapshot() {
        if (auditMarkers == null || auditMarkers.isEmpty()) {
            return AuditMarkSnapshot.empty();
        }
        Set<TargetSystemAuditMark> allMarks = new HashSet<>();
        Map<String, TargetSystemAuditMarker> markerByChangeId = new HashMap<>();
        for (TargetSystemAuditMarker marker : auditMarkers) {
            for (TargetSystemAuditMark mark : marker.listAll()) {
                allMarks.add(mark);
                markerByChangeId.put(mark.getChangeId(), marker);
            }
        }
        return new AuditMarkSnapshot(allMarks, markerByChangeId);
    }

    private AuditMarkSnapshot clearSynchronizedMarks(AuditMarkSnapshot snapshot) {
        Map<String, TargetSystemAuditMarker> markerByChangeId = snapshot.getMarkerByChangeId();
        for (Map.Entry<String, TargetSystemAuditMarker> entry : markerByChangeId.entrySet()) {
            entry.getValue().clearMark(entry.getKey());
        }
        if (!markerByChangeId.isEmpty()) {
            logger.info("Cleared {} synchronized audit marks", markerByChangeId.size());
        }
        return AuditMarkSnapshot.empty();
    }

    private ExecutionPlan buildNextExecutionPlan(List<AbstractLoadedStage> loadedStages,
                                                 ExecutionPlanResponse response,
                                                 Lock lock) {
        return ExecutionPlan.newExecution(
                response.getExecutionId(),
                lock,
                CloudExecutionPlanMapper.getExecutableStages(response, loadedStages)
        );
    }

    static class AuditMarkSnapshot {
        private final Collection<TargetSystemAuditMark> marks;
        private final Map<String, TargetSystemAuditMarker> markerByChangeId;

        static AuditMarkSnapshot empty() {
            return new AuditMarkSnapshot(Collections.emptySet(), Collections.emptyMap());
        }

        AuditMarkSnapshot(Collection<TargetSystemAuditMark> marks, Map<String, TargetSystemAuditMarker> markerByChangeId) {
            this.marks = marks;
            this.markerByChangeId = markerByChangeId;
        }

        Collection<TargetSystemAuditMark> getMarks() {
            return marks;
        }

        Map<String, TargetSystemAuditMarker> getMarkerByChangeId() {
            return markerByChangeId;
        }
    }
}
