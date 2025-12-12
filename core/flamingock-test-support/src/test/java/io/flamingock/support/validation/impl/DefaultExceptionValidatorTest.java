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

import io.flamingock.support.validation.error.ExceptionNotExpectedError;
import io.flamingock.support.validation.error.ExceptionTypeMismatchError;
import io.flamingock.support.validation.error.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DefaultExceptionValidatorTest {

    @Test
    @DisplayName("DefaultExceptionValidatorTest: No exception expected and none thrown — should succeed")
    void noExceptionExpected_andNoneThrown_shouldSucceed() {
        DefaultExceptionValidator validator = new DefaultExceptionValidator(null, null);
        ValidationResult result = validator.validate(null);
        assertTrue(result.isSuccess());
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("DefaultExceptionValidatorTest: No exception expected but an exception thrown — should fail with ExceptionNotExpectedError")
    void noExceptionExpected_butExceptionThrown_shouldFailWithNotExpected() {
        DefaultExceptionValidator validator = new DefaultExceptionValidator(null, null);
        RuntimeException ex = new RuntimeException("message");
        ValidationResult result = validator.validate(ex);
        assertTrue(result.hasErrors());
        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertInstanceOf(ExceptionNotExpectedError.class, result.getErrors().get(0));
    }

    @Test
    @DisplayName("DefaultExceptionValidatorTest: Exception expected but none thrown — should fail with ExceptionTypeMismatchError (null actual)")
    void exceptionExpected_butNoneThrown_shouldFailWithTypeMismatch_nullActual() {
        DefaultExceptionValidator validator = new DefaultExceptionValidator(IOException.class, null);
        ValidationResult result = validator.validate(null);
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertInstanceOf(ExceptionTypeMismatchError.class, result.getErrors().get(0));
    }

    @Test
    @DisplayName("DefaultExceptionValidatorTest: Exception expected but wrong type thrown — should fail with ExceptionTypeMismatchError")
    void exceptionExpected_butWrongTypeThrown_shouldFailWithTypeMismatch() {
        DefaultExceptionValidator validator = new DefaultExceptionValidator(IllegalArgumentException.class, null);
        RuntimeException ex = new RuntimeException("message");
        ValidationResult result = validator.validate(ex);
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertInstanceOf(ExceptionTypeMismatchError.class, result.getErrors().get(0));
    }

    @Test
    @DisplayName("DefaultExceptionValidatorTest: Exception expected and correct type thrown — should succeed and invoke consumer")
    void exceptionExpected_andCorrectTypeThrown_shouldSucceed_andInvokeConsumer() {
        AtomicBoolean consumed = new AtomicBoolean(false);
        DefaultExceptionValidator validator = new DefaultExceptionValidator(RuntimeException.class, e -> consumed.set(true));
        RuntimeException ex = new RuntimeException("message");
        ValidationResult result = validator.validate(ex);
        assertTrue(result.isSuccess());
        assertFalse(result.hasErrors());
        assertTrue(consumed.get());
    }
}
