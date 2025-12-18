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
import io.flamingock.support.validation.ValidatorArgs;
import io.flamingock.support.validation.error.*;

import java.util.ArrayList;
import java.util.Collections;
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
    private static final List<AuditEntry.Status> EXCLUDED_STATES = Collections.singletonList(
            AuditEntry.Status.STARTED
    );

    public AuditSequenceStrictValidator(AuditReader auditReader, List<AuditEntryDefinition> definitions) {
        this.auditReader = auditReader;
        this.expectations = definitions != null
                ? definitions.stream()
                .map(AuditEntryExpectation::new)
                .collect(Collectors.toList())
                : new ArrayList<>();

       this.actualEntries = auditReader.getAuditHistory().stream()
                .filter(entry -> !EXCLUDED_STATES.contains(entry.getState()))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Internal constructor for direct list initialization (used by tests).
     */
    AuditSequenceStrictValidator(List<AuditEntryDefinition> expectedDefinitions, List<AuditEntry> actualEntries, AuditReader auditReader) {
        this.expectations = expectedDefinitions != null
                ? expectedDefinitions.stream()
                .map(AuditEntryExpectation::new)
                .collect(Collectors.toList())
                : new ArrayList<>();
        this.auditReader = auditReader;
        this.actualEntries = actualEntries != null ? actualEntries : new ArrayList<>();
    }

    @Override
    public ValidationResult validate() {
        List<ValidationError> allErrors = new ArrayList<>();

        if (expectations.size() != actualEntries.size()) {
            allErrors.add(new CountMismatchError(getExpectedChangeIds(), getActualChangeIds()));
        }

        allErrors.addAll(getValidationErrors(expectations, actualEntries));

        return allErrors.isEmpty()
                ? ValidationResult.success(VALIDATOR_NAME)
                : ValidationResult.failure(VALIDATOR_NAME, allErrors.toArray(new ValidationError[0]));
    }

    private static List<ValidationError> getValidationErrors(List<AuditEntryExpectation> expectedEntries, List<AuditEntry> actualEntries) {
        List<ValidationError> allErrors = new ArrayList<>();
        if (expectedEntries.isEmpty()) {
            return allErrors;
        }
        int actualSize = actualEntries.size();
        int limit = Math.max(expectedEntries.size(), actualSize);

        for (int i = 0; i < limit; i++) {
            AuditEntryExpectation expected = i < expectedEntries.size() ? expectedEntries.get(i) : null;
            AuditEntry actual = i < actualEntries.size() ? actualEntries.get(i) : null;
            if( expected != null && actual != null) {
                allErrors.addAll(expected.compareWith(actual));
            } else if( expected != null) {
                AuditEntryDefinition def = expected.getDefinition();
                allErrors.add(new MissingEntryError(i, def.getChangeId(), def.getState()));
            } else {
                assert actual != null;
                allErrors.add(new UnexpectedEntryError(i, actual.getTaskId(), actual.getState()));
            }

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

    public static class Args implements ValidatorArgs {
        private final List<AuditEntryDefinition> expectations;

        public Args(List<AuditEntryDefinition> expectations) {
            this.expectations = expectations;
        }

        public List<AuditEntryDefinition> getExpectations() {
            return expectations;
        }
    }
}
