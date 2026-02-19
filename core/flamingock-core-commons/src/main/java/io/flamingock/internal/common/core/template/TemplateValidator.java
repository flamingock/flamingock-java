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

import java.util.HashMap;
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
 * <p>The validator is used during compile-time by the annotation processor to catch structural
 * mismatches early. Behavior is controlled by the {@code strictTemplateValidation} flag in
 * {@code @EnableFlamingock}:
 * <ul>
 *   <li>{@code true} (default): Compilation fails with detailed error</li>
 *   <li>{@code false}: Warning logged, compilation continues</li>
 * </ul>
 */
public class TemplateValidator {

    /**
     * Enumeration of template types for validation purposes.
     */
    public enum TemplateType {
        /**
         * Template annotated with {@code @ChangeTemplate(multiStep = false)} or without annotation.
         * Uses apply/rollback fields.
         */
        SIMPLE,
        /**
         * Template annotated with {@code @ChangeTemplate(multiStep = true)}.
         * Uses steps field.
         */
        STEPPABLE,
        /**
         * Template type could not be determined (kept for backward compatibility).
         */
        UNKNOWN
    }

    private static final String ENTITY_TYPE = "template-change";

    /**
     * Map of template ID to metadata for compile-time validation.
     */
    private final Map<String, TemplateMetadata> templateMetadataMap;

    /**
     * Creates a TemplateValidator with templates from discovery result.
     *
     * @param templates list of discovered template metadata
     */
    public TemplateValidator(List<TemplateMetadata> templates) {
        this.templateMetadataMap = new HashMap<>();
        if (templates != null) {
            for (TemplateMetadata meta : templates) {
                templateMetadataMap.put(meta.getId(), meta);
            }
        }
    }

    /**
     * Creates a TemplateValidator using ChangeTemplateManager (for runtime validation).
     * This constructor is used when templates have already been initialized.
     */
    public TemplateValidator() {
        this.templateMetadataMap = null; // Will use ChangeTemplateManager
    }

    /**
     * Validates the YAML content structure against the template type.
     *
     * @param content the parsed YAML content
     * @return ValidationResult containing any validation errors found
     */
    public ValidationResult validate(ChangeTemplateFileContent content) {
        ValidationResult result = new ValidationResult("Template structure validation");

        String templateName = content.getTemplate();
        String changeId = content.getId() != null ? content.getId() : "unknown";

        if (templateName == null || templateName.trim().isEmpty()) {
            result.add(new ValidationError("Template name is required", changeId, ENTITY_TYPE));
            return result;
        }

        TemplateType type = getTemplateType(templateName, changeId, result);

        if (result.hasErrors()) {
            return result;
        }

        switch (type) {
            case SIMPLE:
                validateSimpleTemplate(content, changeId, result);
                break;
            case STEPPABLE:
                validateSteppableTemplate(content, changeId, result);
                break;
            case UNKNOWN:
                // Unknown types are valid - they may have custom structure
                break;
        }

        return result;
    }

    /**
     * Determines the template type based on metadata or runtime lookup.
     *
     * @param templateId the template identifier (ID, not class name)
     * @param changeId   the change ID for error reporting
     * @param result     the validation result to add errors to
     * @return the TemplateType
     */
    private TemplateType getTemplateType(String templateId, String changeId, ValidationResult result) {
        // Try compile-time metadata first
        if (templateMetadataMap != null) {
            TemplateMetadata meta = templateMetadataMap.get(templateId);
            if (meta != null) {
                return meta.isMultiStep() ? TemplateType.STEPPABLE : TemplateType.SIMPLE;
            }
            // Template not found in compile-time metadata
            result.add(new ValidationError(
                    "Template '" + templateId + "' not found. Ensure the template class has @ChangeTemplate(id = \"" +
                            templateId + "\") annotation.",
                    changeId,
                    ENTITY_TYPE
            ));
            return TemplateType.UNKNOWN;
        }

        // Fall back to runtime lookup via ChangeTemplateManager
        Optional<TemplateMetadata> metaOpt = ChangeTemplateManager.getTemplateMetadata(templateId);
        if (metaOpt.isPresent()) {
            return metaOpt.get().isMultiStep() ? TemplateType.STEPPABLE : TemplateType.SIMPLE;
        }

        // Try class lookup as fallback for old-style templates
        Optional<Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>>> templateClassOpt =
                ChangeTemplateManager.getTemplate(templateId);

        if (!templateClassOpt.isPresent()) {
            result.add(new ValidationError(
                    "Template '" + templateId + "' not found. Ensure the template is registered.",
                    changeId,
                    ENTITY_TYPE
            ));
            return TemplateType.UNKNOWN;
        }

        return getTemplateTypeFromClass(templateClassOpt.get());
    }

    /**
     * Determines the template type based on the {@link ChangeTemplate} annotation.
     *
     * @param templateClass the template class to check
     * @return the TemplateType (SIMPLE or STEPPABLE). Returns SIMPLE by default if annotation is missing.
     */
    public TemplateType getTemplateTypeFromClass(Class<? extends io.flamingock.api.template.ChangeTemplate<?, ?, ?>> templateClass) {
        ChangeTemplate annotation = templateClass.getAnnotation(ChangeTemplate.class);
        if (annotation != null && annotation.multiStep()) {
            return TemplateType.STEPPABLE;
        }
        // Default to SIMPLE (including when annotation is missing or multiStep=false)
        return TemplateType.SIMPLE;
    }

    /**
     * Validates a SimpleTemplate structure:
     * - Must have apply
     * - May have rollback
     * - Must NOT have steps
     */
    private void validateSimpleTemplate(ChangeTemplateFileContent content, String changeId, ValidationResult result) {
        // Validate: apply is required
        if (content.getApply() == null) {
            result.add(new ValidationError(
                    "SimpleTemplate requires 'apply' field",
                    changeId,
                    ENTITY_TYPE
            ));
        }

        // Validate: steps must NOT be present
        if (content.getSteps() != null) {
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
    private void validateSteppableTemplate(ChangeTemplateFileContent content, String changeId, ValidationResult result) {
        // Validate: apply must NOT be present at root level
        if (content.getApply() != null) {
            result.add(new ValidationError(
                    "SteppableTemplate must not have 'apply' at root level. Define 'apply' within each step.",
                    changeId,
                    ENTITY_TYPE
            ));
        }

        // Validate: rollback must NOT be present at root level
        if (content.getRollback() != null) {
            result.add(new ValidationError(
                    "SteppableTemplate must not have 'rollback' at root level. Define 'rollback' within each step.",
                    changeId,
                    ENTITY_TYPE
            ));
        }

        // Validate: steps is required
        Object steps = content.getSteps();
        if (steps == null) {
            result.add(new ValidationError(
                    "SteppableTemplate requires 'steps' field",
                    changeId,
                    ENTITY_TYPE
            ));
            return;
        }

        // Validate each step has apply
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
