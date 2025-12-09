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
package io.flamingock.support.validation.error;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of a validation operation, containing any errors
 * that were found and the name of the validator that produced them.
 *
 * <p>Use the factory methods {@link #success(String)} and {@link #failure(String, ValidationError...)}
 * to create instances.</p>
 */
public class ValidationResult {

    private final String validatorName;
    private final List<ValidationError> errors;

    private ValidationResult(String validatorName, List<ValidationError> errors) {
        this.validatorName = validatorName;
        this.errors = errors != null ? errors : Collections.emptyList();
    }

    /**
     * Creates a successful validation result with no errors.
     *
     * @param validatorName the name of the validator (e.g., "Audit Sequence (Strict)")
     * @return a successful validation result
     */
    public static ValidationResult success(String validatorName) {
        return new ValidationResult(validatorName, Collections.emptyList());
    }

    /**
     * Creates a failed validation result with one or more errors.
     *
     * @param validatorName the name of the validator (e.g., "Audit Sequence (Strict)")
     * @param errors        the validation errors
     * @return a failed validation result
     */
    public static ValidationResult failure(String validatorName, ValidationError... errors) {
        return new ValidationResult(validatorName, Arrays.asList(errors));
    }


    /**
     * Returns whether this validation was successful (no errors).
     *
     * @return {@code true} if no errors were found
     */
    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /**
     * Returns whether this validation has errors.
     *
     * @return {@code true} if errors were found
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns the list of validation errors.
     *
     * @return an unmodifiable list of errors
     */
    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Returns the name of the validator that produced this result.
     *
     * @return the validator name
     */
    public String getValidatorName() {
        return validatorName;
    }
}
