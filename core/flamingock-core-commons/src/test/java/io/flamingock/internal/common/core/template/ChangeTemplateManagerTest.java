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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChangeTemplateManagerTest {

    @BeforeEach
    void setUp() {
        // Clear templates before each test for isolation
        ChangeTemplateManager.clearTemplates();
    }

    @AfterEach
    void tearDown() {
        // Clear templates after each test to avoid polluting other tests
        ChangeTemplateManager.clearTemplates();
    }

    // Test template for unit tests
    @ChangeTemplate(id = "UnitTestTemplate")
    public static class UnitTestTemplate extends AbstractChangeTemplate<Void, String, String> {
        public UnitTestTemplate() {
            super();
        }

        @Apply
        public void apply() {
            // Test implementation
        }
    }

    // Second test template for multiple registration tests
    @ChangeTemplate(id = "SecondUnitTestTemplate")
    public static class SecondUnitTestTemplate extends AbstractChangeTemplate<Void, String, String> {
        public SecondUnitTestTemplate() {
            super();
        }

        @Apply
        public void apply() {
            // Test implementation
        }
    }

    // Third test template for overwrite tests
    @ChangeTemplate(id = "OverwriteTestTemplate")
    public static class OverwriteTestTemplate extends AbstractChangeTemplate<Void, Integer, Integer> {
        public OverwriteTestTemplate() {
            super();
        }

        @Apply
        public void apply() {
            // Test implementation
        }
    }

    @Nested
    @DisplayName("getTemplate tests")
    class GetTemplateTests {

        @Test
        @DisplayName("Should return empty Optional for non-existent template")
        void shouldReturnEmptyOptionalForNonExistentTemplate() {
            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate("NonExistentTemplate");

            assertFalse(result.isPresent(), "Should return empty Optional for non-existent template");
        }

        @Test
        @DisplayName("Should return template class when registered")
        void shouldReturnTemplateClassWhenRegistered() {
            ChangeTemplateManager.addTemplate("UnitTestTemplate", UnitTestTemplate.class);

            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate("UnitTestTemplate");

            assertTrue(result.isPresent(), "Should return present Optional for registered template");
            assertEquals(UnitTestTemplate.class, result.get());
        }

        @Test
        @DisplayName("Should return empty Optional for null template name")
        void shouldReturnEmptyOptionalForNullTemplateName() {
            ChangeTemplateManager.addTemplate("UnitTestTemplate", UnitTestTemplate.class);

            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate(null);

            assertFalse(result.isPresent(), "Should return empty Optional for null template name");
        }
    }

    @Nested
    @DisplayName("addTemplate tests")
    class AddTemplateTests {

        @Test
        @DisplayName("Should add template successfully")
        void shouldAddTemplateSuccessfully() {
            ChangeTemplateManager.addTemplate("UnitTestTemplate", UnitTestTemplate.class);

            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate("UnitTestTemplate");

            assertTrue(result.isPresent());
            assertEquals(UnitTestTemplate.class, result.get());
        }

        @Test
        @DisplayName("Should add multiple templates")
        void shouldAddMultipleTemplates() {
            ChangeTemplateManager.addTemplate("UnitTestTemplate", UnitTestTemplate.class);
            ChangeTemplateManager.addTemplate("SecondUnitTestTemplate", SecondUnitTestTemplate.class);

            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result1 =
                    ChangeTemplateManager.getTemplate("UnitTestTemplate");
            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result2 =
                    ChangeTemplateManager.getTemplate("SecondUnitTestTemplate");

            assertTrue(result1.isPresent());
            assertTrue(result2.isPresent());
            assertEquals(UnitTestTemplate.class, result1.get());
            assertEquals(SecondUnitTestTemplate.class, result2.get());
        }

        @Test
        @DisplayName("Should overwrite existing template with same name")
        void shouldOverwriteExistingTemplateWithSameName() {
            ChangeTemplateManager.addTemplate("SharedName", UnitTestTemplate.class);
            ChangeTemplateManager.addTemplate("SharedName", OverwriteTestTemplate.class);

            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate("SharedName");

            assertTrue(result.isPresent());
            assertEquals(OverwriteTestTemplate.class, result.get(),
                    "Should have overwritten with the new template class");
        }
    }

    @Nested
    @DisplayName("clearTemplates tests")
    class ClearTemplatesTests {

        @Test
        @DisplayName("Should clear all templates")
        void shouldClearAllTemplates() {
            ChangeTemplateManager.addTemplate("UnitTestTemplate", UnitTestTemplate.class);
            ChangeTemplateManager.addTemplate("SecondUnitTestTemplate", SecondUnitTestTemplate.class);

            // Verify templates are registered
            assertTrue(ChangeTemplateManager.getTemplate("UnitTestTemplate").isPresent());
            assertTrue(ChangeTemplateManager.getTemplate("SecondUnitTestTemplate").isPresent());

            // Clear templates
            ChangeTemplateManager.clearTemplates();

            // Verify templates are cleared
            assertFalse(ChangeTemplateManager.getTemplate("UnitTestTemplate").isPresent());
            assertFalse(ChangeTemplateManager.getTemplate("SecondUnitTestTemplate").isPresent());
        }

        @Test
        @DisplayName("Should be safe to clear empty registry")
        void shouldBeSafeToClearEmptyRegistry() {
            assertDoesNotThrow(() -> ChangeTemplateManager.clearTemplates());
        }

        @Test
        @DisplayName("Should reset initialized flag when clearing")
        void shouldResetInitializedFlagWhenClearing() {
            // Initialize with some templates
            List<TemplateMetadata> metadataList = Collections.singletonList(
                    new TemplateMetadata("UnitTestTemplate", false, UnitTestTemplate.class.getName())
            );
            ChangeTemplateManager.initializeFromMetadata(metadataList);
            assertTrue(ChangeTemplateManager.isInitialized());

            // Clear templates
            ChangeTemplateManager.clearTemplates();

            // Should be able to reinitialize
            assertFalse(ChangeTemplateManager.isInitialized());
        }
    }

    @Nested
    @DisplayName("initializeFromMetadata tests")
    class InitializeFromMetadataTests {

        @Test
        @DisplayName("Should initialize templates from metadata")
        void shouldInitializeTemplatesFromMetadata() {
            List<TemplateMetadata> metadataList = Arrays.asList(
                    new TemplateMetadata("UnitTestTemplate", false, UnitTestTemplate.class.getName()),
                    new TemplateMetadata("SecondUnitTestTemplate", false, SecondUnitTestTemplate.class.getName())
            );

            ChangeTemplateManager.initializeFromMetadata(metadataList);

            assertTrue(ChangeTemplateManager.isInitialized());
            assertTrue(ChangeTemplateManager.getTemplate("UnitTestTemplate").isPresent());
            assertTrue(ChangeTemplateManager.getTemplate("SecondUnitTestTemplate").isPresent());
            assertEquals(UnitTestTemplate.class, ChangeTemplateManager.getTemplate("UnitTestTemplate").get());
            assertEquals(SecondUnitTestTemplate.class, ChangeTemplateManager.getTemplate("SecondUnitTestTemplate").get());
        }

        @Test
        @DisplayName("Should handle empty metadata list")
        void shouldHandleEmptyMetadataList() {
            ChangeTemplateManager.initializeFromMetadata(Collections.emptyList());

            assertTrue(ChangeTemplateManager.isInitialized());
            assertFalse(ChangeTemplateManager.getTemplate("UnitTestTemplate").isPresent());
        }

        @Test
        @DisplayName("Should handle null metadata list")
        void shouldHandleNullMetadataList() {
            ChangeTemplateManager.initializeFromMetadata(null);

            assertTrue(ChangeTemplateManager.isInitialized());
        }

        @Test
        @DisplayName("Should not reinitialize if already initialized")
        void shouldNotReinitializeIfAlreadyInitialized() {
            List<TemplateMetadata> firstMetadata = Collections.singletonList(
                    new TemplateMetadata("UnitTestTemplate", false, UnitTestTemplate.class.getName())
            );
            List<TemplateMetadata> secondMetadata = Collections.singletonList(
                    new TemplateMetadata("SecondUnitTestTemplate", false, SecondUnitTestTemplate.class.getName())
            );

            ChangeTemplateManager.initializeFromMetadata(firstMetadata);
            ChangeTemplateManager.initializeFromMetadata(secondMetadata);

            // Only first template should be present (second initialization was skipped)
            assertTrue(ChangeTemplateManager.getTemplate("UnitTestTemplate").isPresent());
            assertFalse(ChangeTemplateManager.getTemplate("SecondUnitTestTemplate").isPresent());
        }

        @Test
        @DisplayName("Should throw exception for non-existent class")
        void shouldThrowExceptionForNonExistentClass() {
            List<TemplateMetadata> metadataList = Collections.singletonList(
                    new TemplateMetadata("NonExistent", false, "com.example.NonExistentClass")
            );

            assertThrows(RuntimeException.class, () ->
                    ChangeTemplateManager.initializeFromMetadata(metadataList));
        }
    }

    @Nested
    @DisplayName("getTemplateMetadata tests")
    class GetTemplateMetadataTests {

        @Test
        @DisplayName("Should return metadata when template is registered")
        void shouldReturnMetadataWhenTemplateIsRegistered() {
            TemplateMetadata metadata = new TemplateMetadata("UnitTestTemplate", true, UnitTestTemplate.class.getName());
            ChangeTemplateManager.addTemplate("UnitTestTemplate", UnitTestTemplate.class, metadata);

            Optional<TemplateMetadata> result = ChangeTemplateManager.getTemplateMetadata("UnitTestTemplate");

            assertTrue(result.isPresent());
            assertEquals("UnitTestTemplate", result.get().getId());
            assertTrue(result.get().isMultiStep());
            assertEquals(UnitTestTemplate.class.getName(), result.get().getFullyQualifiedClassName());
        }

        @Test
        @DisplayName("Should return empty Optional for non-existent template")
        void shouldReturnEmptyOptionalForNonExistentTemplate() {
            Optional<TemplateMetadata> result = ChangeTemplateManager.getTemplateMetadata("NonExistent");

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("isInitialized tests")
    class IsInitializedTests {

        @Test
        @DisplayName("Should return false before initialization")
        void shouldReturnFalseBeforeInitialization() {
            assertFalse(ChangeTemplateManager.isInitialized());
        }

        @Test
        @DisplayName("Should return true after initialization")
        void shouldReturnTrueAfterInitialization() {
            ChangeTemplateManager.initializeFromMetadata(Collections.emptyList());
            assertTrue(ChangeTemplateManager.isInitialized());
        }
    }
}
