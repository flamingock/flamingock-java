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
package io.flamingock.internal.core.store.audit.recovery;

import io.flamingock.internal.core.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.core.recovery.RecoveryIssue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManualInterventionRequiredExceptionTest {

    @Test
    void shouldCreateExceptionForSingleChange() {
        // Given
        RecoveryIssue recoveryIssue = new RecoveryIssue("change-001");

        // When
        ManualInterventionRequiredException exception =
            new ManualInterventionRequiredException(recoveryIssue);

        // Then
        assertEquals(1, exception.getConflictingChanges().size());
        assertEquals("change-001", exception.getConflictingChanges().get(0).getChangeId());
        assertEquals("unknown", exception.getStageName());
    }

    @Test
    void shouldCreateExceptionForMultipleChanges() {
        // Given
        List<RecoveryIssue> changes = Arrays.asList(
            new RecoveryIssue("change-001"),
            new RecoveryIssue("change-002")
        );
        String stageName = "migration-stage";

        // When
        ManualInterventionRequiredException exception = 
            new ManualInterventionRequiredException(changes, stageName);

        // Then
        assertEquals(2, exception.getConflictingChanges().size());
        assertEquals("migration-stage", exception.getStageName());
        assertEquals("change-001, change-002", exception.getConflictingSummary());
    }

    @Test
    void shouldIncludeDetailedMessageForManualIntervention() {
        // Given
        RecoveryIssue recoveryIssue = new RecoveryIssue("change-001");
        
        // When
        ManualInterventionRequiredException exception = 
            new ManualInterventionRequiredException(recoveryIssue);
        String message = exception.getMessage();

        // Then
        assertAll("Message should contain all required information",
            () -> assertTrue(message.contains("MANUAL INTERVENTION REQUIRED")),
            () -> assertTrue(message.contains("change-001")),
            () -> assertTrue(message.contains("Change requires manual intervention")),
            () -> assertTrue(message.contains("REQUIRED ACTIONS")),
            () -> assertTrue(message.contains("flamingock mark change-001 SUCCESS|FAILED")),
            () -> assertTrue(message.contains("verify the state of your target system"))
        );
    }

    @Test
    void shouldIncludeDetailedMessageForRecoveryIssue() {
        // Given
        RecoveryIssue recoveryIssue = new RecoveryIssue("change-002");

        // When
        ManualInterventionRequiredException exception = 
            new ManualInterventionRequiredException(recoveryIssue);
        String message = exception.getMessage();

        // Then
        assertAll("Message should contain recovery issue details",
            () -> assertTrue(message.contains("change-002")),
            () -> assertTrue(message.contains("Change requires manual intervention"))
        );
    }

    @Test
    void shouldIncludeMultipleChangesInMessage() {
        // Given
        List<RecoveryIssue> changes = Arrays.asList(
            new RecoveryIssue("change-001"),
            new RecoveryIssue("change-002"),
            new RecoveryIssue("change-003")
        );
        String stageName = "complex-stage";

        // When
        ManualInterventionRequiredException exception = 
            new ManualInterventionRequiredException(changes, stageName);
        String message = exception.getMessage();

        // Then
        assertAll("Message should contain all changes",
            () -> assertTrue(message.contains("complex-stage")),
            () -> assertTrue(message.contains("change-001")),
            () -> assertTrue(message.contains("change-002")), 
            () -> assertTrue(message.contains("change-003")),
            () -> assertTrue(message.contains("flamingock mark change-001 SUCCESS|FAILED")),
            () -> assertTrue(message.contains("flamingock mark change-002 SUCCESS|FAILED")),
            () -> assertTrue(message.contains("flamingock mark change-003 SUCCESS|FAILED"))
        );
    }

    @Test
    void shouldProvideActionableUserGuidance() {
        // Given
        RecoveryIssue recoveryIssue = new RecoveryIssue("user-change");

        // When
        ManualInterventionRequiredException exception = 
            new ManualInterventionRequiredException(recoveryIssue);
        String message = exception.getMessage();

        // Then
        assertAll("Message should provide clear user guidance",
            () -> assertTrue(message.contains("1. Manually verify the state of your target system")),
            () -> assertTrue(message.contains("2. Determine if each change was successfully applied")),
            () -> assertTrue(message.contains("3. Use the Flamingock CLI to mark each change")),
            () -> assertTrue(message.contains("4. Re-run Flamingock after resolving")),
            () -> assertTrue(message.contains("This safety mechanism prevents data corruption"))
        );
    }

    @Test
    void shouldProvideCLICommandExamples() {
        // Given
        List<RecoveryIssue> changes = Arrays.asList(
            new RecoveryIssue("db-migration-001"),
            new RecoveryIssue("config-update-002")
        );

        // When  
        ManualInterventionRequiredException exception = 
            new ManualInterventionRequiredException(changes, "test-stage");
        String message = exception.getMessage();

        // Then
        assertAll("Message should contain CLI command examples",
            () -> assertTrue(message.contains("CLI Commands:")),
            () -> assertTrue(message.contains("flamingock mark db-migration-001 SUCCESS|FAILED")),
            () -> assertTrue(message.contains("flamingock mark config-update-002 SUCCESS|FAILED"))
        );
    }

    @Test
    void shouldGenerateCorrectConflictingSummary() {
        // Given
        List<RecoveryIssue> changes = Arrays.asList(
            new RecoveryIssue("alpha"),
            new RecoveryIssue("beta"),
            new RecoveryIssue("gamma")
        );

        // When
        ManualInterventionRequiredException exception = 
            new ManualInterventionRequiredException(changes, "test-stage");

        // Then
        String summary = exception.getConflictingSummary();
        assertTrue(summary.contains("alpha"));
        assertTrue(summary.contains("beta"));
        assertTrue(summary.contains("gamma"));
        assertTrue(summary.contains(", "));
    }
}