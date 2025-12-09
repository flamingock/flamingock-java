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
 * Validation error indicating that a custom validation
 * (user-provided validator) failed.
 */
public class CustomValidationError extends ValidationError {

    private final String message;
    private final Throwable cause;

    /**
     * Creates a new custom validation error with a message.
     *
     * @param message the error message describing the validation failure
     */
    public CustomValidationError(String message) {
        this(message, null);
    }

    /**
     * Creates a new custom validation error with a message and cause.
     *
     * @param message the error message describing the validation failure
     * @param cause   the exception thrown by the custom validator, or {@code null}
     */
    public CustomValidationError(String message, Throwable cause) {
        super(ValidationErrorType.CUSTOM_VALIDATION);
        this.message = message;
        this.cause = cause;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    public String formatMessage() {
        if (cause != null) {
            return String.format("Custom validation failed: %s (caused by: %s - '%s')",
                    message,
                    cause.getClass().getSimpleName(),
                    cause.getMessage());
        }
        return String.format("Custom validation failed: %s", message);
    }
}
