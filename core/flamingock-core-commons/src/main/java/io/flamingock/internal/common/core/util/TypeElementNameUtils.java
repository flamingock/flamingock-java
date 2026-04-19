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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public final class TypeElementNameUtils {

    private TypeElementNameUtils() {
    }

    public static String getBinaryName(TypeElement typeElement) {
        Element enclosingElement = typeElement.getEnclosingElement();
        if (enclosingElement instanceof TypeElement && isTypeElement(enclosingElement)) {
            return getBinaryName((TypeElement) enclosingElement) + "$" + typeElement.getSimpleName();
        }
        return typeElement.getQualifiedName().toString();
    }

    private static boolean isTypeElement(Element element) {
        ElementKind kind = element.getKind();
        return kind == ElementKind.CLASS
                || kind == ElementKind.INTERFACE
                || kind == ElementKind.ENUM
                || kind == ElementKind.ANNOTATION_TYPE;
    }

    public static boolean isNonStaticNestedType(TypeElement typeElement) {
        return typeElement.getEnclosingElement() instanceof TypeElement
                && !typeElement.getModifiers().contains(Modifier.STATIC);
    }
}
