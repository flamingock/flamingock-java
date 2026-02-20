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

import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.common.core.error.validation.ValidationResult;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates that template-based YAML changes have the correct structure for their template type.
 *
 * <p>This validator ensures:
 * <ul>
 *   <li><strong>SimpleTemplate</strong>: Must have {@code apply}, may have {@code rollback}, must NOT have {@code steps}</li>
 *   <li><strong>SteppableTemplate</strong>: Must have {@code steps}, must NOT have {@code apply} or {@code rollback} at root level</li>
 * </ul>
 *
 * <p>The validator is stateless and used at runtime during pipeline building to catch structural
 * mismatches before any change execution begins.
 */
public class TemplateValidator {

    /**
     * Enumeration of template types for validation purposes.
     */
    public enum TemplateType {
        /**
         * Template annotated with {@code @ChangeTemplate(multiStep = false)} or default.
         * Uses apply/rollback fields.
         */
        SIMPLE,
        /**
         * Template annotated with {@code @ChangeTemplate(multiStep = true)}.
         * Uses steps field.
         */
        STEPPABLE
    }

    private static final String ENTITY_TYPE = "template-change";

    /**
     * Creates a new TemplateValidator. The validator is stateless.
     */
    public TemplateValidator() {
    }

    /**
     * Validates the YAML content structure against the template type, performing
     * template lookup from the registry.
     *
     * @param preview the template preview change to validate
     * @return ValidationResult containing any validation errors found
     */
    public ValidationResult validate(TemplatePreviewChange preview) {
        ValidationResult result = new ValidationResult("Template structure validation");

        String templateName = preview.getTemplateName();
        String changeId = preview.getId() != null ? preview.getId() : "unknown";

        if (templateName == null || templateName.trim().isEmpty()) {
            result.add(new ValidationError("Template name is required", changeId, ENTITY_TYPE));
            return result;
        }

        Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> templateClassOpt = ChangeTemplateManager.getTemplate(templateName);

        if (!templateClassOpt.isPresent()) {
            result.add(new ValidationError(
                    "Template '" + templateName + "' not found. Ensure the template is registered via SPI.",
                    changeId,
                    ENTITY_TYPE
            ));
            return result;
        }

        Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templateClass = templateClassOpt.get();
        return validateStructure(templateClass, preview);
    }

    /**
     * Validates the YAML content structure against a resolved template class.
     * This method is used by {@code TemplateLoadedTaskBuilder} which already has the template class
     * resolved, avoiding a redundant lookup.
     *
     * @param templateClass the resolved template class
     * @param preview the template preview change to validate
     * @return ValidationResult containing any validation errors found
     */
    public ValidationResult validateStructure(Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templateClass, TemplatePreviewChange preview) {
        ValidationResult result = new ValidationResult("Template structure validation");

        String changeId = preview.getId() != null ? preview.getId() : "unknown";

        ChangeTemplate annotation = templateClass.getAnnotation(ChangeTemplate.class);
        if (annotation == null) {
            result.add(new ValidationError(
                    "Template class '" + templateClass.getSimpleName() + "' is missing @ChangeTemplate annotation",
                    changeId,
                    ENTITY_TYPE
            ));
            return result;
        }

        TemplateType type = annotation.multiStep() ? TemplateType.STEPPABLE : TemplateType.SIMPLE;

        switch (type) {
            case SIMPLE:
                validateSimpleTemplate(preview, changeId, result);
                break;
            case STEPPABLE:
                validateSteppableTemplate(preview, changeId, result);
                break;
        }

        return result;
    }

    /**
     * Determines the template type based on the {@link ChangeTemplate} annotation.
     *
     * @param templateClass the template class to check
     * @return the TemplateType (SIMPLE or STEPPABLE). Returns SIMPLE by default if annotation is missing.
     */
    public TemplateType getTemplateType(Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templateClass) {
        ChangeTemplate annotation = templateClass.getAnnotation(ChangeTemplate.class);
        if (annotation != null && annotation.multiStep()) {
            return TemplateType.STEPPABLE;
        }
        return TemplateType.SIMPLE;
    }

    /**
     * Validates a SimpleTemplate structure:
     * - Must have apply
     * - May have rollback
     * - Must NOT have steps
     */
    private void validateSimpleTemplate(TemplatePreviewChange preview, String changeId, ValidationResult result) {
        if (preview.getApply() == null) {
            result.add(new ValidationError(
                    "SimpleTemplate requires 'apply' field",
                    changeId,
                    ENTITY_TYPE
            ));
        }

        if (preview.getSteps() != null) {
            result.add(new ValidationError(
                    "SimpleTemplate must not have 'steps' field. Use 'apply' and 'rollback' instead.",
                    changeId,
                    ENTITY_TYPE
            ));
        }
    }

    /**
     * Validates a SteppableTemplate structure:
     * - Must have steps
     * - Must NOT have apply at root level
     * - Must NOT have rollback at root level
     * - Each step must have apply
     */
    @SuppressWarnings("unchecked")
    private void validateSteppableTemplate(TemplatePreviewChange preview, String changeId, ValidationResult result) {
        if (preview.getApply() != null) {
            result.add(new ValidationError(
                    "SteppableTemplate must not have 'apply' at root level. Define 'apply' within each step.",
                    changeId,
                    ENTITY_TYPE
            ));
        }

        if (preview.getRollback() != null) {
            result.add(new ValidationError(
                    "SteppableTemplate must not have 'rollback' at root level. Define 'rollback' within each step.",
                    changeId,
                    ENTITY_TYPE
            ));
        }

        Object steps = preview.getSteps();
        if (steps == null) {
            result.add(new ValidationError(
                    "SteppableTemplate requires 'steps' field",
                    changeId,
                    ENTITY_TYPE
            ));
            return;
        }

        if (steps instanceof List) {
            List<?> stepList = (List<?>) steps;
            for (int i = 0; i < stepList.size(); i++) {
                Object step = stepList.get(i);
                if (step instanceof Map) {
                    Map<String, Object> stepMap = (Map<String, Object>) step;
                    if (!stepMap.containsKey("apply") || stepMap.get("apply") == null) {
                        result.add(new ValidationError(
                                "Step " + (i + 1) + " is missing required 'apply' field",
                                changeId,
                                ENTITY_TYPE
                        ));
                    }
                }
            }
        }
    }
}
