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
                .setOrderInContent("001")
                .setChangeUnitClass("java.lang.String") // Using existing class for simplicity
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When
        CodeLoadedChangeUnit result = builder.build();

        // Then
        assertEquals("001", result.getOrder().orElse(null));
        assertEquals("test-id", result.getId());
        assertEquals(String.class, result.getImplementationClass());
    }

    @Test
    @DisplayName("Should build with order from source when orderInContent is null and order in source is present")
    void shouldBuildWithOrderFromSourceWhenOrderInContentIsNullAndOrderInSourceIsPresent() {
        // Given
        builder.setId("test-id")
                .setOrderInContent(null)
                .setChangeUnitClass("com.mypackage._002_MyClass")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When & Then
        // This will throw ClassNotFoundException since the class doesn't exist
        // But it will call the order validation before that, so we can test the order logic
        RuntimeException exception = assertThrows(RuntimeException.class, () -> builder.build());
        assertInstanceOf(ClassNotFoundException.class, exception.getCause());
    }

    @Test
    @DisplayName("Should build with orderInContent when orderInContent matches order in source")
    void shouldBuildWithOrderInContentWhenOrderInContentMatchesOrderInSource() {
        // Given
        builder.setId("test-id")
                .setOrderInContent("002")
                .setChangeUnitClass("java.lang._002_Test") // This will extract "002" from class name
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When & Then
        // This will throw ClassNotFoundException, but order validation happens first
        RuntimeException exception = assertThrows(RuntimeException.class, () -> builder.build());
        assertInstanceOf(ClassNotFoundException.class, exception.getCause());
    }

    @Test
    @DisplayName("Should throw exception when orderInContent does not match order in source")
    void shouldThrowExceptionWhenOrderInContentDoesNotMatchOrderInSource() {
        // Given
        builder.setId("test-id")
                .setOrderInContent("001")
                .setChangeUnitClass("com.mypackage._002_MyClass")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

        assertEquals("ChangeUnit[test-id] Order mismatch: @ChangeUnit(order='001') does not match order in className='002'",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when both orderInContent and order in source are missing")
    void shouldThrowExceptionWhenBothOrderInContentAndOrderInSourceAreMissing() {
        // Given
        builder.setId("test-id")
                .setOrderInContent(null)
                .setChangeUnitClass("java.lang.String")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When & Then
        FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

        assertEquals("ChangeUnit[test-id] Order is required: order must be present in the @ChangeUnit annotation or in the className(e.g. _0001_test-id.java). If present in both, they must have the same value.",
                exception.getMessage());
    }

    @Test
    @DisplayName("Should build with order from source when orderInContent is empty string")
    void shouldBuildWithOrderFromSourceWhenOrderInContentIsEmptyString() {
        // Given
        builder.setId("test-id")
                .setOrderInContent("")
                .setChangeUnitClass("com.mypackage._004_MyClass")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When & Then
        // This will throw ClassNotFoundException, but order validation happens first
        RuntimeException exception = assertThrows(RuntimeException.class, () -> builder.build());
        assertInstanceOf(ClassNotFoundException.class, exception.getCause());
    }

    @Test
    @DisplayName("Should build with order from source when orderInContent is blank string")
    void shouldBuildWithOrderFromSourceWhenOrderInContentIsBlankString() {
        // Given
        builder.setId("test-id")
                .setOrderInContent("   ")
                .setChangeUnitClass("com.mypackage._005_MyClass")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When & Then
        // This will throw ClassNotFoundException, but order validation happens first
        RuntimeException exception = assertThrows(RuntimeException.class, () -> builder.build());
        assertInstanceOf(ClassNotFoundException.class, exception.getCause());
    }

    @Test
    @DisplayName("Should work with real class when order validation passes")
    void shouldWorkWithRealClassWhenOrderValidationPasses() {
        // Given - using a real class that exists
        builder.setId("test-id")
                .setOrderInContent("001")
                .setChangeUnitClass("java.lang.String")
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When
        CodeLoadedChangeUnit result = builder.build();

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
                .setOrderInContent("001")
                .setChangeUnitClass("java.lang.String")
                .setBeforeExecution(true)
                .setRunAlways(false)
                .setTransactional(true)
                .setSystem(false);

        // When
        CodeLoadedChangeUnit result = builder.build();

        // Then
        assertEquals("test-id_before", result.getId()); // Should append "_before" when beforeExecution is true
        assertEquals("001", result.getOrder().orElse(null));
        assertEquals(String.class, result.getImplementationClass());
    }

    // Test class with ChangeUnit annotation for testing setFromFlamingockChangeAnnotation
    @Change(id = "annotation-test", order = "100", transactional = false)
    static class TestChangeUnitClass {
    }

    @Test
    @DisplayName("Should build from annotated class correctly")
    void shouldBuildFromAnnotatedClassCorrectly() {
        // Given
        CodeLoadedTaskBuilder builderFromClass = CodeLoadedTaskBuilder.getInstanceFromClass(TestChangeUnitClass.class);

        // When
        CodeLoadedChangeUnit result = builderFromClass.build();

        // Then
        assertEquals("annotation-test", result.getId());
        assertEquals("100", result.getOrder().orElse(null));
        assertEquals(TestChangeUnitClass.class, result.getImplementationClass());
        assertFalse(result.isRunAlways()); // Default is false since not specified in annotation
        assertFalse(result.isTransactional()); // Explicitly set to false in annotation
        assertFalse(result.isSystem());
    }

    @Test
    @DisplayName("Should support annotated class check")
    void shouldSupportAnnotatedClassCheck() {
        // When & Then
        assertTrue(CodeLoadedTaskBuilder.supportsSourceClass(TestChangeUnitClass.class));
        assertFalse(CodeLoadedTaskBuilder.supportsSourceClass(String.class));
    }

    @Change(id = "no-order-in_annotation")
    static class _100_noOrderInAnnotation {
    }

    @Test
    @DisplayName("Should build from annotated class correctly")
    void shouldBuildFromAnnotatedClassCorrectlyWhenOrderInAnnotationNull() {
        // Given
        CodeLoadedTaskBuilder builderFromClass = CodeLoadedTaskBuilder.getInstanceFromClass(_100_noOrderInAnnotation.class);

        // When
        CodeLoadedChangeUnit result = builderFromClass.build();

        // Then
        assertEquals("no-order-in_annotation", result.getId());
        assertEquals("100", result.getOrder().orElse(null));
        assertEquals(_100_noOrderInAnnotation.class, result.getImplementationClass());
    }

}