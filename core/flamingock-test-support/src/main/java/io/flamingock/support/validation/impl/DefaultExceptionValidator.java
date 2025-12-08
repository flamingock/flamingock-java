package io.flamingock.support.validation.impl;

import io.flamingock.support.validation.ExceptionValidator;
import io.flamingock.support.validation.Validator;
import io.flamingock.support.validation.error.ValidationResult;

import java.util.function.Consumer;

public class DefaultExceptionValidator implements ExceptionValidator {

    private final Class<? extends Throwable> expectedExceptionClass;
    private final Consumer<Throwable> expectedExceptionConsumer;

    private Throwable actualException;

    public DefaultExceptionValidator(Class<? extends Throwable> expectedExceptionClass,
                                     Consumer<Throwable> expectedExceptionConsumer) {
        this.expectedExceptionClass = expectedExceptionClass;
        this.expectedExceptionConsumer = expectedExceptionConsumer;
    }

    public void setActualException(Throwable actualException) {
        this.actualException = actualException;
    }

    @Override
    public ValidationResult validate(Throwable actualException) {
        return new ValidationResult();
    }
}
