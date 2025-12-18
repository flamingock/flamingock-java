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
package io.flamingock.support.validation;

import io.flamingock.support.context.TestContext;
import io.flamingock.support.validation.error.ExceptionNotExpectedError;
import io.flamingock.support.validation.error.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the execution of all validators and aggregates their results.
 *
 * <p>The handler executes all registered validators, collects their results,
 * and throws an {@link AssertionError} if any validation fails. The error
 * message is formatted to display all failures grouped by validator.</p>
 */
public class ValidationHandler {

    private static final String EXECUTION_VALIDATOR_NAME = "Execution";

    private final List<Validator> validators;
    private final Throwable executionException;
    private final ValidationErrorFormatter formatter;

    public ValidationHandler(List<Validator> validators) {
        this(validators, null);
    }

    public ValidationHandler(List<Validator> validators, Throwable executionException) {
        this.validators = validators;
        this.executionException = executionException;
        this.formatter = new ValidationErrorFormatter();
    }

    // TestContext and deferred ValidatorArgs
    public ValidationHandler(TestContext testContext, List<ValidatorArgs> args) {
        this(testContext, args, null);
    }

    public ValidationHandler(TestContext testContext, List<ValidatorArgs> args, Throwable executionException) {
        this.executionException = executionException;
        this.formatter = new ValidationErrorFormatter();
        // Build actual validators now that we have access to the TestContext
        ValidatorFactory factory = new ValidatorFactory(testContext.getAuditReader());
        List<Validator> built = new ArrayList<>();
        if (args != null) {
            for (ValidatorArgs a : args) {
                built.add(factory.getValidator(a));
            }
        }
        this.validators = built;
    }

    /**
     * Executes all validators and throws an AssertionError if any validation fails.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Executes each registered validator</li>
     *   <li>Checks for unexpected exceptions (if no exception validator is registered)</li>
     *   <li>Collects all validation failures</li>
     *   <li>Throws an AssertionError with a formatted message if any failures exist</li>
     * </ol>
     *
     * @throws AssertionError if any validation fails
     */
    public void validate() throws AssertionError {
        List<ValidationResult> results = new ArrayList<>();

        for (Validator validator : validators) {
            ValidationResult result = executeValidator(validator);
            if (result != null) {
                results.add(result);
            }
        }

        // Check if no exception expected but one occurred
        if (executionException != null && !hasExceptionValidator()) {
            results.add(ValidationResult.failure(EXECUTION_VALIDATOR_NAME,
                    new ExceptionNotExpectedError(executionException)));
        }

        // Collect all failures
        List<ValidationResult> failures = results.stream()
                .filter(ValidationResult::hasErrors)
                .collect(Collectors.toList());

        if (!failures.isEmpty()) {
            throw new AssertionError(formatter.format(failures));
        }
    }

    private ValidationResult executeValidator(Validator validator) {
        if (validator instanceof SimpleValidator) {
            return ((SimpleValidator) validator).validate();
        } else if (validator instanceof ExceptionValidator) {
            return ((ExceptionValidator) validator).validate(executionException);
        }
        return null;
    }

    private boolean hasExceptionValidator() {
        return validators.stream()
                .anyMatch(v -> v instanceof ExceptionValidator);
    }
}
