/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.importer;

import io.flamingock.internal.common.core.audit.AuditWriter;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.pipeline.PipelineDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ImporterExecutorTest {

    @Test
    void SHOULD_throwFlamingockException_WHEN_auditEntriesEmpty_IF_defaultImportConfiguration() {
        // Given
        ImporterAdapter importerAdapter = mock(ImporterAdapter.class);
        ImportConfiguration importConfiguration = new ImportConfiguration();
        AuditWriter auditWriter = mock(AuditWriter.class);
        PipelineDescriptor pipelineDescriptor = mock(PipelineDescriptor.class);
        
        when(importerAdapter.getAuditHistory()).thenReturn(Collections.emptyList());
        
        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, 
            () -> ImporterExecutor.runImport(importerAdapter, importConfiguration, auditWriter, pipelineDescriptor));
        
        assertTrue(exception.getMessage().contains("No audit entries found when importing from 'mongockChangeLog'"));
        assertTrue(exception.getMessage().contains("Set 'failOnEmptyOrigin=false'"));
    }

    @Test
    void SHOULD_notThrowException_WHEN_auditEntriesEmpty_IF_failOnEmptyOriginFalse() {
        // Given
        ImporterAdapter importerAdapter = mock(ImporterAdapter.class);
        ImportConfiguration importConfiguration = new ImportConfiguration();
        importConfiguration.setFailOnEmptyOrigin(false);
        AuditWriter auditWriter = mock(AuditWriter.class);
        PipelineDescriptor pipelineDescriptor = mock(PipelineDescriptor.class);
        
        when(importerAdapter.getAuditHistory()).thenReturn(Collections.emptyList());
        
        // When & Then
        assertDoesNotThrow(() -> ImporterExecutor.runImport(importerAdapter, importConfiguration, auditWriter, pipelineDescriptor));
    }
}
