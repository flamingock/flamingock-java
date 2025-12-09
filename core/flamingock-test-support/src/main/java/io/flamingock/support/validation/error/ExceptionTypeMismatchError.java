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
 * Validation error indicating that the thrown exception type
 * does not match the expected type, or no exception was thrown
 * when one was expected.
 */
public class ExceptionTypeMismatchError extends ValidationError {

    private final Class<? extends Throwable> expectedType;
    private final Class<? extends Throwable> actualType;

    /**
     * Creates a new exception type mismatch error.
     *
     * @param expectedType the expected exception type
     * @param actualType   the actual exception type thrown, or {@code null} if no exception was thrown
     */
    public ExceptionTypeMismatchError(Class<? extends Throwable> expectedType,
                                      Class<? extends Throwable> actualType) {
        super(ValidationErrorType.EXCEPTION_TYPE_MISMATCH);
        this.expectedType = expectedType;
        this.actualType = actualType;
    }

    public Class<? extends Throwable> getExpectedType() {
        return expectedType;
    }

    public Class<? extends Throwable> getActualType() {
        return actualType;
    }

    @Override
    public String formatMessage() {
        if (actualType == null) {
            return String.format("Expected exception <%s> but none was thrown",
                    expectedType.getSimpleName());
        }
        return String.format("Exception type mismatch: expected <%s> but was <%s>",
                expectedType.getSimpleName(),
                actualType.getSimpleName());
    }
}
