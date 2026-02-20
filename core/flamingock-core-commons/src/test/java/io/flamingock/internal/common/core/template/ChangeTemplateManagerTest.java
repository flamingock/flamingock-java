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
package io.flamingock.internal.common.core.template;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.internal.common.core.error.FlamingockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChangeTemplateManagerTest {

    @ChangeTemplate(id = "annotated-simple-template")
    public static class AnnotatedSimpleTemplate extends AbstractChangeTemplate<Void, String, String> {
        public AnnotatedSimpleTemplate() {
            super();
        }

        @Apply
        public void apply() {
        }
    }

    @ChangeTemplate(id = "annotated-steppable-template", multiStep = true)
    public static class AnnotatedSteppableTemplate extends AbstractChangeTemplate<Void, String, String> {
        public AnnotatedSteppableTemplate() {
            super();
        }

        @Apply
        public void apply() {
        }
    }

    public static class UnannotatedTemplate extends AbstractChangeTemplate<Void, String, String> {
        public UnannotatedTemplate() {
            super();
        }

        @Apply
        public void apply() {
        }
    }

    @Test
    @DisplayName("addTemplate with annotated simple class should succeed and return correct definition")
    void addTemplateWithAnnotatedSimpleClassShouldSucceed() {
        ChangeTemplateManager.addTemplate(AnnotatedSimpleTemplate.class);

        Optional<ChangeTemplateDefinition> result = ChangeTemplateManager.getTemplate("annotated-simple-template");

        assertTrue(result.isPresent());
        assertEquals(AnnotatedSimpleTemplate.class, result.get().getTemplateClass());
        assertFalse(result.get().isMultiStep());
    }

    @Test
    @DisplayName("addTemplate with annotated steppable class should succeed and return multiStep=true")
    void addTemplateWithAnnotatedSteppableClassShouldSucceed() {
        ChangeTemplateManager.addTemplate(AnnotatedSteppableTemplate.class);

        Optional<ChangeTemplateDefinition> result = ChangeTemplateManager.getTemplate("annotated-steppable-template");

        assertTrue(result.isPresent());
        assertEquals(AnnotatedSteppableTemplate.class, result.get().getTemplateClass());
        assertTrue(result.get().isMultiStep());
    }

    @Test
    @DisplayName("addTemplate with unannotated class should throw FlamingockException")
    void addTemplateWithUnannotatedClassShouldThrow() {
        FlamingockException exception = assertThrows(FlamingockException.class,
                () -> ChangeTemplateManager.addTemplate(UnannotatedTemplate.class));

        assertTrue(exception.getMessage().contains("missing required @ChangeTemplate annotation"));
        assertTrue(exception.getMessage().contains("UnannotatedTemplate"));
    }

    @Test
    @DisplayName("getTemplate for unregistered name should return empty")
    void getTemplateForUnregisteredNameShouldReturnEmpty() {
        Optional<ChangeTemplateDefinition> result = ChangeTemplateManager.getTemplate("NonExistentTemplate");

        assertFalse(result.isPresent());
    }
}
