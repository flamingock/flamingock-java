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
import io.flamingock.internal.core.store.AuditStore;
import io.flamingock.support.domain.AuditEntryDefinition;
import io.flamingock.support.validation.SimpleValidator;
import io.flamingock.support.validation.error.CountMismatchError;
import io.flamingock.support.validation.error.ValidationError;
import io.flamingock.support.validation.error.ValidationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validator that performs strict sequence validation of audit entries.
 *
 * <p>This validator verifies that the actual audit entries match the expected
 * sequence exactly, both in count and in field values. Checking:</p>
 * <ul>
 *   <li>Exact count match between expected and actual entries</li>
 *   <li>Strict field-by-field validation for each entry at each index</li>
 *   <li>Order preservation (expected[0] must match actual[0], etc.)</li>
 * </ul>
 */
public class AuditSequenceStrictValidator implements SimpleValidator {

    private static final String VALIDATOR_NAME = "Audit Sequence (Strict)";

    private final List<AuditEntryExpectation> expectedExpectations;
    private final List<AuditEntry> actualEntries;

    /**
     * Creates a strict sequence validator from an AuditStore and expected definitions.
     *
     * @param auditStore  the audit store to read actual entries from
     * @param definitions the expected audit entry definitions
     */
    public AuditSequenceStrictValidator(AuditStore<?> auditStore, AuditEntryDefinition... definitions) {
        this(Arrays.asList(definitions), auditStore.getPersistence().getAuditHistory());
    }

    /**
     * Internal constructor for direct list initialization (used by tests).
     */
    AuditSequenceStrictValidator(List<AuditEntryDefinition> expectedDefinitions, List<AuditEntry> actualEntries) {
        this.expectedExpectations = expectedDefinitions.stream()
                .map(AuditEntryExpectation::new)
                .collect(Collectors.toList());
        this.actualEntries = actualEntries != null ? actualEntries : new ArrayList<>();
    }

    @Override
    public ValidationResult validate() {
        // Check count
        if (expectedExpectations.size() != actualEntries.size()) {
            return ValidationResult.failure(VALIDATOR_NAME,
                    new CountMismatchError(getExpectedChangeIds(), getActualChangeIds()));
        }

        // Validate each entry in sequence
        List<ValidationError> allErrors = getValidationErrors(expectedExpectations, actualEntries);

        if (allErrors.isEmpty()) {
            return ValidationResult.success(VALIDATOR_NAME);
        }

        return ValidationResult.failure(VALIDATOR_NAME, allErrors.toArray(
                new io.flamingock.support.validation.error.ValidationError[0]));
    }

    private static List<ValidationError> getValidationErrors(List<AuditEntryExpectation> expectedExpectations, List<AuditEntry> actualEntries) {
        List<ValidationError> allErrors = new ArrayList<>();
        for (int i = 0; i < expectedExpectations.size(); i++) {
            AuditEntryExpectation expected = expectedExpectations.get(i);
            AuditEntry actual = actualEntries.get(i);

            List<io.flamingock.support.validation.error.FieldMismatchError> entryErrors = expected.compareWith(actual);
            if (!entryErrors.isEmpty()) {
                allErrors.addAll(entryErrors);
            }
        }
        return allErrors;
    }

    private List<String> getExpectedChangeIds() {
        return expectedExpectations.stream()
                .map(exp -> exp.getDefinition().getChangeId())
                .collect(Collectors.toList());
    }

    private List<String> getActualChangeIds() {
        return actualEntries.stream()
                .map(AuditEntry::getTaskId)
                .collect(Collectors.toList());
    }
}
