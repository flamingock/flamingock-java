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
import io.flamingock.internal.common.core.error.FlamingockException;
import org.jetbrains.annotations.TestOnly;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Manages the discovery, registration, and retrieval of {@link ChangeTemplate} implementations.
 * <p>
 * This class serves two primary purposes in different contexts:
 * <ol>
 *   <li><strong>GraalVM Build-time Context</strong> - The {@link #getRawTemplates()} method is called by
 *       the GraalVM RegistrationFeature to discover all available templates. For each template, 
 *       the feature registers both the template class itself and all classes returned by 
 *       {@link ChangeTemplate#getReflectiveClasses()} for reflection in native images.</li>
 *   <li><strong>Runtime Context</strong> - The {@link #loadTemplates()} method is called during 
 *       Flamingock initialization to populate the internal registry with all available templates 
 *       for use during execution.</li>
 * </ol>
 * <p>
 * Templates are discovered through Java's {@link ServiceLoader} mechanism from two sources:
 * <ul>
 *   <li>Direct implementations of {@link ChangeTemplate} registered via SPI</li>
 *   <li>Templates provided by {@link ChangeTemplateFactory} implementations registered via SPI</li>
 * </ul>
 * <p>
 * <strong>Thread Safety Note:</strong> This class is not thread-safe during initialization. The 
 * {@link #loadTemplates()} method modifies static state and is intended to be called only once 
 * during application startup from a single thread. After initialization, the template registry 
 * is effectively read-only and can be safely accessed concurrently.
 */

public final class ChangeTemplateManager {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("TemplateManager");

    private static final Map<String, ChangeTemplateDefinition> templates = new HashMap<>();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ChangeTemplateManager() {
    }


    /**
     * Loads and registers all available templates from the classpath into the internal registry.
     * <p>
     * This method is intended to be called once during Flamingock runtime initialization.
     * It discovers all templates via {@link #getRawTemplates()} and registers them in the internal
     * registry, indexed by their simple class name.
     * <p>
     * This method is not thread-safe and should be called from a single thread during application
     * startup before any template lookups are performed.
     */
    @SuppressWarnings("unchecked")
    public static void loadTemplates() {
        logger.debug("Registering templates");
        getRawTemplates().forEach(template -> {
            Class<? extends ChangeTemplate<?, ?, ?>> templateClass = (Class<? extends ChangeTemplate<?, ?, ?>>) template.getClass();
            ChangeTemplateDefinition definition = buildDefinition(templateClass);
            templates.put(templateClass.getSimpleName(), definition);
            logger.debug("registered template: {}", templateClass.getSimpleName());
        });

    }

    /**
     * Retrieves a template definition by name from the internal registry.
     * <p>
     * This method is used during runtime to look up template definitions by their simple name.
     * It returns an {@link Optional} that will be empty if no template with the specified
     * name has been registered.
     * <p>
     * This method is thread-safe after initialization (after {@link #loadTemplates()} has been called).
     *
     * @param templateName The simple class name of the template to retrieve
     * @return An Optional containing the template definition if found, or empty if not found
     */
    public static Optional<ChangeTemplateDefinition> getTemplate(String templateName) {
        return Optional.ofNullable(templates.get(templateName));
    }

    public static ChangeTemplateDefinition getTemplateOrFail(String templateName) {
        return Optional.ofNullable(templates.get(templateName))
                .orElseThrow(()-> new FlamingockException(String.format("Template[%s] not found. This is probably because template's name is wrong or template's library not imported", templateName)));
    }

    /**
     * Discovers and returns all available templates from the classpath.
     * <p>
     * This method is used in two contexts:
     * <ul>
     *   <li>By the GraalVM RegistrationFeature during build time to discover templates that need
     *       reflection registration for native image generation</li>
     *   <li>By the {@link #loadTemplates()} method during runtime initialization to populate
     *       the internal template registry</li>
     * </ul>
     * <p>
     * Templates are discovered from two sources:
     * <ol>
     *   <li>Direct implementations of {@link ChangeTemplate} registered via SPI</li>
     *   <li>Templates provided by {@link ChangeTemplateFactory} implementations registered via SPI</li>
     * </ol>
     * <p>
     * This method creates new instances of templates each time it's called and does not modify
     * any internal state.
     *
     * @return A collection of all discovered template instances
     */
    public static Collection<ChangeTemplate<?, ?, ?>> getRawTemplates() {
        logger.debug("Retrieving ChangeTemplates");

        //Loads the ChangeTemplates directly registered with SPI
        List<ChangeTemplate<?, ?, ?>> templateClasses = new ArrayList<>();
        for (ChangeTemplate<?, ?, ?> template : ServiceLoader.load(ChangeTemplate.class)) {
            templateClasses.add(template);
        }

        //Loads the ChangeTemplates from the federated ChangeTemplateFactory, registered with SPI
        for (ChangeTemplateFactory factory : ServiceLoader.load(ChangeTemplateFactory.class)) {
            templateClasses.addAll(factory.getTemplates());
        }
        logger.debug("returning ChangeTemplates");

        return templateClasses;
    }


    /**
     * Adds a template to the internal registry for testing purposes.
     * <p>
     * This method is intended for use in test environments only to register mock or test templates.
     * It directly modifies the internal template registry and is not thread-safe.
     *
     * @param templateName The name to register the template under (typically the simple class name)
     * @param templateClass The template class to register
     */
    @TestOnly
    public static void addTemplate(String templateName, Class<? extends ChangeTemplate<?, ?, ?>> templateClass) {
        ChangeTemplateDefinition definition = buildDefinition(templateClass);
        templates.put(templateName, definition);
    }


    /**
     * Validates the {@code @ChangeTemplate} annotation on the given class and builds a
     * {@link ChangeTemplateDefinition} with pre-resolved metadata.
     *
     * @param templateClass the template class to validate and wrap
     * @return a new ChangeTemplateDefinition
     * @throws FlamingockException if the class is missing the {@code @ChangeTemplate} annotation
     */
    private static ChangeTemplateDefinition buildDefinition(Class<? extends ChangeTemplate<?, ?, ?>> templateClass) {
        io.flamingock.api.annotations.ChangeTemplate annotation =
                templateClass.getAnnotation(io.flamingock.api.annotations.ChangeTemplate.class);
        if (annotation == null) {
            throw new FlamingockException(String.format(
                    "Template class '%s' is missing required @ChangeTemplate annotation",
                    templateClass.getSimpleName()));
        }
        return new ChangeTemplateDefinition(templateClass, annotation.multiStep());
    }
}
