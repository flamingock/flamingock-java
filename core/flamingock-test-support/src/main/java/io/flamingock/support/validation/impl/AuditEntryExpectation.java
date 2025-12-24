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
import io.flamingock.support.validation.error.FieldMismatchError;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Internal wrapper that adds validation logic to an {@link AuditEntryDefinition}.
 *
 * <p>This class is used internally by the test framework to validate actual audit entries
 * against expected definitions. Users should not interact with this class directly;
 * they should use {@link AuditEntryDefinition} instead.</p>
 */

public class AuditEntryExpectation {

    private final AuditEntryDefinition definition;

    public AuditEntryExpectation(AuditEntryDefinition definition) {
        this.definition = definition;
    }

    /**
     * Compares this expectation against an actual audit entry.
     *
     * <p>Returns a list of field mismatches (empty if all expected fields match).
     * Only fields with non-null expected values are verified, except for
     * {@code changeId} and {@code status} which are always verified.</p>
     *
     * @param actual the actual audit entry to compare against
     * @return list of field mismatch errors (empty if all match)
     */
    public List<FieldMismatchError> compareWith(AuditEntry actual) {
        List<FieldMismatchError> errors = new ArrayList<>();

        // Required fields - always verified
        if (!definition.getChangeId().equals(actual.getTaskId())) {
            errors.add(new FieldMismatchError("changeId", definition.getChangeId(), actual.getTaskId()));
        }

        if (definition.getState() != actual.getState()) {
            errors.add(new FieldMismatchError("status",
                    definition.getState().name(),
                    actual.getState() != null ? actual.getState().name() : null));
        }

        // Optional fields - verified when non-null
        if (definition.getExecutionId() != null && !definition.getExecutionId().equals(actual.getExecutionId())) {
            errors.add(new FieldMismatchError("executionId", definition.getExecutionId(), actual.getExecutionId()));
        }

        if (definition.getStageId() != null && !definition.getStageId().equals(actual.getStageId())) {
            errors.add(new FieldMismatchError("stageId", definition.getStageId(), actual.getStageId()));
        }

        if (definition.getAuthor() != null && !definition.getAuthor().equals(actual.getAuthor())) {
            errors.add(new FieldMismatchError("author", definition.getAuthor(), actual.getAuthor()));
        }

        if (definition.getClassName() != null && !definition.getClassName().equals(actual.getClassName())) {
            errors.add(new FieldMismatchError("className", definition.getClassName(), actual.getClassName()));
        }

        if (definition.getMethodName() != null && !definition.getMethodName().equals(actual.getMethodName())) {
            errors.add(new FieldMismatchError("methodName", definition.getMethodName(), actual.getMethodName()));
        }

        if (definition.getMetadata() != null && !Objects.equals(definition.getMetadata(), actual.getMetadata())) {
            errors.add(new FieldMismatchError("metadata",
                    String.valueOf(definition.getMetadata()),
                    String.valueOf(actual.getMetadata())));
        }

        if (definition.getExecutionMillis() != null && !definition.getExecutionMillis().equals(actual.getExecutionMillis())) {
            errors.add(new FieldMismatchError("executionMillis",
                    String.valueOf(definition.getExecutionMillis()),
                    String.valueOf(actual.getExecutionMillis())));
        }

        if (definition.getExecutionHostname() != null && !definition.getExecutionHostname().equals(actual.getExecutionHostname())) {
            errors.add(new FieldMismatchError("executionHostname", definition.getExecutionHostname(), actual.getExecutionHostname()));
        }

        if (definition.getErrorTrace() != null && !definition.getErrorTrace().equals(actual.getErrorTrace())) {
            errors.add(new FieldMismatchError("errorTrace", definition.getErrorTrace(), actual.getErrorTrace()));
        }

        if (definition.getTargetSystemId() != null && !definition.getTargetSystemId().equals(actual.getTargetSystemId())) {
            errors.add(new FieldMismatchError("targetSystemId", definition.getTargetSystemId(), actual.getTargetSystemId()));
        }

        if (definition.getRecoveryStrategy() != null && definition.getRecoveryStrategy() != actual.getRecoveryStrategy()) {
            errors.add(new FieldMismatchError("recoveryStrategy",
                    definition.getRecoveryStrategy().name(),
                    actual.getRecoveryStrategy() != null ? actual.getRecoveryStrategy().name() : null));
        }

        if (definition.getOrder() != null && !definition.getOrder().equals(actual.getOrder())) {
            errors.add(new FieldMismatchError("order", definition.getOrder(), actual.getOrder()));
        }

        if (definition.getTransactional() != null && !definition.getTransactional().equals(actual.getTransactionFlag())) {
            errors.add(new FieldMismatchError("transactional",
                    String.valueOf(definition.getTransactional()),
                    String.valueOf(actual.getTransactionFlag())));
        }

        // Timestamp - exact match only
        if (definition.getCreatedAt() != null && !definition.getCreatedAt().equals(actual.getCreatedAt())) {
            errors.add(new FieldMismatchError("createdAt",
                    definition.getCreatedAt().toString(),
                    actual.getCreatedAt() != null ? actual.getCreatedAt().toString() : null));
        }

        return errors;
    }

    /**
     * Returns the wrapped definition.
     *
     * @return the audit entry definition
     */
    AuditEntryDefinition getDefinition() {
        return definition;
    }
}
