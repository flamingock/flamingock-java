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
package io.flamingock.internal.common.core.util;

import org.junit.jupiter.api.Test;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;

import java.util.Collections;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypeElementNameUtilsTest {

    @Test
    void shouldReturnQualifiedNameForTopLevelClass() {
        TypeElement change = mockTypeElement("io.flamingock.changes.Change001", "Change001", packageElement());

        assertEquals("io.flamingock.changes.Change001", TypeElementNameUtils.getBinaryName(change));
    }

    @Test
    void shouldReturnBinaryNameForNestedClass() {
        TypeElement group = mockTypeElement("io.flamingock.changes.ChangeGroup", "ChangeGroup", packageElement());
        TypeElement change = mockTypeElement("io.flamingock.changes.ChangeGroup.Change001", "Change001", group);

        assertEquals("io.flamingock.changes.ChangeGroup$Change001", TypeElementNameUtils.getBinaryName(change));
    }

    @Test
    void shouldReturnBinaryNameForDeeplyNestedClass() {
        TypeElement group = mockTypeElement("io.flamingock.changes.ChangeGroup", "ChangeGroup", packageElement());
        TypeElement subgroup = mockTypeElement("io.flamingock.changes.ChangeGroup.SubGroup", "SubGroup", group);
        TypeElement change = mockTypeElement("io.flamingock.changes.ChangeGroup.SubGroup.Change001", "Change001", subgroup);

        assertEquals("io.flamingock.changes.ChangeGroup$SubGroup$Change001", TypeElementNameUtils.getBinaryName(change));
    }

    @Test
    void shouldDetectNonStaticNestedClass() {
        TypeElement group = mockTypeElement("io.flamingock.changes.ChangeGroup", "ChangeGroup", packageElement());
        TypeElement change = mockTypeElement("io.flamingock.changes.ChangeGroup.Change001", "Change001", group);

        assertTrue(TypeElementNameUtils.isNonStaticNestedType(change));
    }

    @Test
    void shouldNotDetectStaticNestedClassAsNonStaticNestedClass() {
        TypeElement group = mockTypeElement("io.flamingock.changes.ChangeGroup", "ChangeGroup", packageElement());
        TypeElement change = mockTypeElement("io.flamingock.changes.ChangeGroup.Change001", "Change001", group, EnumSet.of(Modifier.STATIC));

        assertFalse(TypeElementNameUtils.isNonStaticNestedType(change));
    }

    @Test
    void shouldNotDetectTopLevelClassAsNonStaticNestedClass() {
        TypeElement change = mockTypeElement("io.flamingock.changes.Change001", "Change001", packageElement());

        assertFalse(TypeElementNameUtils.isNonStaticNestedType(change));
    }

    private TypeElement mockTypeElement(String qualifiedName, String simpleName, Element enclosingElement) {
        return mockTypeElement(qualifiedName, simpleName, enclosingElement, Collections.emptySet());
    }

    private TypeElement mockTypeElement(String qualifiedName, String simpleName, Element enclosingElement, java.util.Set<Modifier> modifiers) {
        TypeElement typeElement = mock(TypeElement.class);
        when(typeElement.getKind()).thenReturn(ElementKind.CLASS);
        when(typeElement.getQualifiedName()).thenReturn(name(qualifiedName));
        when(typeElement.getSimpleName()).thenReturn(name(simpleName));
        when(typeElement.getEnclosingElement()).thenReturn(enclosingElement);
        when(typeElement.getModifiers()).thenReturn(modifiers);
        return typeElement;
    }

    private Element packageElement() {
        Element element = mock(Element.class);
        when(element.getKind()).thenReturn(ElementKind.PACKAGE);
        return element;
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
