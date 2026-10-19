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

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.common.core.recovery.action.ChangeActionMap;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityChangeActionBuilderTest {

    @Test
    @DisplayName("Normal already-applied changes are skipped")
    void normalAlreadyAppliedChangeIsSkipped() {
        AbstractLoadedChange change = change("change-1", false);
        Map<String, AuditEntry> audit = new HashMap<>();
        audit.put("change-1", auditEntry("change-1", AuditEntry.Status.APPLIED));

        ChangeActionMap actions = CommunityChangeActionBuilder.build(Collections.singletonList(change), audit);

        assertEquals(ChangeAction.SKIP, actions.getActionFor("change-1"));
    }

    @Test
    @DisplayName("runAlways already-applied changes are applied again")
    void runAlwaysAlreadyAppliedChangeIsAppliedAgain() {
        AbstractLoadedChange change = change("change-1", true);
        Map<String, AuditEntry> audit = new HashMap<>();
        audit.put("change-1", auditEntry("change-1", AuditEntry.Status.APPLIED));

        ChangeActionMap actions = CommunityChangeActionBuilder.build(Collections.singletonList(change), audit);

        assertEquals(ChangeAction.APPLY, actions.getActionFor("change-1"));
    }

    @Test
    @DisplayName("runAlways manual-marked-as-applied changes are applied again")
    void runAlwaysManualMarkedAsAppliedChangeIsAppliedAgain() {
        AbstractLoadedChange change = change("change-1", true);
        Map<String, AuditEntry> audit = new HashMap<>();
        audit.put("change-1", auditEntry("change-1", AuditEntry.Status.MANUAL_MARKED_AS_APPLIED));

        ChangeActionMap actions = CommunityChangeActionBuilder.build(Collections.singletonList(change), audit);

        assertEquals(ChangeAction.APPLY, actions.getActionFor("change-1"));
    }

    private static AbstractLoadedChange change(String id, boolean runAlways) {
        AbstractLoadedChange change = mock(AbstractLoadedChange.class);
        when(change.getId()).thenReturn(id);
        when(change.isRunAlways()).thenReturn(runAlways);
        return change;
    }

    private static AuditEntry auditEntry(String changeId, AuditEntry.Status status) {
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
                null,
                null,
                null,
                RecoveryStrategy.MANUAL_INTERVENTION,
                null
        );
    }
}
