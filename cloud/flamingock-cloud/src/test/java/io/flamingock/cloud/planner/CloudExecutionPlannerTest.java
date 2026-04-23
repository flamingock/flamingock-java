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
import io.flamingock.cloud.api.response.ExecutionPlanResponse;
import io.flamingock.cloud.api.response.StageResponse;
import io.flamingock.cloud.api.vo.CloudChangeAction;
import io.flamingock.cloud.api.vo.CloudExecutionAction;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.cloud.planner.client.ExecutionPlannerClient;
import io.flamingock.internal.common.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.LoadedChangeBuilder;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.loaded.stage.DefaultLoadedStage;
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
    @DisplayName("Should return ABORT plan when server returns ABORT with MANUAL_INTERVENTION changes")
    void shouldReturnAbortPlanWhenServerReturnsAbort() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.ABORT, "exec-1", null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Collections.singletonList(new ChangeResponse(change1.getId(), CloudChangeAction.MANUAL_INTERVENTION))))
        );
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Collections.singletonList(change1)));

        ExecutionPlan plan = planner.getNextExecution(stages);

        assertTrue(plan.isAborted());
        assertThrows(ManualInterventionRequiredException.class, plan::validate);
    }

    @Test
    @DisplayName("Should return ABORT plan that throws FlamingockException when server returns ABORT but no MI changes")
    void shouldReturnAbortPlanWhenServerReturnsAbortWithNoMIChanges() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.ABORT, "exec-1", null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Collections.singletonList(new ChangeResponse(change1.getId(), CloudChangeAction.APPLY))))
        );
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Collections.singletonList(change1)));

        ExecutionPlan plan = planner.getNextExecution(stages);

        assertTrue(plan.isAborted());
        assertThrows(io.flamingock.internal.common.core.error.FlamingockException.class, plan::validate);
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
                CloudExecutionAction.CONTINUE, "exec-1", null,
                Collections.singletonList(new StageResponse("stage-1", 0, Arrays.asList(
                        new ChangeResponse(change1.getId(), CloudChangeAction.SKIP),
                        new ChangeResponse(change2.getId(), CloudChangeAction.APPLY))))
        );
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Arrays.asList(change1, change2)));

        planner.getNextExecution(stages);

        ArgumentCaptor<ExecutionPlanRequest> requestCaptor = ArgumentCaptor.forClass(ExecutionPlanRequest.class);
        verify(client).createExecution(requestCaptor.capture(), any(), anyLong());

        ExecutionPlanRequest request = requestCaptor.getValue();
        Map<String, CloudTargetSystemAuditMarkType> marksByChangeId = request.getClientSubmission().getStages().get(0).getChanges().stream()
                .collect(Collectors.toMap(ChangeRequest::getId, ChangeRequest::getOngoingStatus));

        assertEquals(CloudTargetSystemAuditMarkType.APPLIED, marksByChangeId.get(change1.getId()));
        assertEquals(CloudTargetSystemAuditMarkType.ROLLED_BACK, marksByChangeId.get(change2.getId()));
    }

    @Test
    @DisplayName("Should send NONE status when no audit marks exist for a change")
    void shouldSendNoneStatusWhenNoMarks() {
        CloudExecutionPlanner planner = buildPlanner(Collections.emptyList());

        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.CONTINUE, "exec-1", null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Collections.singletonList(new ChangeResponse(change1.getId(), CloudChangeAction.SKIP))))
        );
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Collections.singletonList(change1)));

        planner.getNextExecution(stages);

        ArgumentCaptor<ExecutionPlanRequest> requestCaptor = ArgumentCaptor.forClass(ExecutionPlanRequest.class);
        verify(client).createExecution(requestCaptor.capture(), any(), anyLong());

        ChangeRequest changeRequest = requestCaptor.getValue().getClientSubmission().getStages().get(0).getChanges().get(0);
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

        planner.getNextExecution(stages);

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

        planner.getNextExecution(stages);

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

        planner.getNextExecution(stages);

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

        planner.getNextExecution(stages);

        // Only change1 should be cleared (was in snapshot), not change2
        verify(marker1).clearMark(change1.getId());
        verify(marker1, never()).clearMark(change2.getId());
    }

    private ExecutionPlanResponse buildSyncResponse(CloudExecutionAction action, boolean synchronizedMarks) {
        ExecutionPlanResponse response = new ExecutionPlanResponse(
                action, "exec-1", null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Arrays.asList(
                                new ChangeResponse(change1.getId(), CloudChangeAction.SKIP),
                                new ChangeResponse(change2.getId(), CloudChangeAction.SKIP))))
        );
        response.setSynchronizedMarks(synchronizedMarks);
        return response;
    }
}
