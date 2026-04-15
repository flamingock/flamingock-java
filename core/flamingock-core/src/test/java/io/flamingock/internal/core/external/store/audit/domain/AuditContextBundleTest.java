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

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.change.executable.CodeExecutableChange;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.CodeLoadedChange;
import io.flamingock.internal.core.change.loaded.CodeLoadedChangeBuilder;
import io.flamingock.internal.core.change.navigation.step.StartStep;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditContextBundleTest {

    @Change(id = "audit-source-file-test", author = "aperezdieppa")
    public static class _001__AuditSourceFileChange {
        @Apply
        public void apply() {
            // no-op
        }
    }

    @Test
    void shouldPopulateAuditEntrySourceFileFromLoadedChange() {
        CodeLoadedChange loadedChange = buildLoadedChange(_001__AuditSourceFileChange.class);
        CodeExecutableChange<CodeLoadedChange> executableChange = new CodeExecutableChange<>(
                "test-stage",
                loadedChange,
                ChangeAction.APPLY,
                loadedChange.getApplyMethod(),
                loadedChange.getRollbackMethod().orElse(null)
        );
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .setStartStep(new StartStep(executableChange))
                .setAppliedAt(LocalDateTime.now())
                .build();
        ExecutionContext executionContext = new ExecutionContext("execution-id", "test-host", Collections.emptyMap());

        AuditEntry auditEntry = new ExecutionAuditContextBundle(
                loadedChange,
                executionContext,
                runtimeContext,
                AuditTxType.NON_TX,
                "target-system")
                .toAuditEntry();

        assertNull(auditEntry.getSourceFile());
    }

    @Test
    void shouldPopulateAuditEntrySourceFileForTemplateBasedChanges() {
        AbstractLoadedChange loadedChange = Mockito.mock(AbstractLoadedChange.class);
        Mockito.when(loadedChange.getId()).thenReturn("template-change");
        Mockito.when(loadedChange.getAuthor()).thenReturn("author");
        Mockito.when(loadedChange.getSource()).thenReturn("io.flamingock.TemplateChange");
        Mockito.when(loadedChange.getSourceFile()).thenReturn("_0001__template-change.yaml");
        Mockito.when(loadedChange.isSystem()).thenReturn(false);
        Mockito.when(loadedChange.getOrder()).thenReturn(java.util.Optional.of("0001"));
        Mockito.when(loadedChange.getRecovery()).thenReturn(RecoveryDescriptor.getDefault());
        Mockito.when(loadedChange.isTransactional()).thenReturn(true);

        ExecutableChange executableChange = Mockito.mock(ExecutableChange.class);
        Mockito.when(executableChange.getApplyMethodName()).thenReturn("apply");
        Mockito.when(executableChange.getStageName()).thenReturn("test-stage");

        RuntimeContext runtimeContext = RuntimeContext.builder()
                .setStartStep(new StartStep(executableChange))
                .setAppliedAt(LocalDateTime.now())
                .build();
        ExecutionContext executionContext = new ExecutionContext("execution-id", "test-host", Collections.emptyMap());

        AuditEntry auditEntry = new ExecutionAuditContextBundle(
                loadedChange,
                executionContext,
                runtimeContext,
                AuditTxType.NON_TX,
                "target-system")
                .toAuditEntry();

        assertEquals("_0001__template-change.yaml", auditEntry.getSourceFile());
    }

    private CodeLoadedChange buildLoadedChange(Class<?> changeClass) {
        try {
            Method factoryMethod = CodeLoadedChangeBuilder.class.getDeclaredMethod("getInstanceFromClass", Class.class);
            factoryMethod.setAccessible(true);
            Object builder = factoryMethod.invoke(null, changeClass);
            return ((CodeLoadedChangeBuilder) builder).build();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
