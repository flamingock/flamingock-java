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
package io.flamingock.support.validation.impl;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.support.domain.AuditEntryDefinition;
import io.flamingock.support.validation.error.CountMismatchError;
import io.flamingock.support.validation.error.FieldMismatchError;
import io.flamingock.support.validation.error.MissingEntryError;
import io.flamingock.support.validation.error.UnexpectedEntryError;
import io.flamingock.support.validation.error.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.flamingock.internal.common.core.audit.AuditEntry.ExecutionType.EXECUTION;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.APPLIED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.FAILED;
import static io.flamingock.support.domain.AuditEntryDefinition.APPLIED;
import static io.flamingock.support.domain.AuditEntryDefinition.FAILED;
import static org.junit.jupiter.api.Assertions.*;

class AuditSequenceStrictValidatorTest {

    private List<AuditEntry> actualEntries;

    @BeforeEach
    void setUp() {
        actualEntries = Arrays.asList(
                createAuditEntry("change-1", APPLIED),
                createAuditEntry("change-2", APPLIED),
                createAuditEntry("change-3", FAILED)
        );
    }

    @Test
    @DisplayName("AuditSequenceStrictValidator passes when entries match exactly")
    void shouldPassValidation_whenEntriesMatchExactly() {
        List<AuditEntryDefinition> expectedDefinitions = Arrays.asList(
                APPLIED("change-1"),
                APPLIED("change-2"),
                FAILED("change-3")
        );

        AuditSequenceStrictValidator validator = new AuditSequenceStrictValidator(expectedDefinitions, actualEntries, null);
        ValidationResult result = validator.validate();

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("AuditSequenceStrictValidator fails when counts mismatch")
    void shouldFailValidation_whenCountMismatch() {
        List<AuditEntryDefinition> expectedDefinitions = Arrays.asList(
                APPLIED("change-1"),
                APPLIED("change-2")
        );

        AuditSequenceStrictValidator validator = new AuditSequenceStrictValidator(expectedDefinitions, actualEntries, null);
        ValidationResult result = validator.validate();

        assertFalse(result.isSuccess());
        assertEquals(2, result.getErrors().size());
        assertTrue(result.getErrors().stream().anyMatch(e -> e instanceof CountMismatchError));
        assertTrue(result.getErrors().stream().anyMatch(e -> e instanceof UnexpectedEntryError));
    }


    @Test
    @DisplayName("AuditSequenceStrictValidator fails when a field status mismatches")
    void shouldFailValidation_whenStatusMismatch() {
        List<AuditEntryDefinition> expectedDefinitions = Arrays.asList(
                APPLIED("change-1"),
                APPLIED("change-2"),
                APPLIED("change-3")  // Expected APPLIED but actual is FAILED
        );

        AuditSequenceStrictValidator validator = new AuditSequenceStrictValidator(expectedDefinitions, actualEntries, null);
        ValidationResult result = validator.validate();

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertInstanceOf(FieldMismatchError.class, result.getErrors().get(0));

        FieldMismatchError error = (FieldMismatchError) result.getErrors().get(0);
        assertEquals("status", error.getFieldName());
    }

    @Test
    @DisplayName("AuditSequenceStrictValidator fails when changeId mismatches")
    void shouldFailValidation_whenChangeIdMismatch() {
        List<AuditEntryDefinition> expectedDefinitions = Arrays.asList(
                APPLIED("change-1"),
                APPLIED("wrong-id"),  // Mismatch
                AuditEntryDefinition.FAILED("change-3")
        );

        AuditSequenceStrictValidator validator = new AuditSequenceStrictValidator(expectedDefinitions, actualEntries, null);
        ValidationResult result = validator.validate();

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertInstanceOf(FieldMismatchError.class, result.getErrors().get(0));

        FieldMismatchError error = (FieldMismatchError) result.getErrors().get(0);
        assertEquals("changeId", error.getFieldName());
    }

    @Test
    @DisplayName("AuditSequenceStrictValidator fails when an expected entry is missing (count + missing entry error)")
    void shouldFailValidation_whenMissingEntry() {
        List<AuditEntry> actualEntriesSubset = Arrays.asList(
                createAuditEntry("change-1", APPLIED),
                createAuditEntry("change-2", APPLIED)
        );

        List<AuditEntryDefinition> expectedDefinitions = Arrays.asList(
                APPLIED("change-1"),
                APPLIED("change-2"),
                AuditEntryDefinition.FAILED("change-3")
        );

        AuditSequenceStrictValidator validator = new AuditSequenceStrictValidator(expectedDefinitions, actualEntriesSubset, null);
        ValidationResult result = validator.validate();

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e instanceof CountMismatchError));
        assertTrue(result.getErrors().stream().anyMatch(e -> e instanceof MissingEntryError));
        MissingEntryError missing = (MissingEntryError) result.getErrors().stream()
                .filter(e -> e instanceof MissingEntryError)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a MissingEntryError but none was found"));
        assertEquals(2, missing.getExpectedIndex());
        assertEquals("change-3", missing.getExpectedChangeId());
    }

    @Test
    @DisplayName("AuditSequenceStrictValidator fails when there is an unexpected actual entry (count + unexpected entry error)")
    void shouldFailValidation_whenUnexpectedEntry() {
        List<AuditEntry> actualEntriesExtra = Arrays.asList(
                createAuditEntry("change-1", APPLIED),
                createAuditEntry("change-2", APPLIED),
                createAuditEntry("change-3", FAILED),
                createAuditEntry("change-4", APPLIED)
        );

        List<AuditEntryDefinition> expectedDefinitions = Arrays.asList(
                APPLIED("change-1"),
                APPLIED("change-2"),
                AuditEntryDefinition.FAILED("change-3")
        );

        AuditSequenceStrictValidator validator = new AuditSequenceStrictValidator(expectedDefinitions, actualEntriesExtra, null);
        ValidationResult result = validator.validate();

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().stream().anyMatch(e -> e instanceof CountMismatchError));
        assertTrue(result.getErrors().stream().anyMatch(e -> e instanceof UnexpectedEntryError));
        UnexpectedEntryError unexpected = (UnexpectedEntryError) result.getErrors().stream()
                .filter(e -> e instanceof UnexpectedEntryError)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected an UnexpectedEntryError but none was found"));
        assertEquals(3, unexpected.getIndex());
        assertEquals("change-4", unexpected.getActualChangeId());
    }

    @Test
    @DisplayName("AuditSequenceStrictValidator passes when optional fields match")
    void shouldPassValidation_whenOptionalFieldsMatch() {
        AuditEntry actualWithOptionalFields = new AuditEntry(
                "exec-1",
                "stage-1",
                "change-1",
                "author",
                LocalDateTime.now(),
                APPLIED,
                EXECUTION,
                "com.example.Change",
                "apply",
                100L,
                "host",
                null,
                false,
                null,
                null,
                "target-1",
                "1",
                null,
                true
        );

        AuditEntryDefinition expectedWithOptionalFields = APPLIED("change-1")
                .withAuthor("author")
                .withClassName("com.example.Change")
                .withMethodName("apply")
                .withTargetSystemId("target-1")
                .withOrder("1")
                .withTransactional(true);

        AuditSequenceStrictValidator validator = new AuditSequenceStrictValidator(
                Collections.singletonList(expectedWithOptionalFields),
                Collections.singletonList(actualWithOptionalFields),
                null
        );

        ValidationResult result = validator.validate();

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("AuditSequenceStrictValidator fails when an optional field mismatches")
    void shouldFailValidation_whenOptionalFieldMismatch() {
        AuditEntry actualEntry = new AuditEntry(
                "exec-1",
                "stage-1",
                "change-1",
                "author",
                LocalDateTime.now(),
                APPLIED,
                EXECUTION,
                "com.example.Change",
                "apply",
                100L,
                "host",
                null,
                false,
                null,
                null,
                "target-1",
                null,
                null,
                null
        );

        AuditEntryDefinition expectedWithDifferentOptional = APPLIED("change-1")
                .withTargetSystemId("different-target");

        AuditSequenceStrictValidator validator = new AuditSequenceStrictValidator(
                Collections.singletonList(expectedWithDifferentOptional),
                Collections.singletonList(actualEntry),
                null
        );

        ValidationResult result = validator.validate();

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertInstanceOf(FieldMismatchError.class, result.getErrors().get(0));

        FieldMismatchError error = (FieldMismatchError) result.getErrors().get(0);
        assertEquals("targetSystemId", error.getFieldName());
    }

    private AuditEntry createAuditEntry(String changeId, AuditEntry.Status status) {
        return new AuditEntry(
                "exec-id",
                "stage-id",
                changeId,
                "test-author",
                LocalDateTime.now(),
                status,
                EXECUTION,
                "com.example.TestChange",
                "apply",
                100L,
                "localhost",
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
