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
package io.flamingock.support.validation.impl;

import io.flamingock.support.validation.ExceptionValidator;
import io.flamingock.support.validation.error.ExceptionNotExpectedError;
import io.flamingock.support.validation.error.ExceptionTypeMismatchError;
import io.flamingock.support.validation.error.ValidationResult;

import java.util.function.Consumer;

public class DefaultExceptionValidator implements ExceptionValidator {

    private static final String VALIDATOR_NAME = "Exception";

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
        // No exception expected
        if (expectedExceptionClass == null) {
            if (actualException == null) {
                return ValidationResult.success(VALIDATOR_NAME);
            } else {
                return ValidationResult.failure(VALIDATOR_NAME, new ExceptionNotExpectedError(actualException));
            }
        }

        // An exception is expected but none was thrown
        if (actualException == null) {
            return ValidationResult.failure(VALIDATOR_NAME,
                    new ExceptionTypeMismatchError(expectedExceptionClass, null));
        }

        // Type mismatch
        if (!expectedExceptionClass.isInstance(actualException)) {
            return ValidationResult.failure(VALIDATOR_NAME,
                    new ExceptionTypeMismatchError(expectedExceptionClass, actualException.getClass()));
        }

        if (expectedExceptionConsumer != null) {
            expectedExceptionConsumer.accept(actualException);
        }
        return ValidationResult.success(VALIDATOR_NAME);
    }
}
