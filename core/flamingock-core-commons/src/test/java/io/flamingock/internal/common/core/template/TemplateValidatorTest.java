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
import io.flamingock.internal.common.core.error.validation.ValidationResult;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateValidatorTest {

    private TemplateValidator validator;

    // Test template with @ChangeTemplate (simple template)
    @ChangeTemplate
    public static class TestSimpleTemplate extends AbstractChangeTemplate<Void, String, String> {
        public TestSimpleTemplate() {
            super();
        }

        @Apply
        public void apply() {
            // Test implementation
        }
    }

    // Test template with @ChangeTemplate(multiStep = true)
    @ChangeTemplate(multiStep = true)
    public static class TestSteppableTemplate extends AbstractChangeTemplate<Void, String, String> {
        public TestSteppableTemplate() {
            super();
        }

        @Apply
        public void apply() {
            // Test implementation
        }
    }

    // Test template WITHOUT @ChangeTemplate annotation
    public static class TestUnannotatedTemplate extends AbstractChangeTemplate<Void, String, String> {
        public TestUnannotatedTemplate() {
            super();
        }

        @Apply
        public void apply() {
            // Test implementation
        }
    }

    @BeforeEach
    void setUp() {
        // Register test templates
        ChangeTemplateManager.addTemplate("TestSimpleTemplate", TestSimpleTemplate.class);
        ChangeTemplateManager.addTemplate("TestSteppableTemplate", TestSteppableTemplate.class);
        ChangeTemplateManager.addTemplate("TestUnannotatedTemplate", TestUnannotatedTemplate.class);
        validator = new TemplateValidator();
    }

    @Nested
    @DisplayName("getTemplateType tests")
    class GetTemplateTypeTests {

        @Test
        @DisplayName("Should return SIMPLE for AbstractSimpleTemplate subclass")
        void shouldReturnSimpleForAbstractSimpleTemplateSubclass() {
            TemplateValidator.TemplateType type = validator.getTemplateType(TestSimpleTemplate.class);
            assertEquals(TemplateValidator.TemplateType.SIMPLE, type);
        }

        @Test
        @DisplayName("Should return STEPPABLE for AbstractSteppableTemplate subclass")
        void shouldReturnSteppableForAbstractSteppableTemplateSubclass() {
            TemplateValidator.TemplateType type = validator.getTemplateType(TestSteppableTemplate.class);
            assertEquals(TemplateValidator.TemplateType.STEPPABLE, type);
        }
    }

    @Nested
    @DisplayName("SimpleTemplate validation tests")
    class SimpleTemplateValidationTests {

        @Test
        @DisplayName("SimpleTemplate with apply only should pass validation")
        void simpleTemplateWithApplyOnlyPasses() {
            TemplatePreviewChange preview = createPreview("test-change-1", "TestSimpleTemplate");
            preview.setApply("CREATE TABLE users");

            ValidationResult result = validator.validate(preview);

            assertFalse(result.hasErrors(), "Should have no errors: " + result.formatMessage());
        }

        @Test
        @DisplayName("SimpleTemplate with apply and rollback should pass validation")
        void simpleTemplateWithApplyAndRollbackPasses() {
            TemplatePreviewChange preview = createPreview("test-change-2", "TestSimpleTemplate");
            preview.setApply("CREATE TABLE users");
            preview.setRollback("DROP TABLE users");

            ValidationResult result = validator.validate(preview);

            assertFalse(result.hasErrors(), "Should have no errors: " + result.formatMessage());
        }

        @Test
        @DisplayName("SimpleTemplate with steps should fail validation")
        void simpleTemplateWithStepsFails() {
            TemplatePreviewChange preview = createPreview("test-change-3", "TestSimpleTemplate");
            preview.setApply("CREATE TABLE users");
            preview.setSteps(Arrays.asList(createStep("step1", null)));

            ValidationResult result = validator.validate(preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("SimpleTemplate must not have 'steps' field"));
        }

        @Test
        @DisplayName("SimpleTemplate missing apply should fail validation")
        void simpleTemplateMissingApplyFails() {
            TemplatePreviewChange preview = createPreview("test-change-4", "TestSimpleTemplate");
            preview.setRollback("DROP TABLE users");
            // apply is NOT set

            ValidationResult result = validator.validate(preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("SimpleTemplate requires 'apply' field"));
        }
    }

    @Nested
    @DisplayName("SteppableTemplate validation tests")
    class SteppableTemplateValidationTests {

        @Test
        @DisplayName("SteppableTemplate with valid steps should pass validation")
        void steppableTemplateWithValidStepsPasses() {
            TemplatePreviewChange preview = createPreview("test-change-5", "TestSteppableTemplate");
            preview.setSteps(Arrays.asList(
                    createStep("CREATE TABLE users", null),
                    createStep("CREATE TABLE orders", "DROP TABLE orders")
            ));

            ValidationResult result = validator.validate(preview);

            assertFalse(result.hasErrors(), "Should have no errors: " + result.formatMessage());
        }

        @Test
        @DisplayName("SteppableTemplate with steps having apply and rollback should pass validation")
        void steppableTemplateWithStepsHavingApplyAndRollbackPasses() {
            TemplatePreviewChange preview = createPreview("test-change-6", "TestSteppableTemplate");
            preview.setSteps(Arrays.asList(
                    createStep("CREATE TABLE users", "DROP TABLE users"),
                    createStep("CREATE TABLE orders", "DROP TABLE orders")
            ));

            ValidationResult result = validator.validate(preview);

            assertFalse(result.hasErrors(), "Should have no errors: " + result.formatMessage());
        }

        @Test
        @DisplayName("SteppableTemplate with apply at root should fail validation")
        void steppableTemplateWithApplyAtRootFails() {
            TemplatePreviewChange preview = createPreview("test-change-7", "TestSteppableTemplate");
            preview.setApply("CREATE TABLE users"); // Should not be at root level
            preview.setSteps(Arrays.asList(createStep("step1", null)));

            ValidationResult result = validator.validate(preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("SteppableTemplate must not have 'apply' at root level"));
        }

        @Test
        @DisplayName("SteppableTemplate with rollback at root should fail validation")
        void steppableTemplateWithRollbackAtRootFails() {
            TemplatePreviewChange preview = createPreview("test-change-8", "TestSteppableTemplate");
            preview.setRollback("DROP TABLE users"); // Should not be at root level
            preview.setSteps(Arrays.asList(createStep("step1", null)));

            ValidationResult result = validator.validate(preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("SteppableTemplate must not have 'rollback' at root level"));
        }

        @Test
        @DisplayName("SteppableTemplate missing steps should fail validation")
        void steppableTemplateMissingStepsFails() {
            TemplatePreviewChange preview = createPreview("test-change-9", "TestSteppableTemplate");
            // steps is NOT set

            ValidationResult result = validator.validate(preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("SteppableTemplate requires 'steps' field"));
        }

        @Test
        @DisplayName("SteppableTemplate with step missing apply should fail validation")
        void steppableTemplateWithStepMissingApplyFails() {
            TemplatePreviewChange preview = createPreview("test-change-10", "TestSteppableTemplate");

            List<Map<String, Object>> steps = new ArrayList<>();
            Map<String, Object> step1 = new HashMap<>();
            step1.put("rollback", "DROP TABLE users"); // apply is missing
            steps.add(step1);
            preview.setSteps(steps);

            ValidationResult result = validator.validate(preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("Step 1 is missing required 'apply' field"));
        }
    }

    @Nested
    @DisplayName("Template not found tests")
    class TemplateNotFoundTests {

        @Test
        @DisplayName("Unknown template name should fail with template not found error")
        void unknownTemplateNameFails() {
            TemplatePreviewChange preview = createPreview("test-change-11", "UnknownTemplate");
            preview.setApply("some operation");

            ValidationResult result = validator.validate(preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("Template 'UnknownTemplate' not found"));
        }

        @Test
        @DisplayName("Missing template name should fail validation")
        void missingTemplateNameFails() {
            TemplatePreviewChange preview = createPreview("test-change-12", null);
            preview.setApply("some operation");

            ValidationResult result = validator.validate(preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("Template name is required"));
        }
    }

    @Nested
    @DisplayName("validateStructure tests")
    class ValidateStructureTests {

        @Test
        @DisplayName("Should validate structure correctly with resolved template class")
        void shouldValidateStructureWithResolvedClass() {
            TemplatePreviewChange preview = createPreview("test-change-13", "TestSimpleTemplate");
            preview.setApply("CREATE TABLE users");

            ValidationResult result = validator.validateStructure(TestSimpleTemplate.class, preview);

            assertFalse(result.hasErrors(), "Should have no errors: " + result.formatMessage());
        }

        @Test
        @DisplayName("Should fail when template class is missing @ChangeTemplate annotation")
        void shouldFailWhenMissingAnnotation() {
            TemplatePreviewChange preview = createPreview("test-change-14", "TestUnannotatedTemplate");
            preview.setApply("some operation");

            ValidationResult result = validator.validateStructure(TestUnannotatedTemplate.class, preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("missing @ChangeTemplate annotation"));
        }

        @Test
        @DisplayName("Should detect steppable template structure mismatch via validateStructure")
        void shouldDetectSteppableMismatchViaValidateStructure() {
            TemplatePreviewChange preview = createPreview("test-change-15", "TestSteppableTemplate");
            preview.setApply("CREATE TABLE users"); // Wrong for steppable

            ValidationResult result = validator.validateStructure(TestSteppableTemplate.class, preview);

            assertTrue(result.hasErrors());
            assertTrue(result.formatMessage().contains("SteppableTemplate must not have 'apply' at root level"));
        }
    }

    /**
     * Helper to create a TemplatePreviewChange with id and template name.
     */
    private TemplatePreviewChange createPreview(String id, String templateName) {
        TemplatePreviewChange preview = new TemplatePreviewChange();
        preview.setId(id);
        preview.setSource(templateName);
        return preview;
    }

    /**
     * Helper method to create a step map with apply and optional rollback.
     */
    private Map<String, Object> createStep(String apply, String rollback) {
        Map<String, Object> step = new HashMap<>();
        step.put("apply", apply);
        if (rollback != null) {
            step.put("rollback", rollback);
        }
        return step;
    }
}
