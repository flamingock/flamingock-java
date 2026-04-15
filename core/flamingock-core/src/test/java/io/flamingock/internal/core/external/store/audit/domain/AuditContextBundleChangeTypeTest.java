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
package io.flamingock.internal.core.external.store.audit.domain;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.CodeLoadedChange;
import io.flamingock.internal.core.change.loaded.SimpleTemplateLoadedChange;
import io.flamingock.internal.core.change.navigation.step.StartStep;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditContextBundleChangeTypeTest {

    @Test
    void toAuditEntry_shouldReturnStandardCode_whenCodeLoadedChange() {
        CodeLoadedChange loadedChange = mockCodeLoadedChange("change-001", false);
        AuditEntry entry = buildAuditEntry(loadedChange);
        assertEquals(AuditEntry.ChangeType.STANDARD_CODE, entry.getType());
    }

    @Test
    void toAuditEntry_shouldReturnStandardTemplate_whenTemplateLoadedChange() {
        SimpleTemplateLoadedChange loadedChange = mockTemplateLoadedChange("change-002");
        AuditEntry entry = buildAuditEntry(loadedChange);
        assertEquals(AuditEntry.ChangeType.STANDARD_TEMPLATE, entry.getType());
    }

    @Test
    void toAuditEntry_shouldReturnMongockExecution_whenLegacyChange() {
        CodeLoadedChange loadedChange = mockCodeLoadedChange("legacy-change", true);
        AuditEntry entry = buildAuditEntry(loadedChange);
        assertEquals(AuditEntry.ChangeType.MONGOCK_EXECUTION, entry.getType());
    }

    private AuditEntry buildAuditEntry(AbstractLoadedChange loadedChange) {
        ExecutionContext executionContext = new ExecutionContext("exec-1", "localhost", Collections.emptyMap());

        ExecutableChange executableChange = mock(ExecutableChange.class);
        when(executableChange.getApplyMethodName()).thenReturn("apply");
        when(executableChange.getStageName()).thenReturn("stage-1");

        StartStep startStep = new StartStep(executableChange);
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .setStartStep(startStep)
                .setAppliedAt(LocalDateTime.now())
                .build();

        ExecutionAuditContextBundle bundle = new ExecutionAuditContextBundle(
                loadedChange,
                executionContext,
                runtimeContext,
                AuditTxType.NON_TX,
                "target-system-1"
        );

        return bundle.toAuditEntry();
    }

    @SuppressWarnings("unchecked")
    private static CodeLoadedChange mockCodeLoadedChange(String id, boolean legacy) {
        CodeLoadedChange change = mock(CodeLoadedChange.class);
        when(change.getId()).thenReturn(id);
        when(change.getAuthor()).thenReturn("test-author");
        when(change.getSource()).thenReturn("TestChangeClass");
        when(change.isSystem()).thenReturn(false);
        when(change.isLegacy()).thenReturn(legacy);
        when(change.getOrder()).thenReturn(Optional.of("001"));
        when(change.getRecovery()).thenReturn(RecoveryDescriptor.getDefault());
        when(change.isTransactional()).thenReturn(true);
        return change;
    }

    @SuppressWarnings("unchecked")
    private static SimpleTemplateLoadedChange mockTemplateLoadedChange(String id) {
        SimpleTemplateLoadedChange change = mock(SimpleTemplateLoadedChange.class);
        when(change.getId()).thenReturn(id);
        when(change.getAuthor()).thenReturn("test-author");
        when(change.getSource()).thenReturn("template-change.yaml");
        when(change.isSystem()).thenReturn(false);
        when(change.isLegacy()).thenReturn(false);
        when(change.getOrder()).thenReturn(Optional.of("002"));
        when(change.getRecovery()).thenReturn(RecoveryDescriptor.getDefault());
        when(change.isTransactional()).thenReturn(true);
        return change;
    }
}
