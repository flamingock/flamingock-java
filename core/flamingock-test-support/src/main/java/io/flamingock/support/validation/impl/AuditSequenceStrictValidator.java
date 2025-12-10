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

import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.support.domain.AuditEntryDefinition;
import io.flamingock.support.validation.SimpleValidator;
import io.flamingock.support.validation.error.ValidationResult;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AuditSequenceStrictValidator implements SimpleValidator {

    private static final String VALIDATOR_NAME = "Audit Sequence (Strict)";

    private final AuditReader auditReader;
    private final List<AuditEntryExpectation> expectations;


    public AuditSequenceStrictValidator(AuditReader auditReader, AuditEntryDefinition... definitions) {
        this.auditReader = auditReader;
        this.expectations = Arrays.stream(definitions)
                .map(AuditEntryExpectation::new)
                .collect(Collectors.toList());
    }

    @Override
    public ValidationResult validate() {
        // TODO: Implement actual validation logic
        return ValidationResult.success(VALIDATOR_NAME);
    }
}
