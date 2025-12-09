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

import io.flamingock.internal.core.store.AuditStore;
import io.flamingock.support.domain.AuditEntryAssertions;
import io.flamingock.support.domain.AuditEntryExpectation;
import io.flamingock.support.validation.SimpleValidator;
import io.flamingock.support.validation.error.ValidationResult;
import io.flamingock.internal.common.core.audit.AuditEntry;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Strict sequence validator: verifies actual audit entries exactly match the provided expectations.
 */
public class AuditSequenceStrictValidator implements SimpleValidator {

    private static final String VALIDATOR_NAME = "Audit Sequence (Strict)";

    private final AuditStore<?> auditStore;
    private final List<AuditEntryExpectation> expectations;


    public AuditSequenceStrictValidator(AuditStore<?> auditStore, AuditEntryExpectation... expectations) {
        this.auditStore = auditStore;
        this.expectations = Arrays.asList(expectations);
    }

    @Override
    public ValidationResult validate() {

        List<AuditEntry> actualEntries = auditStore.getPersistence().getAuditHistory();
        List<AuditEntry> sortedActual = actualEntries.stream()
                .sorted()
                .collect(Collectors.toList());

        if (sortedActual.size() != expectations.size()) {
            return ValidationResult.failure(String.format(
                    "%s: Expected %d audit entries but found %d. Expected: %s, Actual: %s",
                    VALIDATOR_NAME,
                    expectations.size(),
                    sortedActual.size(),
                    formatExpectedSequence(expectations),
                    formatActualSequence(sortedActual)
            ));
        }

        for (int i = 0; i < expectations.size(); i++) {
            AuditEntry actual = sortedActual.get(i);
            AuditEntryExpectation expected = expectations.get(i);

            try {
                AuditEntryAssertions.assertAuditEntry(actual, expected);
            } catch (AssertionError e) {
                return ValidationResult.failure(String.format(
                        "%s: Audit entry mismatch at position %d: %s. Full expected sequence: %s, Full actual sequence: %s",
                        VALIDATOR_NAME,
                        i,
                        e.getMessage(),
                        formatExpectedSequence(expectations),
                        formatActualSequence(sortedActual)
                ));
            }
        }

        return ValidationResult.success(VALIDATOR_NAME);
    }

    private String formatExpectedSequence(List<AuditEntryExpectation> expectedAudits) {
        if (expectedAudits.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < expectedAudits.size(); i++) {
            if (i > 0) sb.append(", ");
            AuditEntryExpectation exp = expectedAudits.get(i);
            sb.append(String.format("(%s, %s)", exp.getExpectedChangeId(), exp.getExpectedState()));
        }
        sb.append("]");
        return sb.toString();
    }

    private String formatActualSequence(List<AuditEntry> actualEntries) {
        if (actualEntries.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < actualEntries.size(); i++) {
            if (i > 0) sb.append(", ");
            AuditEntry entry = actualEntries.get(i);
            sb.append(String.format("(%s, %s)", entry.getTaskId(), entry.getState()));
        }
        sb.append("]");
        return sb.toString();
    }
}
