package io.flamingock.support.validation;

import java.util.List;

public class ValidationHandler {

    private final List<Validator> validators;
    private final Throwable executionException;

    public ValidationHandler(List<Validator> validators) {
        this(validators, null);
    }

    public ValidationHandler(List<Validator> validators, Throwable executionException) {
        this.validators = validators;
        this.executionException = executionException;
    }


    public void validate() throws AssertionError {

        //TODO process validator and grab the potential validation errors in a AssertionError
        // we probably need another class for building the validation result


    }
}
