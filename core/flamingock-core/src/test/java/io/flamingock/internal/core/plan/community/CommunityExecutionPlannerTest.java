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
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditReader;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
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
    @DisplayName("Should return ABORT without acquiring lock when manual intervention is required")
    void shouldReturnAbortWithoutAcquiringLockWhenManualInterventionRequired() {
        AbstractLoadedChange change = mockLoadedChange("change-1");
        AbstractLoadedStage stage = mockStage("stage-1", change);

        Map<String, AuditEntry> snapshot = new HashMap<>();
        snapshot.put("change-1", buildAuditEntry("change-1", AuditEntry.Status.FAILED, AuditTxType.NON_TX));
        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(snapshot);

        ExecutionPlan plan = planner.getNextExecution(Collections.singletonList(stage));

        assertTrue(plan.isAborted());
        verify(lockService, never()).upsert(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should acquire lock and return execution plan when no manual intervention")
    void shouldAcquireLockAndReturnExecutionPlanWhenNoManualIntervention() {
        AbstractLoadedChange change = mockLoadedChange("change-1");
        AbstractLoadedStage stage = mockStage("stage-1", change);

        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(Collections.emptyMap());
        when(lockService.upsert(any(), any(), anyLong()))
                .thenReturn(new LockAcquisition(RunnerId.fromString("test-runner"), 60000L));

        ExecutionPlan plan = planner.getNextExecution(Collections.singletonList(stage));

        assertFalse(plan.isAborted());
        assertTrue(plan.isExecutionRequired());
        verify(lockService).upsert(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Should return CONTINUE without lock when all changes are already applied")
    void shouldReturnContinueWithoutLockWhenAllChangesApplied() {
        AbstractLoadedChange change = mockLoadedChange("change-1");
        AbstractLoadedStage stage = mockStage("stage-1", change);

        Map<String, AuditEntry> snapshot = new HashMap<>();
        snapshot.put("change-1", buildAuditEntry("change-1", AuditEntry.Status.APPLIED, null));
        when(auditReader.getAuditSnapshotByChangeId()).thenReturn(snapshot);

        ExecutionPlan plan = planner.getNextExecution(Collections.singletonList(stage));

        assertFalse(plan.isAborted());
        assertFalse(plan.isExecutionRequired());
        verify(lockService, never()).upsert(any(), any(), anyLong());
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
        AbstractLoadedStage stage = mock(AbstractLoadedStage.class);
        when(stage.getName()).thenReturn(name);
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
