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
package io.flamingock.internal.core.plan.community;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.recovery.RecoveryIssue;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditReader;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.run.PipelineRun;
import io.flamingock.internal.core.pipeline.run.StageRun;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class CommunityExecutionPlannerTest {

    private CommunityAuditReader auditReader;
    private CommunityLockService lockService;
    private CoreConfigurable configuration;
    private CommunityExecutionPlanner planner;

    @BeforeEach
    void setup() {
        auditReader = mock(CommunityAuditReader.class);
        lockService = mock(CommunityLockService.class);
        configuration = mock(CoreConfigurable.class);

        when(configuration.getLockAcquiredForMillis()).thenReturn(60000L);
        when(configuration.getLockQuitTryingAfterMillis()).thenReturn(30000L);
        when(configuration.getLockTryFrequencyMillis()).thenReturn(1000L);
        when(configuration.isEnableRefreshDaemon()).thenReturn(false);

        planner = new CommunityExecutionPlanner(
                RunnerId.fromString("test-runner"),
                lockService,
                auditReader,
                configuration
        );
    }

    @Test
    @DisplayName("Should acquire lock and return execution plan even when changes require manual intervention (MI is now a per-stage concern)")
    void shouldProceedEvenWhenManualInterventionRequired() {
        AbstractLoadedChange change = mockLoadedChange("change-1");
        AbstractLoadedStage stage = mockStage("stage-1", change);

        Map<String, AuditEntry> snapshot = new HashMap<>();
        snapshot.put("change-1", buildAuditEntry("change-1", AuditEntry.Status.FAILED, AuditTxType.NON_TX));
        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(snapshot);
        when(lockService.upsert(any(), any(), anyLong()))
                .thenReturn(new LockAcquisition(RunnerId.fromString("test-runner"), 60000L));

        ExecutionPlan plan = planner.getNextExecution(PipelineRun.of(Collections.singletonList(stage)));

        // The planner no longer aborts based on MI; the operation lambda gates MI per stage.
        assertFalse(plan.isAborted());
        verify(lockService).upsert(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should acquire lock and return execution plan when no manual intervention")
    void shouldAcquireLockAndReturnExecutionPlanWhenNoManualIntervention() {
        AbstractLoadedChange change = mockLoadedChange("change-1");
        AbstractLoadedStage stage = mockStage("stage-1", change);

        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(Collections.emptyMap());
        when(lockService.upsert(any(), any(), anyLong()))
                .thenReturn(new LockAcquisition(RunnerId.fromString("test-runner"), 60000L));

        ExecutionPlan plan = planner.getNextExecution(PipelineRun.of(Collections.singletonList(stage)));

        assertFalse(plan.isAborted());
        assertTrue(plan.isExecutionRequired());
        verify(lockService).upsert(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should skip stages already marked Failed in the run and plan only the remaining stages")
    void shouldSkipFailedStagesAndPlanRemaining() {
        AbstractLoadedChange change1 = mockLoadedChange("change-1");
        AbstractLoadedChange change2 = mockLoadedChange("change-2");
        AbstractLoadedStage stage1 = mockStage("stage-1", change1);
        AbstractLoadedStage stage2 = mockStage("stage-2", change2);

        PipelineRun pipelineRun = PipelineRun.of(java.util.Arrays.asList(stage1, stage2));
        pipelineRun.markStageFailed("stage-1", new RuntimeException("boom"));

        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(Collections.emptyMap());
        when(lockService.upsert(any(), any(), anyLong()))
                .thenReturn(new LockAcquisition(RunnerId.fromString("test-runner"), 60000L));

        ExecutionPlan plan = planner.getNextExecution(pipelineRun);

        assertFalse(plan.isAborted());
        assertTrue(plan.isExecutionRequired());
        // stage-1 was Failed → filtered before action-building. stage-2 was eligible → planned.
        verify(stage1, never()).applyActions(any());
        verify(stage2, atLeastOnce()).applyActions(any());
    }

    @Test
    @DisplayName("Should skip stages in BlockedForMI (subtype of Failed) and plan only the remaining stages")
    void shouldSkipBlockedForMIStagesAndPlanRemaining() {
        AbstractLoadedChange change1 = mockLoadedChange("change-1");
        AbstractLoadedChange change2 = mockLoadedChange("change-2");
        AbstractLoadedStage stage1 = mockStage("stage-1", change1);
        AbstractLoadedStage stage2 = mockStage("stage-2", change2);

        PipelineRun pipelineRun = PipelineRun.of(java.util.Arrays.asList(stage1, stage2));
        pipelineRun.markStageBlockedFromMI(
                "stage-1",
                Collections.singletonList(new RecoveryIssue("change-1")));

        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(Collections.emptyMap());
        when(lockService.upsert(any(), any(), anyLong()))
                .thenReturn(new LockAcquisition(RunnerId.fromString("test-runner"), 60000L));

        ExecutionPlan plan = planner.getNextExecution(pipelineRun);

        assertFalse(plan.isAborted());
        assertTrue(plan.isExecutionRequired());
        verify(stage1, never()).applyActions(any());
        verify(stage2, atLeastOnce()).applyActions(any());
    }

    @Test
    @DisplayName("Should return ABORT without acquiring lock when every stage in the only block has failed")
    void shouldReturnAbortWhenAllStagesInTheOnlyBlockHaveFailed() {
        AbstractLoadedChange change = mockLoadedChange("change-1");
        AbstractLoadedStage stage = mockStage("stage-1", change);

        PipelineRun pipelineRun = PipelineRun.of(Collections.singletonList(stage));
        pipelineRun.markStageFailed("stage-1", new RuntimeException("boom"));

        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(Collections.emptyMap());

        ExecutionPlan plan = planner.getNextExecution(pipelineRun);

        // The only block is now terminal+hasFailures → ABORT. Lock is not acquired (block
        // selection happens before the audit dance).
        assertTrue(plan.isAborted());
        assertFalse(plan.isExecutionRequired());
        verify(lockService, never()).upsert(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should return CONTINUE without lock when all changes are already applied")
    void shouldReturnContinueWithoutLockWhenAllChangesApplied() {
        AbstractLoadedChange change = mockLoadedChange("change-1");
        AbstractLoadedStage stage = mockStage("stage-1", change);

        Map<String, AuditEntry> snapshot = new HashMap<>();
        snapshot.put("change-1", buildAuditEntry("change-1", AuditEntry.Status.APPLIED, null));
        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(snapshot);

        ExecutionPlan plan = planner.getNextExecution(PipelineRun.of(Collections.singletonList(stage)));

        assertFalse(plan.isAborted());
        assertFalse(plan.isExecutionRequired());
        verify(lockService, never()).upsert(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should return CONTINUE when every block is terminal+successful (multi-block, all done)")
    void shouldReturnContinueWhenAllBlocksAreSuccessful() {
        AbstractLoadedChange systemChange = mockLoadedChange("sys-c1");
        AbstractLoadedChange userChange = mockLoadedChange("user-c1");
        AbstractLoadedStage systemStage = mockTypedStage("flamingock-system-stage", io.flamingock.api.StageType.SYSTEM, systemChange);
        AbstractLoadedStage userStage = mockTypedStage("changes", io.flamingock.api.StageType.DEFAULT, userChange);

        PipelineRun pipelineRun = PipelineRun.of(java.util.Arrays.asList(systemStage, userStage));
        // Both stages completed in this run.
        pipelineRun.markStageCompleted("flamingock-system-stage", io.flamingock.internal.common.core.response.data.StageResult.builder()
                .stageId("flamingock-system-stage").stageName("flamingock-system-stage")
                .state(io.flamingock.internal.common.core.response.data.StageState.COMPLETED).build());
        pipelineRun.markStageCompleted("changes", io.flamingock.internal.common.core.response.data.StageResult.builder()
                .stageId("changes").stageName("changes")
                .state(io.flamingock.internal.common.core.response.data.StageState.COMPLETED).build());

        // Always-walk reads the audit on every iteration (stamps planner verdicts onto stages
        // operation hasn't terminal-stated). Provide an empty snapshot — the test isn't about
        // verdicts, it's about CONTINUE shortcut when state already shows completion.
        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(Collections.emptyMap());

        ExecutionPlan plan = planner.getNextExecution(pipelineRun);

        assertFalse(plan.isAborted());
        assertFalse(plan.isExecutionRequired());
        verify(lockService, never()).upsert(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should return ABORT when an earlier block is terminal+hasFailures (block dependency)")
    void shouldReturnAbortWhenAnEarlierBlockHasFailures() {
        AbstractLoadedChange systemChange = mockLoadedChange("sys-c1");
        AbstractLoadedChange userChange = mockLoadedChange("user-c1");
        AbstractLoadedStage systemStage = mockTypedStage("flamingock-system-stage", io.flamingock.api.StageType.SYSTEM, systemChange);
        AbstractLoadedStage userStage = mockTypedStage("changes", io.flamingock.api.StageType.DEFAULT, userChange);

        PipelineRun pipelineRun = PipelineRun.of(java.util.Arrays.asList(systemStage, userStage));
        // System block fails — earlier block's failure must block downstream work.
        pipelineRun.markStageFailed("flamingock-system-stage", new RuntimeException("system stage exploded"));
        // Always-walk reads audit every iteration; provide an empty snapshot.
        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(Collections.emptyMap());

        ExecutionPlan plan = planner.getNextExecution(pipelineRun);

        assertTrue(plan.isAborted());
        verify(lockService, never()).upsert(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should mark a block as UP_TO_DATE (planner verdict) when its changes are already applied per audit and plan from the next block")
    void shouldMarkUpToDateBlockAndPlanFromNextBlock() {
        AbstractLoadedChange legacyChange = mockLoadedChange("legacy-c1");
        AbstractLoadedChange userChange = mockLoadedChange("user-c1");
        AbstractLoadedStage legacyStage = mockTypedStage("flamingock-legacy-stage", io.flamingock.api.StageType.LEGACY, legacyChange);
        AbstractLoadedStage userStage = mockTypedStage("changes", io.flamingock.api.StageType.DEFAULT, userChange);

        PipelineRun pipelineRun = PipelineRun.of(java.util.Arrays.asList(legacyStage, userStage));
        // Legacy stages are NOT_STARTED in the runtime view but the audit says their changes are
        // already applied (a previous run / another instance). The planner must advance past the
        // legacy block by stamping UP_TO_DATE + ALREADY_APPLIED records, then plan the user
        // (DEFAULT) block. The legacy stage's state stays NOT_STARTED — the verdict alone carries
        // the "done" semantic.
        Map<String, AuditEntry> snapshot = new HashMap<>();
        snapshot.put("legacy-c1", buildAuditEntry("legacy-c1", AuditEntry.Status.APPLIED, null));
        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(snapshot);
        when(lockService.upsert(any(), any(), anyLong()))
                .thenReturn(new LockAcquisition(RunnerId.fromString("test-runner"), 60000L));

        ExecutionPlan plan = planner.getNextExecution(pipelineRun);

        StageRun legacyRun = pipelineRun.getStageRun("flamingock-legacy-stage");
        assertEquals(io.flamingock.internal.common.core.response.data.PlannerVerdict.UP_TO_DATE,
                legacyRun.getResult().getPlannerVerdict(),
                "Legacy stage should carry UP_TO_DATE verdict from the planner");
        assertTrue(legacyRun.getState().isNotStarted(),
                "Legacy stage state stays NOT_STARTED — verdict carries the done semantic; "
                        + "operation never invoked the executor for this stage");
        assertEquals(1, legacyRun.getResult().getChanges().size(),
                "Planner should have populated one ALREADY_APPLIED ChangeResult from the audit snapshot");
        assertEquals("legacy-c1", legacyRun.getResult().getChanges().get(0).getChangeId());
        assertEquals(io.flamingock.internal.common.core.response.data.ChangeStatus.ALREADY_APPLIED,
                legacyRun.getResult().getChanges().get(0).getStatus());
        // Planner advanced to the DEFAULT block and acquired the lock for actual work.
        // The legacy block was verdicted UP_TO_DATE during stampSnapshotFacts and never reached
        // buildExecutableStages — so applyActions is NOT called on legacyStage; only userStage.
        assertFalse(plan.isAborted());
        assertTrue(plan.isExecutionRequired());
        verify(legacyStage, never()).applyActions(any());
        verify(userStage, atLeastOnce()).applyActions(any());
        verify(lockService, atLeastOnce()).upsert(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should plan from active block, skipping earlier successful blocks (block dependency)")
    void shouldPlanFromActiveBlockSkippingEarlierSuccessfulBlock() {
        AbstractLoadedChange systemChange = mockLoadedChange("sys-c1");
        AbstractLoadedChange userChange = mockLoadedChange("user-c1");
        AbstractLoadedStage systemStage = mockTypedStage("flamingock-system-stage", io.flamingock.api.StageType.SYSTEM, systemChange);
        AbstractLoadedStage userStage = mockTypedStage("changes", io.flamingock.api.StageType.DEFAULT, userChange);

        PipelineRun pipelineRun = PipelineRun.of(java.util.Arrays.asList(systemStage, userStage));
        // System block done; DEFAULT block (user stages) still pending.
        pipelineRun.markStageCompleted("flamingock-system-stage", io.flamingock.internal.common.core.response.data.StageResult.builder()
                .stageId("flamingock-system-stage").stageName("flamingock-system-stage")
                .state(io.flamingock.internal.common.core.response.data.StageState.COMPLETED).build());

        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(Collections.emptyMap());
        when(lockService.upsert(any(), any(), anyLong()))
                .thenReturn(new LockAcquisition(RunnerId.fromString("test-runner"), 60000L));

        ExecutionPlan plan = planner.getNextExecution(pipelineRun);

        assertFalse(plan.isAborted());
        assertTrue(plan.isExecutionRequired());
        // The completed system stage was skipped — only the user-block stage was action-built.
        verify(systemStage, never()).applyActions(any());
        verify(userStage, atLeastOnce()).applyActions(any());
    }

    @Test
    @DisplayName("Should enrich an operation-touched stage with audit-only changes (parallel-runner / external-marking scenario)")
    void shouldEnrichOperationTouchedStageWithAuditOnlyChanges() {
        // Stage has 2 changes. Operation applied c1 this run (state=COMPLETED, c1=APPLIED record).
        // Audit shows both c1 AND c2 as APPLIED — c2 was applied by another instance (parallel
        // runner) or by an external mark-as-applied. The planner should walk the stage despite its
        // COMPLETED state and add c2 as ALREADY_APPLIED via defensive merge — without disturbing
        // the operation's c1=APPLIED record or the stage's state.
        AbstractLoadedChange c1 = mockLoadedChange("c1");
        AbstractLoadedChange c2 = mockLoadedChange("c2");
        AbstractLoadedStage stage = mockTypedStage("only-stage", io.flamingock.api.StageType.DEFAULT, c1, c2);

        PipelineRun pipelineRun = PipelineRun.of(java.util.Collections.singletonList(stage));
        // Simulate operation's view: stage finished with only c1 recorded as APPLIED.
        io.flamingock.internal.common.core.response.data.StageResult operationOutput =
                io.flamingock.internal.common.core.response.data.StageResult.builder()
                        .stageId("only-stage").stageName("only-stage")
                        .state(io.flamingock.internal.common.core.response.data.StageState.COMPLETED)
                        .addChange(io.flamingock.internal.common.core.response.data.ChangeResult.builder()
                                .changeId("c1")
                                .status(io.flamingock.internal.common.core.response.data.ChangeStatus.APPLIED)
                                .build())
                        .build();
        pipelineRun.markStageCompleted("only-stage", operationOutput);

        // Audit reflects what's authoritative system-wide: both c1 and c2 are applied.
        Map<String, AuditEntry> snapshot = new HashMap<>();
        snapshot.put("c1", buildAuditEntry("c1", AuditEntry.Status.APPLIED, null));
        snapshot.put("c2", buildAuditEntry("c2", AuditEntry.Status.APPLIED, null));
        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(snapshot);

        planner.getNextExecution(pipelineRun);

        StageRun run = pipelineRun.getStageRun("only-stage");
        assertTrue(run.getState().isCompleted(),
                "Operation's COMPLETED state must stand — planner never overwrites state");
        // c1 stays APPLIED (operation wrote it; defensive merge preserves it). c2 added as
        // ALREADY_APPLIED by the planner from the audit snapshot.
        List<io.flamingock.internal.common.core.response.data.ChangeResult> records =
                run.getResult().getChanges();
        assertEquals(2, records.size(), "Planner should have added c2; total records = 2");
        io.flamingock.internal.common.core.response.data.ChangeResult c1Record =
                records.stream().filter(r -> "c1".equals(r.getChangeId())).findFirst().orElseThrow(AssertionError::new);
        io.flamingock.internal.common.core.response.data.ChangeResult c2Record =
                records.stream().filter(r -> "c2".equals(r.getChangeId())).findFirst().orElseThrow(AssertionError::new);
        assertEquals(io.flamingock.internal.common.core.response.data.ChangeStatus.APPLIED, c1Record.getStatus(),
                "c1 must remain APPLIED — operation's record is authoritative");
        assertEquals(io.flamingock.internal.common.core.response.data.ChangeStatus.ALREADY_APPLIED, c2Record.getStatus(),
                "c2 must be ALREADY_APPLIED — planner-added from audit");

        // Aggregate reporting reflects the enriched view: 1 applied this run, 1 already at target.
        io.flamingock.internal.common.core.response.data.ExecuteResponseData response = pipelineRun.toResponse();
        assertEquals(1, response.getAppliedChanges());
        assertEquals(1, response.getSkippedChanges());
        assertEquals(0, response.getFailedChanges());
    }

    private static AbstractLoadedChange mockLoadedChange(String id) {
        AbstractLoadedChange change = mock(AbstractLoadedChange.class);
        when(change.getId()).thenReturn(id);
        when(change.isRunAlways()).thenReturn(false);
        when(change.isStandard()).thenReturn(true);
        when(change.getRecovery()).thenReturn(RecoveryDescriptor.getDefault());
        when(change.isTransactional()).thenReturn(false);
        return change;
    }

    private static AbstractLoadedStage mockStage(String name, AbstractLoadedChange... changes) {
        return mockTypedStage(name, null, changes);
    }

    private static AbstractLoadedStage mockTypedStage(String name,
                                                      io.flamingock.api.StageType type,
                                                      AbstractLoadedChange... changes) {
        AbstractLoadedStage stage = mock(AbstractLoadedStage.class);
        when(stage.getName()).thenReturn(name);
        if (type != null) {
            when(stage.getType()).thenReturn(type);
        }
        List<AbstractLoadedChange> changeList = java.util.Arrays.asList(changes);
        when(stage.getChanges()).thenReturn(changeList);

        when(stage.applyActions(any())).thenAnswer(invocation -> {
            io.flamingock.internal.common.core.recovery.action.ChangeActionMap actionMap = invocation.getArgument(0);
            List<io.flamingock.internal.core.change.executable.ExecutableChange> execChanges = new java.util.ArrayList<>();
            for (AbstractLoadedChange c : changeList) {
                io.flamingock.internal.common.core.recovery.action.ChangeAction action = actionMap.getActionFor(c.getId());
                execChanges.add(new StubExecutableChange(c.getId(), action));
            }
            return new io.flamingock.internal.core.pipeline.execution.ExecutableStage(name, execChanges);
        });
        return stage;
    }

    private static AuditEntry buildAuditEntry(String changeId, AuditEntry.Status status, AuditTxType txType) {
        return new AuditEntry(
                "exec-1",
                "stage-1",
                changeId,
                "test-author",
                LocalDateTime.now(),
                status,
                AuditEntry.ChangeType.STANDARD_CODE,
                "io.flamingock.test.TestChange",
                "apply",
                null,
                100L,
                "localhost",
                null,
                false,
                null,
                txType,
                null,
                null,
                RecoveryStrategy.MANUAL_INTERVENTION,
                null
        );
    }

    private static class StubExecutableChange implements io.flamingock.internal.core.change.executable.ExecutableChange {
        private final String id;
        private final io.flamingock.internal.common.core.recovery.action.ChangeAction action;

        StubExecutableChange(String id, io.flamingock.internal.common.core.recovery.action.ChangeAction action) {
            this.id = id;
            this.action = action;
        }

        @Override public String getId() { return id; }
        @Override public io.flamingock.internal.common.core.recovery.action.ChangeAction getAction() { return action; }
        @Override public boolean isAlreadyApplied() { return action == io.flamingock.internal.common.core.recovery.action.ChangeAction.SKIP; }
        @Override public boolean isTransactional() { return false; }
        @Override public io.flamingock.internal.common.core.change.ChangeDescriptor getLoadedChange() { return null; }
        @Override public String getStageName() { return "test"; }
        @Override public void apply(io.flamingock.internal.core.runtime.ExecutionRuntime rt) {}
        @Override public String getApplyMethodName() { return "apply"; }
        @Override public void rollback(io.flamingock.internal.core.runtime.ExecutionRuntime rt) {}
        @Override public String getRollbackMethodName() { return null; }
        @Override public java.util.Optional<String> getOrder() { return java.util.Optional.of("001"); }
        @Override public String getAuthor() { return "test"; }
        @Override public String getSource() { return "Test"; }
        @Override public String getSourceFile() { return null; }
        @Override public boolean isRunAlways() { return false; }
        @Override public java.util.Optional<Boolean> getTransactionalFlag() { return java.util.Optional.empty(); }
        @Override public boolean isStandard() { return true; }
        @Override public boolean isSystem() { return false; }
        @Override public io.flamingock.internal.common.core.change.TargetSystemDescriptor getTargetSystem() { return null; }
        @Override public io.flamingock.internal.common.core.change.RecoveryDescriptor getRecovery() { return RecoveryDescriptor.getDefault(); }
        @Override public boolean isLegacy() { return false; }
        @Override public boolean isSortable() { return true; }
    }
}
