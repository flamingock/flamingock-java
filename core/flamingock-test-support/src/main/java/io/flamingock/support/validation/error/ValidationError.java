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

/**
 * Abstract base class for all validation errors in the Flamingock test support framework.
 *
 * <p>Each concrete validation error extends this class and provides its specific
 * error type via the constructor and a human-readable message via {@link #formatMessage()}.</p>
 *
 * @see ValidationErrorType
 * @see ValidationResult
 */
public abstract class ValidationError {

    private final ValidationErrorType errorType;

    /**
     * Constructs a validation error with the specified type.
     *
     * @param errorType the type of validation error
     */
    protected ValidationError(ValidationErrorType errorType) {
        this.errorType = errorType;
    }

    /**
     * Returns the type of this validation error.
     *
     * @return the error type
     */
    public ValidationErrorType getErrorType() {
        return errorType;
    }

    /**
     * Formats this error as a human-readable message for display in assertion errors.
     *
     * @return a formatted error message
     */
    public abstract String formatMessage();
}
