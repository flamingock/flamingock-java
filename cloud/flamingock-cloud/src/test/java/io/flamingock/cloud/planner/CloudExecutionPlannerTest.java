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
import io.flamingock.cloud.lock.CloudLockService;
import io.flamingock.cloud.planner.client.ExecutionPlannerClient;
import io.flamingock.internal.common.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.LoadedChangeBuilder;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.loaded.stage.DefaultLoadedStage;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.core.cloud.changes._001__CloudChange1;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudExecutionPlannerTest {

    private static AbstractLoadedChange change1;

    private ExecutionPlannerClient client;
    private CloudExecutionPlanner planner;

    @BeforeAll
    static void setupChanges() {
        change1 = LoadedChangeBuilder.getCodeBuilderInstance(_001__CloudChange1.class).build();
    }

    @BeforeEach
    void setup() {
        client = mock(ExecutionPlannerClient.class);
        CoreConfigurable config = mock(CoreConfigurable.class);
        when(config.getLockAcquiredForMillis()).thenReturn(60000L);
        when(config.getLockQuitTryingAfterMillis()).thenReturn(30000L);
        when(config.getLockTryFrequencyMillis()).thenReturn(1000L);

        TargetSystemAuditMarker auditMarker = mock(TargetSystemAuditMarker.class);
        when(auditMarker.listAll()).thenReturn(Collections.emptySet());

        planner = new CloudExecutionPlanner(
                RunnerId.fromString("test-runner"),
                client,
                config,
                mock(CloudLockService.class),
                auditMarker,
                TimeService.getDefault()
        );
    }

    @Test
    @DisplayName("Should return ABORT plan when server returns ABORT with MANUAL_INTERVENTION changes")
    void shouldReturnAbortPlanWhenServerReturnsAbort() {
        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.ABORT,
                "exec-1",
                null,
                Collections.singletonList(new StageResponse("stage-1", 0,
                        Collections.singletonList(new ChangeResponse(change1.getId(), CloudChangeAction.MANUAL_INTERVENTION))))
        );
        when(client.createExecution(any(), any(), anyLong())).thenReturn(response);

        List<AbstractLoadedStage> stages = Collections.singletonList(
                new DefaultLoadedStage("stage-1", StageType.DEFAULT, Collections.singletonList(change1)));

        ExecutionPlan plan = planner.getNextExecution(stages);

        assertTrue(plan.isAborted());
        assertFalse(plan.isExecutionRequired());
        assertThrows(ManualInterventionRequiredException.class, plan::validate);
    }

    @Test
    @DisplayName("Should return ABORT plan that throws FlamingockException when server returns ABORT but no MI changes")
    void shouldReturnAbortPlanWhenServerReturnsAbortWithNoMIChanges() {
        ExecutionPlanResponse response = new ExecutionPlanResponse(
                CloudExecutionAction.ABORT,
                "exec-1",
                null,
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
}
