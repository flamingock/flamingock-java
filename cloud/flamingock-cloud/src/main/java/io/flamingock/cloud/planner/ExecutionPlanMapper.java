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
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.common.cloud.planner.request.ExecutionPlanRequest;
import io.flamingock.internal.common.cloud.planner.response.ExecutionPlanResponse;
import io.flamingock.internal.common.cloud.planner.request.StageRequest;
import io.flamingock.internal.common.cloud.planner.request.TaskRequest;
import io.flamingock.internal.common.cloud.planner.response.StageResponse;
import io.flamingock.internal.common.cloud.planner.response.TaskResponse;
import io.flamingock.internal.common.cloud.planner.response.RequiredActionTask;
import io.flamingock.internal.common.cloud.vo.OngoingStatus;
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.engine.audit.domain.AuditStageStatus;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.engine.lock.LockKey;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.common.core.task.TaskDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.flamingock.internal.common.core.audit.AuditEntry.Status.EXECUTED;

public final class ExecutionPlanMapper {

    public static ExecutionPlanRequest toRequest(List<AbstractLoadedStage> loadedStages,
                                                 long lockAcquiredForMillis,
                                                 Map<String, OngoingStatus> ongoingStatusesMap) {

        List<StageRequest> requestStages = new ArrayList<>(loadedStages.size());
        for (int i = 0; i < loadedStages.size(); i++) {
            AbstractLoadedStage currentStage = loadedStages.get(i);
            List<TaskRequest> stageTasks = currentStage
                    .getTasks()
                    .stream()
                    .map(descriptor -> ExecutionPlanMapper.mapToTaskRequest(descriptor, ongoingStatusesMap))
                    .collect(Collectors.toList());
            requestStages.add(new StageRequest(currentStage.getName(), i, stageTasks));
        }

        return new ExecutionPlanRequest(lockAcquiredForMillis, requestStages);
    }

    private static TaskRequest mapToTaskRequest(TaskDescriptor descriptor,
                                                Map<String, OngoingStatus> ongoingStatusesMap) {
        if (ongoingStatusesMap.containsKey(descriptor.getId())) {
            if (ongoingStatusesMap.get(descriptor.getId()) == OngoingStatus.ROLLBACK) {
                return TaskRequest.ongoingRollback(descriptor.getId(), descriptor.isTransactional());
            } else {
                return TaskRequest.ongoingExecution(descriptor.getId(), descriptor.isTransactional());
            }
        } else {
            return TaskRequest.task(descriptor.getId(), descriptor.isTransactional());
        }
    }

    static List<ExecutableStage> getExecutableStages(ExecutionPlanResponse response, List<AbstractLoadedStage> loadedStages) {
        //Create a set for the filter in the loop
        List<StageResponse> stages = response.getStages() != null ? response.getStages() : Collections.emptyList();
        Set<String> stageNameSet = stages.stream().map(StageResponse::getName).collect(Collectors.toSet());

        //Create a map to allow indexed access when looping
        Map<String, StageResponse> responseStagesMap = stages
                .stream()
                .collect(Collectors.toMap(StageResponse::getName, Function.identity()));

        return loadedStages.stream()
                .filter(loadedStage -> stageNameSet.contains(loadedStage.getName()))
                .map(loadedStage -> mapToExecutable(loadedStage, responseStagesMap.get(loadedStage.getName())))
                .collect(Collectors.toList());

    }

    private static ExecutableStage mapToExecutable(AbstractLoadedStage loadedStage, StageResponse stageResponse) {

        Map<String, RequiredActionTask> taskStateMap = stageResponse.getTasks()
                .stream()
                .collect(Collectors.toMap(TaskResponse::getId, TaskResponse::getState));

        AuditStageStatus.StatusBuilder builder = AuditStageStatus.statusBuilder();

        //We assume that if the taskId is not in the response is already successfully executed
        loadedStage
                .getTasks()
                .stream()
                .map(TaskDescriptor::getId)
                .filter(taskId -> taskStateMap.get(taskId) != RequiredActionTask.PENDING_EXECUTION)
                .forEach(taskId -> builder.addState(taskId, EXECUTED));

        return loadedStage.applyState(builder.build());
    }

    public static Lock extractLockFromResponse(ExecutionPlanResponse response,
                                               CoreConfigurable coreConfiguration,
                                               RunnerId owner,
                                               CloudLockService lockService,
                                               TimeService timeService) {
        return new Lock(
                owner,
                LockKey.fromString(response.getLock().getKey()),
                response.getLock().getAcquiredForMillis(),
                coreConfiguration.getLockQuitTryingAfterMillis(),
                coreConfiguration.getLockTryFrequencyMillis(),
                lockService,
                timeService
        );
    }
}
