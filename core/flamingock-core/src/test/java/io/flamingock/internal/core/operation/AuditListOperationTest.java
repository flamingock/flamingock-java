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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditListOperationTest {

    @Mock
    private AuditPersistence persistence;

    private AuditListOperation operation;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        operation = new AuditListOperation(persistence);
    }

    @Test
    @DisplayName("Should return empty list when no audit entries exist")
    void shouldReturnEmptyListWhenNoAuditEntriesExist() {
        // Given
        when(persistence.getAuditHistory()).thenReturn(Collections.emptyList());
        AuditListArgs args = new AuditListArgs();

        // When
        AuditListResult result = operation.execute(args);

        // Then
        assertNotNull(result);
        assertTrue(result.getAuditEntries().isEmpty());
    }

    @Test
    @DisplayName("Should return audit entries when they exist")
    void shouldReturnAuditEntriesWhenTheyExist() {
        // Given
        AuditEntry entry1 = createAuditEntry("exec-1", "task-1");
        AuditEntry entry2 = createAuditEntry("exec-2", "task-2");
        List<AuditEntry> entries = Arrays.asList(entry1, entry2);
        when(persistence.getAuditHistory()).thenReturn(entries);
        AuditListArgs args = new AuditListArgs();

        // When
        AuditListResult result = operation.execute(args);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getAuditEntries().size());
        assertEquals(entries, result.getAuditEntries());
    }

    @Test
    @DisplayName("Should delegate to AuditPersistence getAuditHistory")
    void shouldDelegateToAuditPersistenceGetAuditHistory() {
        // Given
        when(persistence.getAuditHistory()).thenReturn(Collections.emptyList());
        AuditListArgs args = new AuditListArgs();

        // When
        operation.execute(args);

        // Then
        verify(persistence, times(1)).getAuditHistory();
        verifyNoMoreInteractions(persistence);
    }

    private AuditEntry createAuditEntry(String executionId, String taskId) {
        return new AuditEntry(
                executionId,
                "stage-1",
                taskId,
                "test-author",
                LocalDateTime.now(),
                AuditEntry.Status.APPLIED,
                AuditEntry.ChangeType.STANDARD_CODE,
                "TestClass",
                "apply",
                "TestClass.java",
                100L,
                "localhost",
                null,
                false,
                null
        );
    }
}
