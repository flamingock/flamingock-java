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
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.support.domain.AuditEntryDefinition;
import io.flamingock.support.validation.SimpleValidator;
import io.flamingock.support.validation.error.*;

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

    private final AuditReader auditReader;
    private final List<AuditEntryExpectation> expectations;
    private final List<AuditEntry> actualEntries;

    public AuditSequenceStrictValidator(AuditReader auditReader, AuditEntryDefinition... definitions) {
        this.auditReader = auditReader;
        this.expectations = Arrays.stream(definitions)
                .map(AuditEntryExpectation::new)
                .collect(Collectors.toList());
        this.actualEntries = auditReader.getAuditHistory();
    }

    /**
     * Internal constructor for direct list initialization (used by tests).
     */
    AuditSequenceStrictValidator(List<AuditEntryDefinition> expectedDefinitions, List<AuditEntry> actualEntries, AuditReader auditReader) {
        this.expectations = expectedDefinitions.stream()
                .map(AuditEntryExpectation::new)
                .collect(Collectors.toList());
        this.auditReader = auditReader;
        this.actualEntries = actualEntries != null ? actualEntries : new ArrayList<>();
    }

    @Override
    public ValidationResult validate() {
        List<ValidationError> allErrors = new ArrayList<>();

        int expectedSize = expectations.size();
        int actualSize = actualEntries.size();

        if (expectedSize != actualSize) {
            allErrors.add(new CountMismatchError(getExpectedChangeIds(), getActualChangeIds()));
        }

        allErrors.addAll(getValidationErrors(expectations, actualEntries));

        if (expectedSize > actualSize) {
            for (int i = actualSize; i < expectedSize; i++) {
                AuditEntryDefinition def = expectations.get(i).getDefinition();
                allErrors.add(new MissingEntryError(i, def.getChangeId(), def.getState()));
            }
        }

        if (expectedSize < actualSize) {
            for (int i = expectedSize; i < actualSize; i++) {
                AuditEntry actual = actualEntries.get(i);
                allErrors.add(new UnexpectedEntryError(i, actual.getTaskId(), actual.getState()));
            }
        }

        if (allErrors.isEmpty()) {
            return ValidationResult.success(VALIDATOR_NAME);
        }

        return ValidationResult.failure(VALIDATOR_NAME, allErrors.toArray(new ValidationError[0]));
    }

    private static List<ValidationError> getValidationErrors(List<AuditEntryExpectation> expectedEntries, List<AuditEntry> actualEntries) {
        List<ValidationError> allErrors = new ArrayList<>();
        if (expectedEntries == null || expectedEntries.isEmpty()) {
            return allErrors;
        }
        int actualSize = actualEntries != null ? actualEntries.size() : 0;
        int limit = Math.min(expectedEntries.size(), actualSize);
        for (int i = 0; i < limit; i++) {
            AuditEntryExpectation expected = expectedEntries.get(i);
            AuditEntry actual = actualEntries.get(i);
            List<FieldMismatchError> entryErrors = expected.compareWith(actual);
            allErrors.addAll(entryErrors);
        }
        return allErrors;
    }

    private List<String> getExpectedChangeIds() {
        return expectations.stream()
                .map(exp -> exp.getDefinition().getChangeId())
                .collect(Collectors.toList());
    }

    private List<String> getActualChangeIds() {
        return actualEntries.stream()
                .map(AuditEntry::getTaskId)
                .collect(Collectors.toList());
    }
}
