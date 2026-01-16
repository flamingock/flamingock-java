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
import io.flamingock.support.stages.ThenStage;
import io.flamingock.support.validation.SimpleValidator;
import io.flamingock.support.validation.ValidatorArgs;
import io.flamingock.support.validation.error.*;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates that the final state sequence of audit entries matches the expected definitions exactly.
 *
 * <p>This validator focuses on the <strong>final state</strong> of each change, filtering out
 * intermediate states like {@code STARTED}. It validates only the states that represent
 * actual outcomes: {@code APPLIED}, {@code FAILED}, {@code ROLLED_BACK}, {@code ROLLBACK_FAILED}.</p>
 *
 * <p><strong>Exact sequence validation:</strong></p>
 * <ul>
 *   <li>The number of actual entries must exactly match the number of expected definitions</li>
 *   <li>Order is preserved: expected[0] must match actual[0], expected[1] must match actual[1], etc.</li>
 * </ul>
 *
 * <p>This is not a "contains" validator - if the audit log has 3 changes, you must
 * provide exactly 3 expected definitions.</p>
 *
 * <p><strong>Field validation:</strong></p>
 * <p>Only fields that are set in the {@link AuditEntryDefinition} are validated. The {@code changeId}
 * and {@code state} are always compared (they are required). Additional fields depend on how the
 * definition is constructed:</p>
 * <ul>
 *   <li>Class-based factory methods (e.g., {@code APPLIED(MyChange.class)}) auto-extract fields
 *       from annotations: author, className, methodName, targetSystemId, recoveryStrategy, order, transactional</li>
 *   <li>String-based factory methods (e.g., {@code APPLIED("change-id")}) only set changeId and state</li>
 *   <li>Use {@code withXxx()} methods to add or override specific fields to validate</li>
 *   <li>Fields that are null in the definition are not compared</li>
 * </ul>
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * testSupport.givenBuilderFromContext()
 *     .whenRun()
 *     .thenExpectAuditFinalStateSequence(
 *         APPLIED(CreateUsersChange.class),  // validates fields from annotations
 *         APPLIED("template-change-id"),     // validates only changeId and state
 *         FAILED(BrokenChange.class).withErrorTrace("Expected error")  // adds error trace validation
 *     )
 *     .verify();
 * }</pre>
 *
 * @see AuditEntryDefinition
 * @see ThenStage#andExpectAuditFinalStateSequence(AuditEntryDefinition...)
 */
public class AuditFinalStateSequenceValidator implements SimpleValidator {

    private static final String VALIDATOR_NAME = "Audit Final State Sequence";

    private final List<AuditEntryExpectation> expectations;
    private final List<AuditEntry> actualEntries;
    private static final List<AuditEntry.Status> EXCLUDED_STATES = Collections.singletonList(
            AuditEntry.Status.STARTED
    );

    public AuditFinalStateSequenceValidator(AuditReader auditReader, List<AuditEntryDefinition> definitions) {
        this.expectations = definitions != null
                ? definitions.stream()
                .map(AuditEntryExpectation::new)
                .collect(Collectors.toList())
                : new ArrayList<>();

        this.actualEntries = auditReader.getAuditHistory().stream()
                .filter(entry -> !EXCLUDED_STATES.contains(entry.getState()))
                .filter(auditEntry -> !Boolean.TRUE.equals(auditEntry.getSystemChange()))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Internal constructor for direct list initialization (used by tests).
     */
    @TestOnly
    AuditFinalStateSequenceValidator(List<AuditEntryDefinition> expectedDefinitions, List<AuditEntry> actualEntries) {
        this.expectations = expectedDefinitions != null
                ? expectedDefinitions.stream()
                .map(AuditEntryExpectation::new)
                .collect(Collectors.toList())
                : new ArrayList<>();
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
