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
package io.flamingock.internal.core.change.loaded;

import io.flamingock.api.annotations.Apply;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.api.annotations.Change;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CodeLoadedChangeBuilderTest {

    private class WithoutOrderTestClass {}
    private class _002__MyClass {}

    private CodeLoadedChangeBuilder builder;

    @BeforeEach
    void setUp() {
        builder = CodeLoadedChangeBuilder.getInstance();
    }

    @Test
    @DisplayName("Should build with orderInContent when orderInContent is present and no order in source")
    void shouldBuildWithOrderInContentWhenOrderInContentPresentAndNoOrderInSource() {
        // Given
        builder.setId("test-id")
                .setOrder("001")
                .setChangeClassName(WithoutOrderTestClass.class.getName())
                .setRunAlways(false)
                .setTransactionalFlag(true)
                .setSystem(false);

        // When
        CodeLoadedChange result = builder.build();

        // Then
        assertEquals("001", result.getOrder().orElse(null));
        assertEquals("test-id", result.getId());
        assertEquals(WithoutOrderTestClass.class, result.getImplementationClass());
        assertNull(result.getSourceFile());
    }

    @Test
    @DisplayName("Should throw exception when orderInContent does not match order in source")
    void shouldThrowExceptionWhenOrderInContentDoesNotMatchOrderInSource() {
        // Given
        builder.setId("test-id")
                .setOrder("001")
                .setChangeClassName(_002__MyClass.class.getName())
                .setRunAlways(false)
                .setTransactionalFlag(true)
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
                .setChangeClassName(WithoutOrderTestClass.class.getName())
                .setRunAlways(false)
                .setTransactionalFlag(true)
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
                .setChangeClassName(_002__MyClass.class.getName())
                .setRunAlways(false)
                .setTransactionalFlag(true)
                .setSystem(false);

        // When
        FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

        // Then
        assertEquals("Change[test-id] Order mismatch: @Change(order='') does not match order in className='002'",
            exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when orderInContent is blank string")
    void shouldBuildWithOrderFromSourceWhenOrderInContentIsBlankString() {
        // Given
        builder.setId("test-id")
                .setOrder("   ")
                .setChangeClassName(_002__MyClass.class.getName())
                .setRunAlways(false)
                .setTransactionalFlag(true)
                .setSystem(false);

        // When
        FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

        // Then
        assertEquals("Change[test-id] Order mismatch: @Change(order='   ') does not match order in className='002'",
            exception.getMessage());
    }

    @Test
    @DisplayName("Should work with real class when order validation passes")
    void shouldWorkWithRealClassWhenOrderValidationPasses() {
        // Given - using a real class that exists
        builder.setId("test-id")
                .setOrder("001")
                .setChangeClassName(WithoutOrderTestClass.class.getName())
                .setRunAlways(false)
                .setTransactionalFlag(true)
                .setSystem(false);

        // When
        CodeLoadedChange result = builder.build();

        // Then
        assertEquals("001", result.getOrder().orElse(null));
        assertEquals("test-id", result.getId());
        assertEquals(WithoutOrderTestClass.class, result.getImplementationClass());
        assertFalse(result.isRunAlways());
        assertTrue(result.isTransactional());
        assertFalse(result.isSystem());
        assertNull(result.getSourceFile());
    }

    @Test
    @DisplayName("Should default to true when transactional is null")
    void shouldDefaultToTrueWhenTransactionalIsNull() {
        // Given
        builder.setId("test-id")
                .setOrder("001")
                .setChangeClassName(WithoutOrderTestClass.class.getName())
                .setRunAlways(false)
                .setTransactionalFlag(null)
                .setSystem(false);

        // When
        CodeLoadedChange result = builder.build();

        // Then
        assertTrue(result.isTransactional());
    }

    @Test
    @DisplayName("Should load preview with nested class binary name")
    void shouldLoadPreviewWithNestedClassBinaryName() {
        CodeLoadedChange change = CodeLoadedChangeBuilder.getInstanceFromPreview(preview(Group.NestedChange.class.getName())).build();

        assertEquals(Group.NestedChange.class, change.getImplementationClass());
    }

    // Test class with Change annotation for testing setFromFlamingockChangeAnnotation
    @Change(id = "annotation-test", transactional = false, author = "aperezdieppa")
    public static class _100__TestChangeClass {
        @Apply
        public void apply() {
            // testing purpose
        }
    }

    @Test
    @DisplayName("Should build from annotated class correctly")
    void shouldBuildFromAnnotatedClassCorrectly() {
        // Given
        CodeLoadedChangeBuilder builderFromClass = CodeLoadedChangeBuilder.getInstanceFromClass(_100__TestChangeClass.class);

        // When
        CodeLoadedChange result = builderFromClass.build();

        // Then
        assertEquals("annotation-test", result.getId());
        assertEquals("100", result.getOrder().orElse(null));
        assertEquals(_100__TestChangeClass.class, result.getImplementationClass());
        assertNull(result.getSourceFile());
        assertFalse(result.isRunAlways()); // Default is false since not specified in annotation
        assertFalse(result.isTransactional()); // Explicitly set to false in annotation
        assertFalse(result.isSystem());
    }

    @Test
    @DisplayName("Should support annotated class check")
    void shouldSupportAnnotatedClassCheck() {
        // When & Then
        assertTrue(CodeLoadedChangeBuilder.supportsSourceClass(_100__TestChangeClass.class));
        assertFalse(CodeLoadedChangeBuilder.supportsSourceClass(String.class));
    }

    @Change(id = "no-order-in_annotation", author = "aperezdieppa")
    public static class _0001__anotherChange {
        @Apply
        public void apply() {
            // testing purpose
        }
    }

    @Test
    @DisplayName("Should build from annotated class correctly")
    void shouldBuildFromAnnotatedClassCorrectlyWhenOrderInAnnotationNull() {
        // Given
        CodeLoadedChangeBuilder builderFromClass = CodeLoadedChangeBuilder.getInstanceFromClass(_0001__anotherChange.class);

        // When
        CodeLoadedChange result = builderFromClass.build();

        // Then
        assertEquals("no-order-in_annotation", result.getId());
        assertEquals("0001", result.getOrder().orElse(null));
        assertEquals(_0001__anotherChange.class, result.getImplementationClass());
        assertNull(result.getSourceFile());
    }

    @Test
    void shouldInvokeInheritedPreviewMethodsForBothEditions() throws Exception {
        for (boolean legacy : new boolean[]{false, true}) {
            CodeLoadedChange change = CodeLoadedChangeBuilder.getInstanceFromPreview(
                    preview(InheritedChange.class.getName(), legacy, true)).build();
            InheritedChange instance = new InheritedChange();
            change.getApplyMethod().invoke(instance, "applied");
            assertEquals("applied", instance.effect);
            change.getRollbackMethod().get().invoke(instance);
            assertEquals("rolled back", instance.effect);
            assertEquals(BaseChange.class, change.getApplyMethod().getDeclaringClass());
        }
    }

    @Test
    void shouldPreferDirectlyDeclaredPreviewMethods() throws Exception {
        CodeLoadedChange change = CodeLoadedChangeBuilder.getInstanceFromPreview(
                preview(DirectChange.class.getName(), false, true)).build();
        DirectChange instance = new DirectChange();
        change.getApplyMethod().invoke(instance, "applied");
        assertEquals("direct applied", instance.effect);
        change.getRollbackMethod().get().invoke(instance);
        assertEquals("direct rollback", instance.effect);
        assertEquals(DirectChange.class, change.getApplyMethod().getDeclaringClass());
    }

    @Test
    void shouldSkipIntermediateOverloadsForInheritedPreviewSignatures() throws Exception {
        for (boolean legacy : new boolean[]{false, true}) {
            CodeLoadedChange change = CodeLoadedChangeBuilder.getInstanceFromPreview(
                    signaturePreview(OverloadLeaf.class, "apply", "rollback",
                            Collections.singletonList(String.class.getName()), legacy)).build();
            OverloadLeaf instance = new OverloadLeaf();
            assertArrayEquals(new Class<?>[]{String.class}, change.getApplyMethod().getParameterTypes());
            assertArrayEquals(new Class<?>[]{String.class}, change.getRollbackMethod().get().getParameterTypes());
            change.getApplyMethod().invoke(instance, "applied");
            assertEquals("applied", instance.effect);
            change.getRollbackMethod().get().invoke(instance, "rolled back");
            assertEquals("rolled back", instance.effect);
        }
    }

    @Test
    void shouldResolveInheritedPreviewSignaturesWithConcreteOverloads() throws Exception {
        for (boolean legacy : new boolean[]{false, true}) {
            CodeLoadedChange change = CodeLoadedChangeBuilder.getInstanceFromPreview(
                    signaturePreview(OverloadChild.class, "apply", "rollback",
                            Collections.singletonList(String.class.getName()), legacy)).build();
            OverloadChild instance = new OverloadChild();
            assertArrayEquals(new Class<?>[]{String.class}, change.getApplyMethod().getParameterTypes());
            assertArrayEquals(new Class<?>[]{String.class}, change.getRollbackMethod().get().getParameterTypes());
            change.getApplyMethod().invoke(instance, "applied");
            assertEquals("applied", instance.effect);
            change.getRollbackMethod().get().invoke(instance, "rolled back");
            assertEquals("rolled back", instance.effect);
        }
    }

    @Test
    void shouldResolveDirectPreviewSignaturesWithInheritedOverloads() throws Exception {
        for (boolean legacy : new boolean[]{false, true}) {
            CodeLoadedChange change = CodeLoadedChangeBuilder.getInstanceFromPreview(
                    signaturePreview(OverloadChild.class, "apply", "rollback",
                            Collections.singletonList(Integer.class.getName()), legacy)).build();
            OverloadChild instance = new OverloadChild();
            assertArrayEquals(new Class<?>[]{Integer.class}, change.getApplyMethod().getParameterTypes());
            assertArrayEquals(new Class<?>[]{Integer.class}, change.getRollbackMethod().get().getParameterTypes());
            change.getApplyMethod().invoke(instance, 1);
            assertEquals("integer apply", instance.effect);
            change.getRollbackMethod().get().invoke(instance, 1);
            assertEquals("integer rollback", instance.effect);
        }
    }

    @Test
    void shouldMatchInheritedPrimitiveAndArraySignatures() throws Exception {
        CodeLoadedChange change = CodeLoadedChangeBuilder.getInstanceFromPreview(
                signaturePreview(OverloadLeaf.class, "applyArrays", "rollbackArrays",
                        java.util.Arrays.asList("int", "int[]", "java.lang.String[]"), false)).build();
        OverloadLeaf instance = new OverloadLeaf();
        Class<?>[] expected = {int.class, int[].class, String[].class};
        assertArrayEquals(expected, change.getApplyMethod().getParameterTypes());
        assertArrayEquals(expected, change.getRollbackMethod().get().getParameterTypes());
        change.getApplyMethod().invoke(instance, 7, new int[]{8}, new String[]{"apply"});
        assertEquals("apply78", instance.effect);
        change.getRollbackMethod().get().invoke(instance, 9, new int[]{10}, new String[]{"rollback"});
        assertEquals("rollback910", instance.effect);
    }

    public static class OverloadBase {
        String effect;

        @Apply
        public void apply(String value) { effect = value; }

        @io.flamingock.api.annotations.Rollback
        public void rollback(String value) { effect = value; }

        public void applyArrays(int value, int[] numbers, String[] text) {
            effect = text[0] + value + numbers[0];
        }

        public void rollbackArrays(int value, int[] numbers, String[] text) {
            effect = text[0] + value + numbers[0];
        }
    }

    public static class OverloadChild extends OverloadBase {
        public void apply(Integer value) { effect = "integer apply"; }
        public void rollback(Integer value) { effect = "integer rollback"; }
        public void applyArrays(Integer value, Integer[] numbers, Object[] text) { }
        public void rollbackArrays(Integer value, Integer[] numbers, Object[] text) { }
    }

    public static class OverloadLeaf extends OverloadChild { }

    private CodePreviewChange signaturePreview(Class<?> source, String apply, String rollback,
                                             java.util.List<String> parameters, boolean legacy) {
        return new CodePreviewChange("signature-change", "001", "flamingock", source.getName(), null,
                PreviewConstructor.getDefault(), new PreviewMethod(apply, parameters),
                new PreviewMethod(rollback, parameters), false, true, false, null,
                RecoveryDescriptor.getDefault(), legacy);
    }

    public static class BaseChange {
        String effect;

        public void apply(String value) {
            effect = value;
        }

        public void rollback() {
            effect = "rolled back";
        }
    }

    public static class MiddleChange extends BaseChange { }

    public static class InheritedChange extends MiddleChange { }

    public static class DirectChange extends MiddleChange {
        @Override
        public void apply(String value) {
            effect = "direct " + value;
        }

        @Override
        public void rollback() {
            effect = "direct rollback";
        }
    }

    private CodePreviewChange preview(String sourceClassPath) {
        return preview(sourceClassPath, false, false);
    }

    private CodePreviewChange preview(String sourceClassPath, boolean legacy, boolean rollback) {
        return new CodePreviewChange(
                "nested-change",
                "001",
                "flamingock",
                sourceClassPath,
                null,
                PreviewConstructor.getDefault(),
                new PreviewMethod("apply", rollback
                        ? Collections.singletonList(String.class.getName()) : Collections.emptyList()),
                rollback ? new PreviewMethod("rollback", Collections.emptyList()) : null,
                false,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault(),
                legacy);
    }

    static class Group {

        static class NestedChange {

            @Apply
            void apply() {
            }
        }
    }

}
