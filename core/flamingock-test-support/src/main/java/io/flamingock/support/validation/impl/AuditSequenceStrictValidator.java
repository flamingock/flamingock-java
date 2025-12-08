package io.flamingock.support.validation.impl;

import io.flamingock.internal.core.store.AuditStore;
import io.flamingock.support.domain.AuditEntryExpectation;
import io.flamingock.support.validation.SimpleValidator;
import io.flamingock.support.validation.Validator;
import io.flamingock.support.validation.error.ValidationResult;

import java.util.Arrays;
import java.util.List;

public class AuditSequenceStrictValidator implements SimpleValidator {

    private final AuditStore<?> auditStore;
    private final List<AuditEntryExpectation> expectations;

    public AuditSequenceStrictValidator(AuditStore<?> auditStore, AuditEntryExpectation... expectations) {
        this.auditStore = auditStore;
        this.expectations = Arrays.asList(expectations);
    }

    @Override
    public ValidationResult validate() {
        return new ValidationResult();
    }
}
