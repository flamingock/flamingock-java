/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.preview.builder;

import io.flamingock.api.annotations.Change;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import org.junit.jupiter.api.Test;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodePreviewChangeBuilderTest {

    @Test
    void shouldBuildNullSourceFileForCodeChanges() {
        CodePreviewChange preview = CodePreviewChangeBuilder.instance()
                .setId("test-id")
                .setOrder("001")
                .setAuthor("author")
                .setSourceClassPath("io.flamingock.TestChange")
                .setConstructor(PreviewConstructor.getDefault())
                .setApplyMethod(new PreviewMethod("apply", Collections.emptyList()))
                .setRollbackMethod(null)
                .setRunAlways(false)
                .setTransactionalFlag(true)
                .setSystem(false)
                .setRecovery(RecoveryDescriptor.getDefault())
                .build();

        assertNull(preview.getSourceFile());
    }

    @Test
    void shouldExtractSourcePackageFromNestedBinaryClassName() {
        CodePreviewChange preview = buildPreview("io.flamingock.ChangeGroup$_001__NestedChange");

        assertEquals("io.flamingock", preview.getSourcePackage());
    }

    @Test
    void shouldSetEmptySourcePackageWhenSourceClassIsNotDeclaredInPackage() {
        CodePreviewChange preview = buildPreview("_001__DefaultPackageChange");

        assertEquals("", preview.getSourcePackage());
    }

    @Test
    void shouldThrowWhenChangeIsNonStaticNestedClass() {
        TypeElement group = mockTypeElement("io.flamingock.GroupClass", "GroupClass", packageElement());
        TypeElement change = mockTypeElement("io.flamingock.GroupClass._1001__InnerChange", "_1001__InnerChange", group);
        Change changeAnnotation = changeAnnotation();
        when(change.getAnnotation(Change.class)).thenReturn(changeAnnotation);

        FlamingockException exception = assertThrows(
                FlamingockException.class,
                () -> CodePreviewChangeBuilder.instance(change).build());

        assertEquals(
                "Change class [io.flamingock.GroupClass$_1001__InnerChange] is a non-static nested class. Nested change classes must be static so Flamingock can instantiate them without an enclosing class instance.",
                exception.getMessage());
    }

    private CodePreviewChange buildPreview(String sourceClassPath) {
        return CodePreviewChangeBuilder.instance()
                .setId("test-id")
                .setOrder("001")
                .setAuthor("author")
                .setSourceClassPath(sourceClassPath)
                .setConstructor(PreviewConstructor.getDefault())
                .setApplyMethod(new PreviewMethod("apply", Collections.emptyList()))
                .setRollbackMethod(null)
                .setRunAlways(false)
                .setTransactionalFlag(true)
                .setSystem(false)
                .setRecovery(RecoveryDescriptor.getDefault())
                .build();
    }

    private TypeElement mockTypeElement(String qualifiedName, String simpleName, Element enclosingElement) {
        TypeElement typeElement = mock(TypeElement.class);
        when(typeElement.getKind()).thenReturn(ElementKind.CLASS);
        when(typeElement.getQualifiedName()).thenReturn(name(qualifiedName));
        when(typeElement.getSimpleName()).thenReturn(name(simpleName));
        when(typeElement.getEnclosingElement()).thenReturn(enclosingElement);
        when(typeElement.getModifiers()).thenReturn(Collections.emptySet());
        return typeElement;
    }

    private Element packageElement() {
        Element element = mock(Element.class);
        when(element.getKind()).thenReturn(ElementKind.PACKAGE);
        return element;
    }

    private Change changeAnnotation() {
        Change change = mock(Change.class);
        when(change.id()).thenReturn("inner-change");
        return change;
    }

    private Name name(String value) {
        return new Name() {
            @Override
            public boolean contentEquals(CharSequence cs) {
                return value.contentEquals(cs);
            }

            @Override
            public int length() {
                return value.length();
            }

            @Override
            public char charAt(int index) {
                return value.charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return value.subSequence(start, end);
            }

            @Override
            public String toString() {
                return value;
            }
        };
    }
}
