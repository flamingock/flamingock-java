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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.api.annotations.Change;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeLoadedTaskBuilderTest {

    private CodeLoadedTaskBuilder builder;

    @BeforeEach
    void setUp() {
        builder = CodeLoadedTaskBuilder.getInstance();
    }

    @Test
    @DisplayName("Should build with orderInContent when orderInContent is present and no order in source")
    void shouldBuildWithOrderInContentWhenOrderInContentPresentAndNoOrderInSource() {
        // Given
        builder.setId("test-id")
                .setOrder("001")
                .setChangeClass("java.lang.String") // Using existing class for simplicity
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When
        CodeLoadedChange result = builder.build();

        // Then
        assertEquals("001", result.getOrder().orElse(null));
        assertEquals("test-id", result.getId());
        assertEquals(String.class, result.getImplementationClass());
    }

    @Test
    @DisplayName("Should throw exception when orderInContent does not match order in source")
    void shouldThrowExceptionWhenOrderInContentDoesNotMatchOrderInSource() {
        // Given
        builder.setId("test-id")
                .setOrder("001")
                .setChangeClass("com.mypackage._002__MyClass")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

        assertEquals("Change[test-id] Order mismatch: @Change(order='001') does not match order in className='002'",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when both orderInContent and order in source are missing")
    void shouldThrowExceptionWhenBothOrderInContentAndOrderInSourceAreMissing() {
        // Given
        builder.setId("test-id")
                .setOrder(null)
                .setChangeClass("java.lang.String")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

        assertEquals("Change[test-id] Order is required: order must be present in the @Change annotation or in the className(e.g. _0001__test-id.java). If present in both, they must have the same value.",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when orderInContent is empty string")
    void shouldBuildWithOrderFromSourceWhenOrderInContentIsEmptyString() {
        // Given
        builder.setId("test-id")
                .setOrder("")
                .setChangeClass("com.mypackage._004__MyClass")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When
        FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

        // Then
        assertEquals("Change[test-id] Order mismatch: @Change(order='') does not match order in className='004'",
            exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when orderInContent is blank string")
    void shouldBuildWithOrderFromSourceWhenOrderInContentIsBlankString() {
        // Given
        builder.setId("test-id")
                .setOrder("   ")
                .setChangeClass("com.mypackage._005__MyClass")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When
        FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

        // Then
        assertEquals("Change[test-id] Order mismatch: @Change(order='   ') does not match order in className='005'",
            exception.getMessage());
    }

    @Test
    @DisplayName("Should work with real class when order validation passes")
    void shouldWorkWithRealClassWhenOrderValidationPasses() {
        // Given - using a real class that exists
        builder.setId("test-id")
                .setOrder("001")
                .setChangeClass("java.lang.String")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When
        CodeLoadedChange result = builder.build();

        // Then
        assertEquals("001", result.getOrder().orElse(null));
        assertEquals("test-id", result.getId());
        assertEquals(String.class, result.getImplementationClass());
        assertFalse(result.isRunAlways());
        assertTrue(result.isTransactional());
        assertFalse(result.isSystem());
    }

    @Test
    @DisplayName("Should handle beforeExecution flag correctly")
    void shouldHandleBeforeExecutionFlagCorrectly() {
        // Given
        builder.setId("test-id")
                .setOrder("001")
                .setChangeClass("java.lang.String")
                .setBeforeExecution(true)
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When
        CodeLoadedChange result = builder.build();

        // Then
        assertEquals("test-id_before", result.getId()); // Should append "_before" when beforeExecution is true
        assertEquals("001", result.getOrder().orElse(null));
        assertEquals(String.class, result.getImplementationClass());
    }

    // Test class with Change annotation for testing setFromFlamingockChangeAnnotation
    @Change(id = "annotation-test", transactional = false, author = "aperezdieppa")
    static class _100__TestChangeClass {
    }

    @Test
    @DisplayName("Should build from annotated class correctly")
    void shouldBuildFromAnnotatedClassCorrectly() {
        // Given
        CodeLoadedTaskBuilder builderFromClass = CodeLoadedTaskBuilder.getInstanceFromClass(_100__TestChangeClass.class);

        // When
        CodeLoadedChange result = builderFromClass.build();

        // Then
        assertEquals("annotation-test", result.getId());
        assertEquals("100", result.getOrder().orElse(null));
        assertEquals(_100__TestChangeClass.class, result.getImplementationClass());
        assertFalse(result.isRunAlways()); // Default is false since not specified in annotation
        assertFalse(result.isTransactional()); // Explicitly set to false in annotation
        assertFalse(result.isSystem());
    }

    @Test
    @DisplayName("Should support annotated class check")
    void shouldSupportAnnotatedClassCheck() {
        // When & Then
        assertTrue(CodeLoadedTaskBuilder.supportsSourceClass(_100__TestChangeClass.class));
        assertFalse(CodeLoadedTaskBuilder.supportsSourceClass(String.class));
    }

    @Change(id = "no-order-in_annotation", author = "aperezdieppa")
    static class _0001__anotherChange {
    }

    @Test
    @DisplayName("Should build from annotated class correctly")
    void shouldBuildFromAnnotatedClassCorrectlyWhenOrderInAnnotationNull() {
        // Given
        CodeLoadedTaskBuilder builderFromClass = CodeLoadedTaskBuilder.getInstanceFromClass(_0001__anotherChange.class);

        // When
        CodeLoadedChange result = builderFromClass.build();

        // Then
        assertEquals("no-order-in_annotation", result.getId());
        assertEquals("0001", result.getOrder().orElse(null));
        assertEquals(_0001__anotherChange.class, result.getImplementationClass());
    }

}
