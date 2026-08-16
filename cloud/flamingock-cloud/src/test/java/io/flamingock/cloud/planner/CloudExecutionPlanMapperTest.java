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
package io.flamingock.cloud.planner;

import io.flamingock.api.StageType;
import io.flamingock.cloud.api.response.ChangeResponse;
import io.flamingock.cloud.api.response.ExecutionPlanResponse;
import io.flamingock.cloud.api.response.StageResponse;
import io.flamingock.cloud.api.vo.CloudChangeAction;
import io.flamingock.cloud.api.vo.CloudExecutionAction;
import io.flamingock.internal.common.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.LoadedChangeBuilder;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.loaded.stage.DefaultLoadedStage;
import io.flamingock.core.cloud.changes._001__CloudChange1;
import io.flamingock.core.cloud.changes._002__CloudChange2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.flamingock.cloud.api.request.ExecutionPlanRequest;
import io.flamingock.cloud.api.request.ChangeRequest;
import io.flamingock.cloud.api.request.StageRequest;
import io.flamingock.cloud.api.vo.CloudChangeStatus;
import io.flamingock.cloud.api.vo.CloudStageStatus;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;
import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.pipeline.run.PipelineRun;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CloudExecutionPlanMapperTest {

    private static AbstractLoadedChange change1;
    private static AbstractLoadedChange change2;

    @BeforeAll
    static void setup() {
        change1 = LoadedChangeBuilder.getCodeBuilderInstance(_001__CloudChange1.class).build();
        change2 = LoadedChangeBuilder.getCodeBuilderInstance(_002__CloudChange2.class).build();
    }

    @Test
    @DisplayName("Should map APPLY action from cloud response to internal APPLY")
    void shouldMapApplyAction() {
        List<AbstractLoadedStage> loadedStages = Arrays.asList(buildStage("stage-1", change1));
        ExecutionPlanResponse response = buildResponse(
                buildStageResponse("stage-1", 0, changeResponse(change1.getId(), CloudChangeAction.APPLY))
        );

        List<ExecutableStage> result = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);

        assertEquals(1, result.size());
        ExecutableChange execChange = result.get(0).getChanges().get(0);
        assertEquals(ChangeAction.APPLY, execChange.getAction());
    }

    @Test
    @DisplayName("Should map SKIP action from cloud response to internal SKIP")
    void shouldMapSkipAction() {
        List<AbstractLoadedStage> loadedStages = Arrays.asList(buildStage("stage-1", change1));
        ExecutionPlanResponse response = buildResponse(
                buildStageResponse("stage-1", 0, changeResponse(change1.getId(), CloudChangeAction.SKIP))
        );

        List<ExecutableStage> result = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);

        assertEquals(1, result.size());
        ExecutableChange execChange = result.get(0).getChanges().get(0);
        assertEquals(ChangeAction.SKIP, execChange.getAction());
    }

    @Test
    @DisplayName("Should map MANUAL_INTERVENTION action from cloud response to internal MANUAL_INTERVENTION")
    void shouldMapManualInterventionAction() {
        List<AbstractLoadedStage> loadedStages = Arrays.asList(buildStage("stage-1", change1));
        ExecutionPlanResponse response = buildResponse(
                buildStageResponse("stage-1", 0, changeResponse(change1.getId(), CloudChangeAction.MANUAL_INTERVENTION))
        );

        List<ExecutableStage> result = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);

        assertEquals(1, result.size());
        ExecutableChange execChange = result.get(0).getChanges().get(0);
        assertEquals(ChangeAction.MANUAL_INTERVENTION, execChange.getAction());
    }

    @Test
    @DisplayName("Should default to SKIP when change is not present in the cloud response")
    void shouldDefaultToSkipWhenChangeNotInResponse() {
        List<AbstractLoadedStage> loadedStages = Arrays.asList(buildStage("stage-1", change1, change2));
        ExecutionPlanResponse response = buildResponse(
                buildStageResponse("stage-1", 0, changeResponse(change1.getId(), CloudChangeAction.APPLY))
        );

        List<ExecutableStage> result = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);

        Map<String, ChangeAction> actions = result.get(0).getChanges().stream()
                .collect(Collectors.toMap(ExecutableChange::getId, ExecutableChange::getAction));
        assertEquals(ChangeAction.APPLY, actions.get(change1.getId()));
        assertEquals(ChangeAction.SKIP, actions.get(change2.getId()));
    }

    @Test
    @DisplayName("Should correctly map mixed actions in a single stage")
    void shouldMapMixedActions() {
        List<AbstractLoadedStage> loadedStages = Arrays.asList(buildStage("stage-1", change1, change2));
        ExecutionPlanResponse response = buildResponse(
                buildStageResponse("stage-1", 0,
                        changeResponse(change1.getId(), CloudChangeAction.APPLY),
                        changeResponse(change2.getId(), CloudChangeAction.MANUAL_INTERVENTION))
        );

        List<ExecutableStage> result = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);

        Map<String, ChangeAction> actions = result.get(0).getChanges().stream()
                .collect(Collectors.toMap(ExecutableChange::getId, ExecutableChange::getAction));
        assertEquals(ChangeAction.APPLY, actions.get(change1.getId()));
        assertEquals(ChangeAction.MANUAL_INTERVENTION, actions.get(change2.getId()));
    }

    @Test
    @DisplayName("Should only include stages that are present in the cloud response")
    void shouldFilterStagesNotInResponse() {
        List<AbstractLoadedStage> loadedStages = Arrays.asList(
                buildStage("stage-1", change1),
                buildStage("stage-2", change2)
        );
        ExecutionPlanResponse response = buildResponse(
                buildStageResponse("stage-1", 0, changeResponse(change1.getId(), CloudChangeAction.APPLY))
        );

        List<ExecutableStage> result = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);

        assertEquals(1, result.size());
        assertEquals("stage-1", result.get(0).getName());
    }

    @Test
    @DisplayName("Should throw ManualInterventionRequiredException when ExecutableStage.validate() is called with a MANUAL_INTERVENTION change")
    void shouldThrowManualInterventionOnStageValidate() {
        List<AbstractLoadedStage> loadedStages = Arrays.asList(buildStage("stage-1", change1));
        ExecutionPlanResponse response = buildResponse(
                buildStageResponse("stage-1", 0, changeResponse(change1.getId(), CloudChangeAction.MANUAL_INTERVENTION))
        );

        List<ExecutableStage> stages = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);

        // MI is now a per-stage concern: ExecutableStage.validate() throws so the operation lambda
        // can demote the stage to BlockedForMI without affecting the rest of the pipeline.
        assertThrows(ManualInterventionRequiredException.class, stages.get(0)::validate);
    }

    @Test
    @DisplayName("Should build stages from ABORT response preserving MANUAL_INTERVENTION actions")
    void shouldBuildAbortPlanFromAbortResponse() {
        List<AbstractLoadedStage> loadedStages = Arrays.asList(buildStage("stage-1", change1, change2));
        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.ABORT, 1L, null,
                Arrays.asList(buildStageResponse("stage-1", 0,
                        changeResponse(change1.getId(), CloudChangeAction.MANUAL_INTERVENTION),
                        changeResponse(change2.getId(), CloudChangeAction.APPLY)))
        );

        List<ExecutableStage> result = CloudExecutionPlanMapper.getExecutableStages(response, loadedStages);

        // CloudExecutionPlanMapper.getExecutableStages preserves change actions on the mapped
        // ExecutableStage regardless of the response action; verify that mapping directly.
        // ExecutionPlan.ABORT() no longer carries stages — that's a planner-control-flow concern.
        Map<String, ChangeAction> actions = result.get(0).getChanges().stream()
                .collect(Collectors.toMap(ExecutableChange::getId, ExecutableChange::getAction));
        assertEquals(ChangeAction.MANUAL_INTERVENTION, actions.get(change1.getId()));
        assertEquals(ChangeAction.APPLY, actions.get(change2.getId()));
    }

    @Test
    @DisplayName("Should map ongoing status from audit marks to ChangeRequests in toRequest()")
    void shouldMapOngoingStatusFromAuditMarksToChangeRequests() {
        List<AbstractLoadedStage> loadedStages = Arrays.asList(buildStage("stage-1", change1, change2));

        HashMap<String, TargetSystemAuditMarkType> ongoingStatusesMap = new HashMap<>();
        ongoingStatusesMap.put(change1.getId(), TargetSystemAuditMarkType.APPLIED);

        ExecutionPlanRequest request = CloudExecutionPlanMapper.toRequest(
                PipelineRun.of(loadedStages), 60000L, ongoingStatusesMap);

        Map<String, CloudTargetSystemAuditMarkType> marksByChangeId = request.getClientSubmission().getBlocks().get(0).getStages().get(0).getChanges().stream()
                .collect(Collectors.toMap(ChangeRequest::getId, ChangeRequest::getOngoingStatus));

        assertEquals(CloudTargetSystemAuditMarkType.APPLIED, marksByChangeId.get(change1.getId()));
        assertEquals(CloudTargetSystemAuditMarkType.NONE, marksByChangeId.get(change2.getId()));
    }

    @Test
    @DisplayName("toRequest() populates ChangeRequest.currentStatus from the operation's recorded ChangeResult statuses")
    void shouldMapCurrentStatusFromPipelineRun() {
        // Two changes in one stage; the operation has applied change1 and left change2
        // at NOT_REACHED (default). The mapper must reflect both on the wire.
        AbstractLoadedStage stage = buildStage("stage-1", change1, change2);
        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(stage));

        // Mark stage-1 completed with change1 = APPLIED. markStageCompleted merges by
        // change ID — change1 gets upgraded, change2 stays at the constructor default NOT_REACHED.
        ChangeResult change1Applied = ChangeResult.builder()
                .changeId(change1.getId())
                .status(ChangeStatus.APPLIED)
                .build();
        pipelineRun.markStageCompleted(
                "stage-1",
                StageResult.builder()
                        .stageId("stage-1")
                        .stageName("stage-1")
                        .state(StageState.COMPLETED)
                        .changes(Arrays.asList(change1Applied))
                        .build());

        ExecutionPlanRequest request = CloudExecutionPlanMapper.toRequest(
                pipelineRun, 60000L, Collections.emptyMap());

        // currentStatus is omitted on the wire (null) for NOT_REACHED — Collectors.toMap rejects
        // null values, so use a plain loop.
        Map<String, CloudChangeStatus> currentById = new HashMap<>();
        request.getClientSubmission().getBlocks().get(0).getStages().get(0).getChanges()
                .forEach(c -> currentById.put(c.getId(), c.getCurrentStatus()));

        assertEquals(CloudChangeStatus.APPLIED, currentById.get(change1.getId()),
                "Operation-applied change must surface as APPLIED in the request");
        assertNull(currentById.get(change2.getId()),
                "Untouched change (NOT_REACHED) must be absent on the wire (null)");
    }

    @Test
    @DisplayName("Should map per-stage status from PipelineRun into StageRequest.status")
    void shouldMapPerStageStatusFromPipelineRun() {
        // change2 lives ONLY in stage-blocked so the MI exception resolves to that stage
        // (markStagesBlockedFromMI maps recovery issues to the first stage whose changes match).
        AbstractLoadedStage notStartedStage = buildStage("stage-not-started", change1);
        AbstractLoadedStage completedStage = buildStage("stage-completed", change1);
        AbstractLoadedStage failedStage = buildStage("stage-failed", change1);
        AbstractLoadedStage blockedStage = buildStage("stage-blocked", change2);

        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(
                notStartedStage, completedStage, failedStage, blockedStage));

        // Mark states explicitly.
        pipelineRun.markStageCompleted("stage-completed",
                StageResult.builder().stageId("stage-completed").stageName("stage-completed")
                        .state(StageState.COMPLETED).build());
        pipelineRun.markStageFailed("stage-failed", new RuntimeException("boom"));
        pipelineRun.markStageBlockedFromMI(
                "stage-blocked",
                Collections.singletonList(new RecoveryIssue(change2.getId())));

        ExecutionPlanRequest request = CloudExecutionPlanMapper.toRequest(
                pipelineRun, 60000L, Collections.emptyMap());

        // Plain loop because Collectors.toMap rejects null values; NOT_STARTED maps to null.
        // All four stages are typed DEFAULT (via the buildStage helper), so they end up in a
        // single block; flatten the block list to iterate them.
        Map<String, CloudStageStatus> statusByName = new HashMap<>();
        request.getClientSubmission().getBlocks().forEach(block ->
                block.getStages().forEach(stage ->
                        statusByName.put(stage.getName(), stage.getStatus())));

        // NOT_STARTED is encoded as null on the wire (back-compat: missing field == not started).
        assertNull(statusByName.get("stage-not-started"));
        assertEquals(CloudStageStatus.COMPLETED, statusByName.get("stage-completed"));
        assertEquals(CloudStageStatus.FAILED, statusByName.get("stage-failed"));
        assertEquals(CloudStageStatus.BLOCKED_MANUAL_INTERVENTION, statusByName.get("stage-blocked"));
    }

    @Test
    @DisplayName("toRequest() emits one block per StageRunBlock in dependency order with the right type and stage contents")
    void toRequestEmitsOneBlockPerStageRunBlock() {
        // Three stages of three different types — PipelineRun.of(...) groups them into
        // SYSTEM -> LEGACY -> DEFAULT blocks (one stage each).
        AbstractLoadedStage systemStage = new DefaultLoadedStage("system-stage", StageType.SYSTEM,
                Collections.singletonList(change1));
        AbstractLoadedStage legacyStage = new DefaultLoadedStage("legacy-stage", StageType.LEGACY,
                Collections.singletonList(change1));
        AbstractLoadedStage userStage = new DefaultLoadedStage("user-stage", StageType.DEFAULT,
                Collections.singletonList(change1));

        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(userStage, systemStage, legacyStage));

        ExecutionPlanRequest request = CloudExecutionPlanMapper.toRequest(
                pipelineRun, 60000L, Collections.emptyMap());

        // Block order is dependency order, not input order: SYSTEM -> LEGACY -> DEFAULT.
        List<io.flamingock.cloud.api.request.StageBlockRequest> blocks =
                request.getClientSubmission().getBlocks();
        assertEquals(3, blocks.size());
        assertEquals(StageType.SYSTEM, blocks.get(0).getType());
        assertEquals(1, blocks.get(0).getStages().size());
        assertEquals("system-stage", blocks.get(0).getStages().get(0).getName());
        assertEquals(StageType.LEGACY, blocks.get(1).getType());
        assertEquals("legacy-stage", blocks.get(1).getStages().get(0).getName());
        assertEquals(StageType.DEFAULT, blocks.get(2).getType());
        assertEquals("user-stage", blocks.get(2).getStages().get(0).getName());
    }

    @Test
    @DisplayName("toRequest() preserves the global stage order index across blocks")
    void toRequestPreservesGlobalStageOrderAcrossBlocks() {
        // Two stages in the SYSTEM block, then one in DEFAULT — verify the per-stage `order`
        // field increments globally across the flattened block sequence.
        AbstractLoadedStage system1 = new DefaultLoadedStage("system-1", StageType.SYSTEM,
                Collections.singletonList(change1));
        AbstractLoadedStage system2 = new DefaultLoadedStage("system-2", StageType.SYSTEM,
                Collections.singletonList(change2));
        AbstractLoadedStage user1 = new DefaultLoadedStage("user-1", StageType.DEFAULT,
                Collections.singletonList(change1));

        PipelineRun pipelineRun = PipelineRun.of(Arrays.asList(system1, system2, user1));

        ExecutionPlanRequest request = CloudExecutionPlanMapper.toRequest(
                pipelineRun, 60000L, Collections.emptyMap());

        List<io.flamingock.cloud.api.request.StageBlockRequest> blocks =
                request.getClientSubmission().getBlocks();
        assertEquals(2, blocks.size());
        assertEquals(StageType.SYSTEM, blocks.get(0).getType());
        assertEquals(0, blocks.get(0).getStages().get(0).getOrder());
        assertEquals(1, blocks.get(0).getStages().get(1).getOrder());
        assertEquals(StageType.DEFAULT, blocks.get(1).getType());
        assertEquals(2, blocks.get(1).getStages().get(0).getOrder());
    }

    @Test
    @DisplayName("toRequest() produces an empty blocks list when the PipelineRun has no stages")
    void toRequestEmptyPipelineRunYieldsEmptyBlocks() {
        PipelineRun pipelineRun = PipelineRun.of(Collections.<AbstractLoadedStage>emptyList());

        ExecutionPlanRequest request = CloudExecutionPlanMapper.toRequest(
                pipelineRun, 60000L, Collections.emptyMap());

        assertNotNull(request.getClientSubmission().getBlocks());
        assertTrue(request.getClientSubmission().getBlocks().isEmpty());
    }

    private static DefaultLoadedStage buildStage(String name, AbstractLoadedChange... changes) {
        return new DefaultLoadedStage(name, StageType.DEFAULT, Arrays.asList(changes));
    }

    private static ExecutionPlanResponse buildResponse(StageResponse... stages) {
        return new ExecutionPlanResponse(CloudExecutionAction.EXECUTE, 1L, null, Arrays.asList(stages));
    }

    private static StageResponse buildStageResponse(String name, int order, ChangeResponse... changes) {
        return new StageResponse(name, order, Arrays.asList(changes));
    }

    private static ChangeResponse changeResponse(String id, CloudChangeAction action) {
        return new ChangeResponse(id, action);
    }
}
