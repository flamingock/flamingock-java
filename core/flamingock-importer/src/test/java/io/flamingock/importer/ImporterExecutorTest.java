package io.flamingock.importer.mongodb;

import io.flamingock.importer.ImportConfiguration;
import io.flamingock.importer.ImporterAdapter;
import io.flamingock.importer.ImporterExecutor;
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
        
        when(importerAdapter.getAuditEntries()).thenReturn(Collections.emptyList());
        
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
        
        when(importerAdapter.getAuditEntries()).thenReturn(Collections.emptyList());
        
        // When & Then
        assertDoesNotThrow(() -> ImporterExecutor.runImport(importerAdapter, importConfiguration, auditWriter, pipelineDescriptor));
    }
}
