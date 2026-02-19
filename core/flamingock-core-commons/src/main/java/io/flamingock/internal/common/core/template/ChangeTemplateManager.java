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
package io.flamingock.internal.common.core.template;

import io.flamingock.api.template.ChangeTemplate;
import org.jetbrains.annotations.TestOnly;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the registration and retrieval of {@link ChangeTemplate} implementations.
 *
 * <p>This class serves as a central registry for templates, which are initialized from
 * {@link TemplateMetadata} discovered during annotation processing. Templates are
 * indexed by their unique ID (from {@code @ChangeTemplate.id()}).
 *
 * <p>The initialization flow:
 * <ol>
 *   <li>Annotation processor discovers templates and serializes metadata to FlamingockMetadata</li>
 *   <li>At runtime, {@link #initializeFromMetadata(List)} loads classes from metadata</li>
 *   <li>Templates are looked up by ID via {@link #getTemplate(String)}</li>
 * </ol>
 *
 * <p><strong>Thread Safety Note:</strong> This class is not thread-safe during initialization.
 * The {@link #initializeFromMetadata(List)} method modifies static state and should be called
 * only once during application startup. After initialization, the registry is read-only and
 * can be safely accessed concurrently.
 */
public final class ChangeTemplateManager {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("TemplateManager");

    /**
     * Internal storage for template entries, indexed by template ID.
     */
    private static final Map<String, TemplateEntry> templates = new HashMap<>();

    /**
     * Flag to track if templates have been initialized.
     */
    private static boolean initialized = false;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ChangeTemplateManager() {
    }

    /**
     * Initializes the template registry from metadata discovered during annotation processing.
     *
     * <p>This method loads template classes using their fully qualified class names from metadata
     * and registers them by their unique ID. It should be called once during Flamingock
     * initialization before any template lookups are performed.
     *
     * <p>If templates are already initialized, this method returns immediately.
     *
     * @param templateMetadataList list of template metadata from annotation processing
     * @throws RuntimeException if a template class cannot be loaded
     */
    @SuppressWarnings("unchecked")
    public static void initializeFromMetadata(List<TemplateMetadata> templateMetadataList) {
        if (initialized) {
            logger.debug("Templates already initialized, skipping");
            return;
        }

        if (templateMetadataList == null || templateMetadataList.isEmpty()) {
            logger.debug("No templates to initialize");
            initialized = true;
            return;
        }

        logger.debug("Initializing templates from metadata");

        for (TemplateMetadata meta : templateMetadataList) {
            try {
                Class<?> clazz = Class.forName(meta.getFullyQualifiedClassName());
                Class<? extends ChangeTemplate<?, ?, ?>> templateClass =
                        (Class<? extends ChangeTemplate<?, ?, ?>>) clazz;

                TemplateEntry entry = new TemplateEntry(templateClass, meta);

                // Register by ID only
                templates.put(meta.getId(), entry);
                logger.debug("Registered template: {} -> {}", meta.getId(), meta.getFullyQualifiedClassName());

            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Template class not found: " + meta.getFullyQualifiedClassName(), e);
            } catch (ClassCastException e) {
                throw new RuntimeException("Class " + meta.getFullyQualifiedClassName() +
                        " does not implement ChangeTemplate", e);
            }
        }

        initialized = true;
        logger.debug("Initialized {} templates", templates.size());
    }

    /**
     * Retrieves a template class by its unique ID.
     *
     * <p>Template IDs are defined in the {@code @ChangeTemplate.id()} annotation
     * and used in YAML files to reference templates (e.g., {@code template: sql}).
     *
     * @param templateId the unique template identifier
     * @return an Optional containing the template class if found, or empty if not found
     */
    public static Optional<Class<? extends ChangeTemplate<?, ?, ?>>> getTemplate(String templateId) {
        TemplateEntry entry = templates.get(templateId);
        return entry != null ? Optional.of(entry.templateClass) : Optional.empty();
    }

    /**
     * Retrieves template metadata by its unique ID.
     *
     * @param templateId the unique template identifier
     * @return an Optional containing the template metadata if found, or empty if not found
     */
    public static Optional<TemplateMetadata> getTemplateMetadata(String templateId) {
        TemplateEntry entry = templates.get(templateId);
        return entry != null ? Optional.of(entry.metadata) : Optional.empty();
    }

    /**
     * Checks if the template manager has been initialized.
     *
     * @return true if templates have been initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Adds a template to the internal registry for testing purposes.
     *
     * <p>This method is intended for use in test environments only to register mock or test templates.
     * It directly modifies the internal template registry and is not thread-safe.
     *
     * @param templateId    the unique template identifier
     * @param templateClass the template class to register
     * @param metadata      the template metadata
     */
    @TestOnly
    public static void addTemplate(String templateId, Class<? extends ChangeTemplate<?, ?, ?>> templateClass,
                                   TemplateMetadata metadata) {
        templates.put(templateId, new TemplateEntry(templateClass, metadata));
    }

    /**
     * Adds a template to the internal registry for testing purposes.
     *
     * <p>This is a convenience overload that extracts multiStep from the @ChangeTemplate annotation.
     *
     * @param templateId    the unique template identifier
     * @param templateClass the template class to register
     */
    @TestOnly
    public static void addTemplate(String templateId, Class<? extends ChangeTemplate<?, ?, ?>> templateClass) {
        // Extract multiStep from @ChangeTemplate annotation if present
        boolean multiStep = false;
        io.flamingock.api.annotations.ChangeTemplate annotation =
                templateClass.getAnnotation(io.flamingock.api.annotations.ChangeTemplate.class);
        if (annotation != null) {
            multiStep = annotation.multiStep();
        }
        TemplateMetadata metadata = new TemplateMetadata(templateId, multiStep, templateClass.getName());
        templates.put(templateId, new TemplateEntry(templateClass, metadata));
    }

    /**
     * Clears all templates from the internal registry and resets initialization state.
     *
     * <p>This method is intended for use in test environments only to reset the template registry
     * between tests, ensuring test isolation.
     */
    @TestOnly
    public static void clearTemplates() {
        templates.clear();
        initialized = false;
    }

    /**
     * Internal class to hold template class and metadata together.
     */
    private static class TemplateEntry {
        final Class<? extends ChangeTemplate<?, ?, ?>> templateClass;
        final TemplateMetadata metadata;

        TemplateEntry(Class<? extends ChangeTemplate<?, ?, ?>> templateClass, TemplateMetadata metadata) {
            this.templateClass = templateClass;
            this.metadata = metadata;
        }
    }
}
