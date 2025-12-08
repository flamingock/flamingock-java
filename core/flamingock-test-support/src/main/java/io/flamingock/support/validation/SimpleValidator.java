package io.flamingock.support.validation;

import io.flamingock.support.validation.error.ValidationResult;

public interface SimpleValidator extends Validator {

    ValidationResult validate();

}
