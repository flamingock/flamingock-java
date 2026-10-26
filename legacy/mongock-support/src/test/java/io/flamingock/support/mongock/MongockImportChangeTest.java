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
package io.flamingock.support.mongock;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.internal.core.external.targets.operations.TransactionalTargetSystemOps;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static io.flamingock.internal.common.core.audit.AuditReaderType.MONGOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongockImportChangeTest {
    @Test
    void failedAppendIncludesEntryIdentityAndOriginalCause() {
        TargetSystemManager manager = mock(TargetSystemManager.class);
        TransactionalTargetSystemOps ops = mock(TransactionalTargetSystemOps.class);
        AuditHistoryReader reader = mock(AuditHistoryReader.class);
        PipelineDescriptor pipeline = mock(PipelineDescriptor.class);
        CommunityAuditPersistence persistence = mock(CommunityAuditPersistence.class);
        AuditPersistenceFactory<CommunityAuditPersistence> factory = stageId -> {
            assertEquals("flamingock-legacy-stage", stageId);
            return persistence;
        };
        AuditEntry entry = mock(AuditEntry.class);
        RuntimeException cause = new RuntimeException("write failed");
        when(manager.getTargetSystem("mongo")).thenReturn(ops);
        when(ops.getAuditAuditReader(MONGOCK)).thenReturn(Optional.of(reader));
        when(reader.getAuditHistory()).thenReturn(Collections.singletonList(entry));
        when(entry.getChangeId()).thenReturn("change-1");
        when(entry.copyWithNewIdAndStageId("change-1", "stage-1")).thenReturn(entry);
        when(pipeline.getStageByChange("change-1")).thenReturn(Optional.of("stage-1"));
        when(persistence.append(any(AuditEntry.class))).thenReturn(new Result.Error(cause));

        FlamingockException failure = assertThrows(FlamingockException.class,
                () -> new MongockImportChange().importHistory("mongo", manager, factory, pipeline, null, null, null));
        assertSame(cause, failure.getCause());
        assertEquals("Error importing audit entry with changeId[change-1] into stage[stage-1] for targetSystem[mongo].",
                failure.getMessage());
    }
}
