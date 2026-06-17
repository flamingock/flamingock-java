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
import io.flamingock.cloud.api.request.ExecutionPlanRequest;
import io.flamingock.cloud.api.request.ChangeRequest;
import io.flamingock.cloud.api.response.ChangeResponse;
import io.flamingock.cloud.api.response.ChangeResultResponse;
import io.flamingock.cloud.api.response.ExecutionPlanResponse;
import io.flamingock.cloud.api.response.PipelineResultResponse;
import io.flamingock.cloud.api.response.StageResponse;
import io.flamingock.cloud.api.response.StageResultResponse;
import io.flamingock.cloud.api.vo.CloudChangeAction;
import io.flamingock.cloud.api.vo.CloudChangeStatus;
import io.flamingock.cloud.api.vo.CloudExecutionAction;
import io.flamingock.cloud.api.vo.CloudPlannerVerdict;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.response.data.PlannerVerdict;
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.cloud.planner.client.ExecutionPlannerClient;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.LoadedChangeBuilder;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.loaded.stage.DefaultLoadedStage;
import io.flamingock.internal.core.pipeline.run.PipelineRun;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.core.cloud.changes._001__CloudChange1;
import io.flamingock.core.cloud.changes._002__CloudChange2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudExecutionPlannerTest {

    private static AbstractLoadedChange change1;
    private static AbstractLoadedChange change2;

    private ExecutionPlannerClient client;
    private CoreConfigurable config;

    @BeforeAll
    static void setupChanges() {
        change1 = LoadedChangeBuilder.getCodeBuilderInstance(_001__CloudChange1.class).build();
        change2 = LoadedChangeBuilder.getCodeBuilderInstance(_002__CloudChange2.class).build();
    }

    @BeforeEach
    void setup() {
        client = mock(ExecutionPlannerClient.class);
        config = mock(CoreConfigurable.class);
        when(config.getLockAcquiredForMillis()).thenReturn(60000L);
        when(config.getLockQuitTryingAfterMillis()).thenReturn(30000L);
        when(config.getLockTryFrequencyMillis()).thenReturn(1000L);
    }

    private CloudExecutionPlanner buildPlanner(List<TargetSystemAuditMarker> auditMarkers) {
        return new CloudExecutionPlanner(
                RunnerId.fromString("test-runner"),
                client,
                config,
                mock(CloudLockService.class),
                auditMarkers,
                TimeService.getDefault()
        );
    }

    @Test
    @DisplayName("Should send the multi-block PipelineRun on the wire, preserving block order and per-block stages")
    void shouldSendMultiBlockPipelineRunOnTheWire() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        // Stub the server to return CONTINUE so the planner doesn't loop or try to acquire a lock.
        ExecutionPlanResponse continueResponse = new ExecutionPlanResponse();
        continueResponse.setAction(CloudExecutionAction.CONTINUE);
        continueResponse.setPipelineResult(pipelineResultUpToDate(
                "system-stage", "legacy-stage", "user-a", "user-b"));
        when(client.createExecution(any(), any(), anyLong())).thenReturn(continueResponse);

        // Build a PipelineRun with three blocks: SYSTEM (1 stage), LEGACY (1 stage), DEFAULT (2 stages).
        AbstractLoadedStage systemStage = new DefaultLoadedStage("system-stage", StageType.SYSTEM,
                Collections.singletonList(change1));
        AbstractLoadedStage legacyStage = new DefaultLoadedStage("legacy-stage", StageType.LEGACY,
                Collections.singletonList(change1));
        AbstractLoadedStage userStageA = new DefaultLoadedStage("user-a", StageType.DEFAULT,
                Collections.singletonList(change1));
        AbstractLoadedStage userStageB = new DefaultLoadedStage("user-b", StageType.DEFAULT,
                Collections.singletonList(change2));

        planner.getNextExecution(PipelineRun.of(Arrays.asList(systemStage, legacyStage, userStageA, userStageB)));

        ArgumentCaptor<ExecutionPlanRequest> requestCaptor = ArgumentCaptor.forClass(ExecutionPlanRequest.class);
        verify(client).createExecution(requestCaptor.capture(), any(), anyLong());

        List<io.flamingock.cloud.api.request.StageBlockRequest> blocks =
                requestCaptor.getValue().getClientSubmission().getBlocks();

        // Three blocks, in dependency order.
        assertEquals(3, blocks.size());
        assertEquals(StageType.SYSTEM, blocks.get(0).getType());
        assertEquals(1, blocks.get(0).getStages().size());
        assertEquals("system-stage", blocks.get(0).getStages().get(0).getName());

        assertEquals(StageType.LEGACY, blocks.get(1).getType());
        assertEquals(1, blocks.get(1).getStages().size());
        assertEquals("legacy-stage", blocks.get(1).getStages().get(0).getName());

        // DEFAULT block contains both user stages, in input order.
        assertEquals(StageType.DEFAULT, blocks.get(2).getType());
        assertEquals(2, blocks.get(2).getStages().size());
        assertEquals("user-a", blocks.get(2).getStages().get(0).getName());
        assertEquals("user-b", blocks.get(2).getStages().get(1).getName());
    }

    @Test
    @DisplayName("Should return ABORT plan when server returns ABORT (regardless of change actions)")
    void shouldReturnAbortPlanWhenServerReturnsAbort() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.ABORT, 1L, null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Collections.singletonList(new ChangeResponse(change1.getId(), CloudChangeAction.MANUAL_INTERVENTION))))
        );
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Collections.singletonList(change1)));

        ExecutionPlan plan = planner.getNextExecution(PipelineRun.of(stages));

        // ABORT is a control-flow signal — the operation reads isAborted() and breaks the loop.
        // No exception flows from ExecutionPlan itself. MI per-stage is tested separately on
        // ExecutableStage.validate() (see core's ExecutableStageTest / AbstractPipelineTraverseOperationTest).
        assertTrue(plan.isAborted());
    }

    @Test
    @DisplayName("Should return ABORT plan when server returns ABORT (no MI changes)")
    void shouldReturnAbortPlanWhenServerReturnsAbortWithNoMIChanges() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.ABORT, 1L, null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Collections.singletonList(new ChangeResponse(change1.getId(), CloudChangeAction.APPLY))))
        );
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Collections.singletonList(change1)));

        ExecutionPlan plan = planner.getNextExecution(PipelineRun.of(stages));

        assertTrue(plan.isAborted());
    }

    @Test
    @DisplayName("Should include audit marks from multiple target systems in the execution request")
    void shouldIncludeAuditMarksInExecutionRequest() {
        TargetSystemAuditMarker marker1 = mock(TargetSystemAuditMarker.class);
        when(marker1.listAll()).thenReturn(new HashSet<>(Collections.singletonList(
                new TargetSystemAuditMark(change1.getId(), TargetSystemAuditMarkType.APPLIED)
        )));

        TargetSystemAuditMarker marker2 = mock(TargetSystemAuditMarker.class);
        when(marker2.listAll()).thenReturn(new HashSet<>(Collections.singletonList(
                new TargetSystemAuditMark(change2.getId(), TargetSystemAuditMarkType.ROLLED_BACK)
        )));

        CloudExecutionPlanner planner = buildPlanner(Arrays.asList(marker1, marker2));

        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.CONTINUE, 1L, null,
                Collections.singletonList(new StageResponse("stage-1", 0, Arrays.asList(
                        new ChangeResponse(change1.getId(), CloudChangeAction.SKIP),
                        new ChangeResponse(change2.getId(), CloudChangeAction.APPLY))))
        );
        response.setPipelineResult(pipelineResultUpToDate("stage-1"));
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Arrays.asList(change1, change2)));

        planner.getNextExecution(PipelineRun.of(stages));

        ArgumentCaptor<ExecutionPlanRequest> requestCaptor = ArgumentCaptor.forClass(ExecutionPlanRequest.class);
        verify(client).createExecution(requestCaptor.capture(), any(), anyLong());

        ExecutionPlanRequest request = requestCaptor.getValue();
        Map<String, CloudTargetSystemAuditMarkType> marksByChangeId = request.getClientSubmission().getBlocks().get(0).getStages().get(0).getChanges().stream()
                .collect(Collectors.toMap(ChangeRequest::getId, ChangeRequest::getOngoingStatus));

        assertEquals(CloudTargetSystemAuditMarkType.APPLIED, marksByChangeId.get(change1.getId()));
        assertEquals(CloudTargetSystemAuditMarkType.ROLLED_BACK, marksByChangeId.get(change2.getId()));
    }

    @Test
    @DisplayName("Should send NONE status when no audit marks exist for a change")
    void shouldSendNoneStatusWhenNoMarks() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.CONTINUE, 1L, null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Collections.singletonList(new ChangeResponse(change1.getId(), CloudChangeAction.SKIP))))
        );
        response.setPipelineResult(pipelineResultUpToDate("stage-1"));
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Collections.singletonList(change1)));

        planner.getNextExecution(PipelineRun.of(stages));

        ArgumentCaptor<ExecutionPlanRequest> requestCaptor = ArgumentCaptor.forClass(ExecutionPlanRequest.class);
        verify(client).createExecution(requestCaptor.capture(), any(), anyLong());

        ChangeRequest changeRequest = requestCaptor.getValue().getClientSubmission().getBlocks().get(0).getStages().get(0).getChanges().get(0);
        assertEquals(CloudTargetSystemAuditMarkType.NONE, changeRequest.getOngoingStatus());
    }

    @Test
    @DisplayName("Should clear marks when response has synchronizedMarks=true")
    void shouldClearMarksWhenResponseHasSynchronizedMarksTrue() {
        TargetSystemAuditMarker marker1 = mock(TargetSystemAuditMarker.class);
        when(marker1.listAll()).thenReturn(new HashSet<>(Collections.singletonList(
                new TargetSystemAuditMark(change1.getId(), TargetSystemAuditMarkType.APPLIED)
        )));

        TargetSystemAuditMarker marker2 = mock(TargetSystemAuditMarker.class);
        when(marker2.listAll()).thenReturn(new HashSet<>(Collections.singletonList(
                new TargetSystemAuditMark(change2.getId(), TargetSystemAuditMarkType.ROLLED_BACK)
        )));

        CloudExecutionPlanner planner = buildPlanner(Arrays.asList(marker1, marker2));

        ExecutionPlanResponse response = buildSyncResponse(CloudExecutionAction.CONTINUE, true);
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Arrays.asList(change1, change2)));

        planner.getNextExecution(PipelineRun.of(stages));

        verify(marker1).clearMark(change1.getId());
        verify(marker2).clearMark(change2.getId());
    }

    @Test
    @DisplayName("Should not clear marks when response has synchronizedMarks=false")
    void shouldNotClearMarksWhenResponseHasSynchronizedMarksFalse() {
        TargetSystemAuditMarker marker1 = mock(TargetSystemAuditMarker.class);
        when(marker1.listAll()).thenReturn(new HashSet<>(Collections.singletonList(
                new TargetSystemAuditMark(change1.getId(), TargetSystemAuditMarkType.APPLIED)
        )));

        CloudExecutionPlanner planner = buildPlanner(Collections.singletonList(marker1));

        ExecutionPlanResponse response = buildSyncResponse(CloudExecutionAction.CONTINUE, false);
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Collections.singletonList(change1)));

        planner.getNextExecution(PipelineRun.of(stages));

        verify(marker1, never()).clearMark(any());
    }

    @Test
    @DisplayName("Should clear marks regardless of response action (ABORT)")
    void shouldClearMarksRegardlessOfResponseAction() {
        TargetSystemAuditMarker marker1 = mock(TargetSystemAuditMarker.class);
        when(marker1.listAll()).thenReturn(new HashSet<>(Collections.singletonList(
                new TargetSystemAuditMark(change1.getId(), TargetSystemAuditMarkType.APPLIED)
        )));

        CloudExecutionPlanner planner = buildPlanner(Collections.singletonList(marker1));

        ExecutionPlanResponse response = buildSyncResponse(CloudExecutionAction.ABORT, true);
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Collections.singletonList(change1)));

        planner.getNextExecution(PipelineRun.of(stages));

        verify(marker1).clearMark(change1.getId());
    }

    @Test
    @DisplayName("Should only clear marks that were captured in the snapshot, not new ones")
    void shouldNotClearNewMarksWrittenAfterRequest() {
        TargetSystemAuditMarker marker1 = mock(TargetSystemAuditMarker.class);
        // At snapshot time, only change1 has a mark
        when(marker1.listAll()).thenReturn(new HashSet<>(Collections.singletonList(
                new TargetSystemAuditMark(change1.getId(), TargetSystemAuditMarkType.APPLIED)
        )));

        CloudExecutionPlanner planner = buildPlanner(Collections.singletonList(marker1));

        ExecutionPlanResponse response = buildSyncResponse(CloudExecutionAction.CONTINUE, true);
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Arrays.asList(change1, change2)));

        planner.getNextExecution(PipelineRun.of(stages));

        // Only change1 should be cleared (was in snapshot), not change2
        verify(marker1).clearMark(change1.getId());
        verify(marker1, never()).clearMark(change2.getId());
    }

    private ExecutionPlanResponse buildSyncResponse(CloudExecutionAction action, boolean synchronizedMarks) {
        ExecutionPlanResponse response = new ExecutionPlanResponse(
                action, 1L, null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Arrays.asList(
                                new ChangeResponse(change1.getId(), CloudChangeAction.SKIP),
                                new ChangeResponse(change2.getId(), CloudChangeAction.SKIP))))
        );
        // pipelineResult is required by validate() on CONTINUE/EXECUTE. ABORT/AWAIT
        // ignore it but accept it being present — convenient for one shared helper.
        response.setPipelineResult(pipelineResultUpToDate("stage-1"));
        response.setSynchronizedMarks(synchronizedMarks);
        return response;
    }

    /**
     * Trivial pipelineResult helper: marks every named stage as UP_TO_DATE with no
     * per-change ALREADY_APPLIED records. Used by tests that don't care about the result
     * side specifically; the new tests further below build richer pipelineResults to
     * exercise the actual writer behaviour.
     */
    private PipelineResultResponse pipelineResultUpToDate(String... stageNames) {
        List<StageResultResponse> stages = Arrays.stream(stageNames)
                .map(name -> new StageResultResponse(name, CloudPlannerVerdict.UP_TO_DATE,
                        Collections.emptyList()))
                .collect(Collectors.toList());
        return new PipelineResultResponse(stages);
    }

    // -----------------------------------------------------------------------------------
    // Tests for the new pipelineResult application path (server-as-planner)
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("CONTINUE with pipelineResult writes per-stage verdict into PipelineRun")
    void continueWithPipelineResultWritesVerdict() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        // Server says UP_TO_DATE for stage-a, NEEDS_WORK for stage-b. Client must honour both.
        PipelineResultResponse pipelineResult = new PipelineResultResponse(Arrays.asList(
                new StageResultResponse("stage-a", CloudPlannerVerdict.UP_TO_DATE,
                        Collections.emptyList()),
                new StageResultResponse("stage-b", CloudPlannerVerdict.NEEDS_WORK,
                        Collections.emptyList())
        ));
        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.CONTINUE, 1L, null, Collections.emptyList(),
                pipelineResult, false);
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Arrays.asList(
                new DefaultLoadedStage("stage-a", StageType.DEFAULT,
                        Collections.singletonList(change1)),
                new DefaultLoadedStage("stage-b", StageType.DEFAULT,
                        Collections.singletonList(change2)));
        PipelineRun run = PipelineRun.of(stages);

        planner.getNextExecution(run);

        // Verdicts land on the PipelineRun's per-stage StageResult.plannerVerdict via the
        // existing community-side writer (monotone-forward enforced there).
        assertEquals(PlannerVerdict.UP_TO_DATE,
                run.getStageRuns().get(0).getResult().getPlannerVerdict());
        assertEquals(PlannerVerdict.NEEDS_WORK,
                run.getStageRuns().get(1).getResult().getPlannerVerdict());
    }

    @Test
    @DisplayName("EXECUTE with pipelineResult upgrades NOT_REACHED records to ALREADY_APPLIED")
    void executeWithPipelineResultUpgradesAlreadyAppliedRecords() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        // Server tells the client: change1 is ALREADY_APPLIED, change2 is NOT_REACHED.
        // Per the response, only change2 needs to be executed (stages[] reflects the work).
        PipelineResultResponse pipelineResult = new PipelineResultResponse(
                Collections.singletonList(new StageResultResponse("stage-1",
                        CloudPlannerVerdict.NEEDS_WORK,
                        Arrays.asList(
                                new ChangeResultResponse(change1.getId(),
                                        CloudChangeStatus.ALREADY_APPLIED),
                                new ChangeResultResponse(change2.getId(),
                                        CloudChangeStatus.NOT_REACHED))))
        );
        io.flamingock.cloud.api.response.LockInfoResponse lockInfo =
                new io.flamingock.cloud.api.response.LockInfoResponse();
        lockInfo.setKey("test-key");
        lockInfo.setOwner("test-runner");
        lockInfo.setAcquisitionId("acq-1");
        lockInfo.setAcquiredForMillis(60000L);
        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.EXECUTE, 1L, lockInfo,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Collections.singletonList(
                                new ChangeResponse(change2.getId(), CloudChangeAction.APPLY)))),
                pipelineResult, false);
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT,
                        Arrays.asList(change1, change2)));
        PipelineRun run = PipelineRun.of(stages);

        planner.getNextExecution(run);

        // change1 upgraded NOT_REACHED → ALREADY_APPLIED via the planner-side writer.
        // change2 stays NOT_REACHED (defensive merge — server reported NOT_REACHED, so the
        // writer skips it).
        Map<String, ChangeStatus> statusById = run.getStageRuns().get(0).getResult()
                .getChanges().stream()
                .collect(Collectors.toMap(
                        io.flamingock.internal.common.core.response.data.ChangeResult::getChangeId,
                        io.flamingock.internal.common.core.response.data.ChangeResult::getStatus));
        assertEquals(ChangeStatus.ALREADY_APPLIED, statusById.get(change1.getId()));
        assertEquals(ChangeStatus.NOT_REACHED, statusById.get(change2.getId()));
        // Verdict also lands.
        assertEquals(PlannerVerdict.NEEDS_WORK,
                run.getStageRuns().get(0).getResult().getPlannerVerdict());
    }

    @Test
    @DisplayName("ABORT does not touch PipelineRun verdict/records (no pipelineResult on the wire)")
    void abortLeavesPipelineRunUntouched() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        // ABORT carries no pipelineResult — validate() doesn't require it, and the planner
        // must not attempt to apply one (NPE-guard).
        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.ABORT, 1L, null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Collections.singletonList(
                                new ChangeResponse(change1.getId(), CloudChangeAction.APPLY))))
        );
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT,
                        Collections.singletonList(change1)));
        PipelineRun run = PipelineRun.of(stages);

        ExecutionPlan plan = planner.getNextExecution(run);

        assertTrue(plan.isAborted());
        // Default verdict stays NOT_EVALUATED — planner-side writes are gated to
        // CONTINUE/EXECUTE branches only.
        assertEquals(PlannerVerdict.NOT_EVALUATED,
                run.getStageRuns().get(0).getResult().getPlannerVerdict());
    }
}
