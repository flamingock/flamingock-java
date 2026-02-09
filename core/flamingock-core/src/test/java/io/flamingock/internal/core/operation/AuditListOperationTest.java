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
        // Given - default args (no history flag) uses snapshot
        when(persistence.getAuditSnapshot()).thenReturn(Collections.emptyList());
        AuditListArgs args = new AuditListArgs();

        // When
        AuditListResult result = operation.execute(args);

        // Then
        assertNotNull(result);
        assertTrue(result.getAuditEntries().isEmpty());
    }

    @Test
    @DisplayName("Should return audit entries from snapshot when no history flag")
    void shouldReturnAuditEntriesFromSnapshotWhenNoHistoryFlag() {
        // Given
        AuditEntry entry1 = createAuditEntry("exec-1", "task-1");
        AuditEntry entry2 = createAuditEntry("exec-2", "task-2");
        List<AuditEntry> entries = Arrays.asList(entry1, entry2);
        when(persistence.getAuditSnapshot()).thenReturn(entries);
        AuditListArgs args = new AuditListArgs();

        // When
        AuditListResult result = operation.execute(args);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getAuditEntries().size());
        assertEquals(entries, result.getAuditEntries());
    }

    @Test
    @DisplayName("Should delegate to AuditPersistence getAuditSnapshot by default")
    void shouldDelegateToAuditPersistenceGetAuditSnapshotByDefault() {
        // Given
        when(persistence.getAuditSnapshot()).thenReturn(Collections.emptyList());
        AuditListArgs args = new AuditListArgs();

        // When
        operation.execute(args);

        // Then
        verify(persistence, times(1)).getAuditSnapshot();
        verify(persistence, never()).getAuditHistory();
    }

    @Test
    @DisplayName("Should delegate to AuditPersistence getAuditHistory when history flag is set")
    void shouldDelegateToAuditPersistenceGetAuditHistoryWhenHistoryFlagIsSet() {
        // Given
        when(persistence.getAuditHistory()).thenReturn(Collections.emptyList());
        AuditListArgs args = new AuditListArgs(true, null, false);

        // When
        operation.execute(args);

        // Then
        verify(persistence, times(1)).getAuditHistory();
        verify(persistence, never()).getAuditSnapshot();
    }

    @Test
    @DisplayName("Should filter entries by since date")
    void shouldFilterEntriesBySinceDate() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime twoDaysAgo = now.minusDays(2);

        AuditEntry oldEntry = createAuditEntryWithTime("exec-1", "task-1", twoDaysAgo);
        AuditEntry newEntry = createAuditEntryWithTime("exec-2", "task-2", now);
        List<AuditEntry> entries = Arrays.asList(oldEntry, newEntry);
        when(persistence.getAuditSnapshot()).thenReturn(entries);

        AuditListArgs args = new AuditListArgs(false, yesterday, false);

        // When
        AuditListResult result = operation.execute(args);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getAuditEntries().size());
        assertEquals("task-2", result.getAuditEntries().get(0).getTaskId());
    }

    @Test
    @DisplayName("Should set extended flag in result")
    void shouldSetExtendedFlagInResult() {
        // Given
        when(persistence.getAuditSnapshot()).thenReturn(Collections.emptyList());
        AuditListArgs args = new AuditListArgs(false, null, true);

        // When
        AuditListResult result = operation.execute(args);

        // Then
        assertTrue(result.isExtended());
    }

    private AuditEntry createAuditEntry(String executionId, String taskId) {
        return createAuditEntryWithTime(executionId, taskId, LocalDateTime.now());
    }

    private AuditEntry createAuditEntryWithTime(String executionId, String taskId, LocalDateTime time) {
        return new AuditEntry(
                executionId,
                "stage-1",
                taskId,
                "test-author",
                time,
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
