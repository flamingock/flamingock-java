package io.flamingock.support.validation;

import io.flamingock.support.validation.error.ValidationResult;

public interface ExceptionValidator extends Validator {

    ValidationResult validate(Throwable actualException);

}
