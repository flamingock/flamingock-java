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

import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.common.cloud.planner.request.ExecutionPlanRequest;
import io.flamingock.internal.common.cloud.planner.response.ExecutionPlanResponse;
import io.flamingock.internal.common.cloud.planner.request.StageRequest;
import io.flamingock.internal.common.cloud.planner.request.TaskRequest;
import io.flamingock.internal.common.cloud.planner.response.StageResponse;
import io.flamingock.internal.common.cloud.planner.response.TaskResponse;
import io.flamingock.internal.common.cloud.planner.response.CloudChangeAction;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.common.core.recovery.action.ChangeActionMap;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.common.core.task.TaskDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public final class CloudExecutionPlanMapper {

    public static ExecutionPlanRequest toRequest(List<AbstractLoadedStage> loadedStages,
                                                 long lockAcquiredForMillis,
                                                 Map<String, TargetSystemAuditMarkType> ongoingStatusesMap) {

        List<StageRequest> requestStages = new ArrayList<>(loadedStages.size());
        for (int i = 0; i < loadedStages.size(); i++) {
            AbstractLoadedStage currentStage = loadedStages.get(i);
            List<TaskRequest> stageTasks = currentStage
                    .getTasks()
                    .stream()
                    .map(descriptor -> CloudExecutionPlanMapper.mapToTaskRequest(descriptor, ongoingStatusesMap))
                    .collect(Collectors.toList());
            requestStages.add(new StageRequest(currentStage.getName(), i, stageTasks));
        }

        return new ExecutionPlanRequest(lockAcquiredForMillis, requestStages);
    }

    private static TaskRequest mapToTaskRequest(TaskDescriptor descriptor,
                                                Map<String, TargetSystemAuditMarkType> ongoingStatusesMap) {
        if (ongoingStatusesMap.containsKey(descriptor.getId())) {
            if (ongoingStatusesMap.get(descriptor.getId()) == TargetSystemAuditMarkType.ROLLBACK) {
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
        Map<String, CloudChangeAction> taskStateMap = stageResponse.getTasks()
                .stream()
                .collect(Collectors.toMap(TaskResponse::getId, TaskResponse::getAction));

        // Build action map using anti-corruption layer
        ChangeActionMap actionPlan = getChangeActionMap(loadedStage, taskStateMap);
        return loadedStage.applyActions(actionPlan);
    }

    @NotNull
    private static ChangeActionMap getChangeActionMap(AbstractLoadedStage loadedStage, Map<String, CloudChangeAction> actionsMapByChangeId) {
        Map<String, ChangeAction> actionMap = new HashMap<>();

        for (TaskDescriptor task : loadedStage.getTasks()) {
            String taskId = task.getId();
            CloudChangeAction cloudAction = actionsMapByChangeId.get(taskId);

            // If task not in response, assume it's already applied (cloud orchestrator decision)
            if (cloudAction == null) {
                actionMap.put(taskId, ChangeAction.SKIP);
            } else {
                // Use anti-corruption layer to map cloud domain to internal domain
                actionMap.put(taskId, mapCloudActionToChangeAction(cloudAction));
            }
        }

        return new ChangeActionMap(actionMap);
    }

    /**
     * Anti-corruption layer: Maps cloud domain CloudChangeAction to internal ChangeAction.
     * Since both enums now have aligned values, we can use direct enum name mapping.
     * This preserves domain boundaries while enabling the new action-based architecture.
     */
    private static ChangeAction mapCloudActionToChangeAction(CloudChangeAction cloudAction) {
        // Direct mapping since enum values are now aligned
        return ChangeAction.valueOf(cloudAction.name());
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
