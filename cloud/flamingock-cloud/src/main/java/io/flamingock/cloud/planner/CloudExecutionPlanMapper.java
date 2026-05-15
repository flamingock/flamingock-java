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
import io.flamingock.cloud.api.request.ExecutionPlanRequest;
import io.flamingock.cloud.api.response.ExecutionPlanResponse;
import io.flamingock.cloud.api.request.StageRequest;
import io.flamingock.cloud.api.request.ChangeRequest;
import io.flamingock.cloud.api.response.StageResponse;
import io.flamingock.cloud.api.response.ChangeResponse;
import io.flamingock.cloud.api.vo.CloudChangeAction;
import io.flamingock.cloud.api.vo.CloudStageStatus;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;
import io.flamingock.cloud.CloudApiMapper;
import io.flamingock.internal.core.pipeline.run.PipelineRun;
import io.flamingock.internal.core.pipeline.run.StageRun;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.common.core.recovery.action.ChangeActionMap;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.common.core.change.ChangeDescriptor;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
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

    public static ExecutionPlanRequest toRequest(PipelineRun pipelineRun,
                                                 long lockAcquiredForMillis,
                                                 Map<String, TargetSystemAuditMarkType> ongoingStatusesMap) {

        List<StageRun> stageRuns = pipelineRun.getStageRuns();
        List<StageRequest> requestStages = new ArrayList<>(stageRuns.size());
        for (int i = 0; i < stageRuns.size(); i++) {
            StageRun stageRun = stageRuns.get(i);
            AbstractLoadedStage currentStage = stageRun.getLoadedStage();
            List<ChangeRequest> stageChanges = currentStage
                    .getChanges()
                    .stream()
                    .map(descriptor -> CloudExecutionPlanMapper.mapToChangeRequest(descriptor, ongoingStatusesMap))
                    .collect(Collectors.toList());
            CloudStageStatus status = CloudApiMapper.toCloud(stageRun.getState());
            requestStages.add(new StageRequest(currentStage.getName(), i, status, stageChanges));
        }

        return new ExecutionPlanRequest(lockAcquiredForMillis, requestStages);
    }

    private static ChangeRequest mapToChangeRequest(AbstractLoadedChange descriptor,
                                                    Map<String, TargetSystemAuditMarkType> ongoingStatusesMap) {
        TargetSystemAuditMarkType domainStatus = ongoingStatusesMap.get(descriptor.getId());
        CloudTargetSystemAuditMarkType cloudStatus = domainStatus != null
                ? CloudApiMapper.toCloud(domainStatus)
                : CloudTargetSystemAuditMarkType.NONE;
        return new ChangeRequest(descriptor.getId(), cloudStatus, descriptor.isTransactional());
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
        Map<String, CloudChangeAction> changeStateMap = stageResponse.getChanges()
                .stream()
                .collect(Collectors.toMap(ChangeResponse::getId, ChangeResponse::getAction));

        // Build action map using anti-corruption layer
        ChangeActionMap actionPlan = getChangeActionMap(loadedStage, changeStateMap);
        return loadedStage.applyActions(actionPlan);
    }

    @NotNull
    private static ChangeActionMap getChangeActionMap(AbstractLoadedStage loadedStage, Map<String, CloudChangeAction> actionsMapByChangeId) {
        Map<String, ChangeAction> actionMap = new HashMap<>();

        for (ChangeDescriptor change : loadedStage.getChanges()) {
            String changeId = change.getId();
            CloudChangeAction cloudAction = actionsMapByChangeId.get(changeId);

            // If change not in response, assume it's already applied (cloud orchestrator decision)
            if (cloudAction == null) {
                actionMap.put(changeId, ChangeAction.SKIP);
            } else {
                // Use anti-corruption layer to map cloud domain to internal domain
                actionMap.put(changeId, mapCloudActionToChangeAction(cloudAction));
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

}
