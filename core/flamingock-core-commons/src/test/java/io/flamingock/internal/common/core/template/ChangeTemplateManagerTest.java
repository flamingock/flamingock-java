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

import java.util.Collection;
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
    @ChangeTemplate
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
    @ChangeTemplate
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
    @ChangeTemplate
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
        @DisplayName("Should return template by simple class name")
        void shouldReturnTemplateBySimpleClassName() {
            ChangeTemplateManager.addTemplate(UnitTestTemplate.class.getSimpleName(), UnitTestTemplate.class);

            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate(UnitTestTemplate.class.getSimpleName());

            assertTrue(result.isPresent());
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

        @Test
        @DisplayName("Should handle null template name gracefully")
        void shouldHandleNullTemplateNameGracefully() {
            // This tests that the HashMap can accept null keys (it can)
            // The template manager doesn't explicitly validate for null
            assertDoesNotThrow(() ->
                    ChangeTemplateManager.addTemplate(null, UnitTestTemplate.class));
        }

        @Test
        @DisplayName("Should handle null template class gracefully")
        void shouldHandleNullTemplateClassGracefully() {
            // This tests that the HashMap can accept null values (it can)
            // The template manager doesn't explicitly validate for null
            assertDoesNotThrow(() ->
                    ChangeTemplateManager.addTemplate("NullTemplate", null));

            // Getting a null-valued entry returns empty Optional due to Optional.ofNullable
            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate("NullTemplate");

            assertFalse(result.isPresent(), "Should return empty Optional for null template class");
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
    }

    @Nested
    @DisplayName("loadTemplates tests (SPI integration)")
    class LoadTemplatesTests {

        @Test
        @DisplayName("Should load templates from SPI and register by simple class name")
        void shouldLoadTemplatesFromSPIAndRegisterBySimpleClassName() {
            // Load templates via SPI
            ChangeTemplateManager.loadTemplates();

            // Verify that templates from SPI are loaded
            // The actual templates depend on what's registered in META-INF/services
            Collection<io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templates =
                    ChangeTemplateManager.getTemplates();

            // There should be templates discovered via SPI
            // (SPITestTemplate, SPITestSteppableTemplate from direct SPI,
            //  FactoryProvidedTemplate from ChangeTemplateFactory)
            assertTrue(templates.size() >= 3,
                    "Should discover at least 3 templates from SPI");
        }

        @Test
        @DisplayName("Should register direct SPI templates")
        void shouldRegisterDirectSPITemplates() {
            ChangeTemplateManager.loadTemplates();

            // Verify SPITestTemplate is registered (from direct SPI)
            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate("SPITestTemplate");

            assertTrue(result.isPresent(), "SPITestTemplate should be registered via SPI");
        }

        @Test
        @DisplayName("Should register steppable SPI templates")
        void shouldRegisterSteppableSPITemplates() {
            ChangeTemplateManager.loadTemplates();

            // Verify SPITestSteppableTemplate is registered (from direct SPI)
            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate("SPITestSteppableTemplate");

            assertTrue(result.isPresent(), "SPITestSteppableTemplate should be registered via SPI");
        }

        @Test
        @DisplayName("Should register factory-provided templates")
        void shouldRegisterFactoryProvidedTemplates() {
            ChangeTemplateManager.loadTemplates();

            // Verify FactoryProvidedTemplate is registered (from ChangeTemplateFactory)
            Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> result =
                    ChangeTemplateManager.getTemplate("FactoryProvidedTemplate");

            assertTrue(result.isPresent(),
                    "FactoryProvidedTemplate should be registered via ChangeTemplateFactory SPI");
        }
    }

    @Nested
    @DisplayName("getTemplates tests (SPI discovery)")
    class GetTemplatesTests {

        @Test
        @DisplayName("Should return all SPI-discovered templates")
        void shouldReturnAllSPIDiscoveredTemplates() {
            Collection<io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templates =
                    ChangeTemplateManager.getTemplates();

            assertNotNull(templates);
            assertTrue(templates.size() >= 3,
                    "Should discover at least 3 templates from SPI");
        }

        @Test
        @DisplayName("Should include direct SPI implementations")
        void shouldIncludeDirectSPIImplementations() {
            Collection<io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templates =
                    ChangeTemplateManager.getTemplates();

            boolean foundSPITestTemplate = templates.stream()
                    .anyMatch(t -> t.getClass().getSimpleName().equals("SPITestTemplate"));
            boolean foundSPITestSteppableTemplate = templates.stream()
                    .anyMatch(t -> t.getClass().getSimpleName().equals("SPITestSteppableTemplate"));

            assertTrue(foundSPITestTemplate, "Should include SPITestTemplate from direct SPI");
            assertTrue(foundSPITestSteppableTemplate,
                    "Should include SPITestSteppableTemplate from direct SPI");
        }

        @Test
        @DisplayName("Should include factory-provided templates")
        void shouldIncludeFactoryProvidedTemplates() {
            Collection<io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templates =
                    ChangeTemplateManager.getTemplates();

            boolean foundFactoryProvidedTemplate = templates.stream()
                    .anyMatch(t -> t.getClass().getSimpleName().equals("FactoryProvidedTemplate"));

            assertTrue(foundFactoryProvidedTemplate,
                    "Should include FactoryProvidedTemplate from ChangeTemplateFactory");
        }

        @Test
        @DisplayName("Should create new instances on each call")
        void shouldCreateNewInstancesOnEachCall() {
            Collection<io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templates1 =
                    ChangeTemplateManager.getTemplates();
            Collection<io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templates2 =
                    ChangeTemplateManager.getTemplates();

            // The collections should contain new instances
            // We verify this by checking that the template instances are not the same objects
            io.flamingock.api.template.ChangeTemplate<?, ?, ?> first1 = templates1.iterator().next();
            io.flamingock.api.template.ChangeTemplate<?, ?, ?> first2 = templates2.iterator().next();

            // ServiceLoader creates new instances each time, so they should not be the same object
            // (though they may be of the same class)
            assertNotSame(first1, first2,
                    "Each call to getTemplates() should create new instances");
        }
    }
}
