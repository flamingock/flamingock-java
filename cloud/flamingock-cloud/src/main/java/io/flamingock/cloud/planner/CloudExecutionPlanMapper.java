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
import io.flamingock.cloud.api.request.StageBlockRequest;
import io.flamingock.cloud.api.request.StageRequest;
import io.flamingock.cloud.api.request.ChangeRequest;
import io.flamingock.cloud.api.response.StageResponse;
import io.flamingock.cloud.api.response.ChangeResponse;
import io.flamingock.cloud.api.vo.CloudChangeAction;
import io.flamingock.cloud.api.vo.CloudChangeStatus;
import io.flamingock.cloud.api.vo.CloudStageStatus;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;
import io.flamingock.cloud.CloudApiMapper;
import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.core.pipeline.run.PipelineRun;
import io.flamingock.internal.core.pipeline.run.StageRun;
import io.flamingock.internal.core.pipeline.run.StageRunBlock;
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

        // Walk the PipelineRun's block list verbatim — block grouping is owned by PipelineRun,
        // not derived from StageType. Two blocks of the same StageType are preserved as two
        // separate StageBlockRequests in input order.
        List<StageRunBlock> blocks = pipelineRun.getStageBlocks();
        List<StageBlockRequest> requestBlocks = new ArrayList<>(blocks.size());
        // Stage order index is global across the request — preserves the same ordering used
        // before block-awareness (each stage's index in the flat run list).
        int stageOrder = 0;
        for (StageRunBlock block : blocks) {
            List<StageRequest> blockStages = new ArrayList<>(block.getStageRuns().size());
            for (StageRun stageRun : block.getStageRuns()) {
                AbstractLoadedStage currentStage = stageRun.getLoadedStage();
                // Per-change current status from the operation's recorded ChangeResult records.
                // This is what the server uses as informational input to apply its
                // "respect the client's report" rule (e.g. don't re-offer a FAILED change for
                // retry, don't downgrade a client-reported APPLIED to ALREADY_APPLIED).
                Map<String, ChangeStatus> currentStatusByChangeId = currentStatusMap(stageRun);
                List<ChangeRequest> stageChanges = currentStage
                        .getChanges()
                        .stream()
                        .map(descriptor -> CloudExecutionPlanMapper.mapToChangeRequest(
                                descriptor, ongoingStatusesMap, currentStatusByChangeId))
                        .collect(Collectors.toList());
                CloudStageStatus status = CloudApiMapper.toCloud(stageRun.getState());
                blockStages.add(new StageRequest(currentStage.getName(), stageOrder++, status, stageChanges));
            }
            requestBlocks.add(new StageBlockRequest(block.getType(), blockStages));
        }

        return new ExecutionPlanRequest(lockAcquiredForMillis, requestBlocks);
    }

    private static Map<String, ChangeStatus> currentStatusMap(StageRun stageRun) {
        List<ChangeResult> changes = stageRun.getResult().getChanges();
        if (changes == null || changes.isEmpty()) return Collections.emptyMap();
        Map<String, ChangeStatus> result = new HashMap<>(changes.size());
        for (ChangeResult cr : changes) {
            if (cr == null || cr.getChangeId() == null) continue;
            result.put(cr.getChangeId(), cr.getStatus());
        }
        return result;
    }

    private static ChangeRequest mapToChangeRequest(AbstractLoadedChange descriptor,
                                                    Map<String, TargetSystemAuditMarkType> ongoingStatusesMap,
                                                    Map<String, ChangeStatus> currentStatusByChangeId) {
        TargetSystemAuditMarkType domainStatus = ongoingStatusesMap.get(descriptor.getId());
        CloudTargetSystemAuditMarkType cloudStatus = domainStatus != null
                ? CloudApiMapper.toCloud(domainStatus)
                : CloudTargetSystemAuditMarkType.NONE;
        CloudChangeStatus currentStatus = CloudApiMapper.toCloud(
                currentStatusByChangeId.get(descriptor.getId()));
        return new ChangeRequest(descriptor.getId(), cloudStatus, currentStatus, descriptor.isTransactional());
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
