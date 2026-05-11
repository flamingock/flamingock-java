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

import io.flamingock.api.annotations.RollbackTemplate;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.util.ReflectionUtil;
import org.jetbrains.annotations.TestOnly;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Runtime registry of {@link ChangeTemplate} implementations discovered through Java's
 * {@link ServiceLoader} SPI.
 *
 * <p>The runtime entry point is {@link #loadTemplates()} — called once during Flamingock
 * initialization to populate the internal registry. After that, callers look templates up
 * by name via {@link #getTemplate(String)} or {@link #getTemplateOrFail(String)}.
 *
 * <p>This class is intentionally runtime-only. The GraalVM {@code RegistrationFeature}
 * needs a build-time-safe way to enumerate templates without instantiating them (which
 * would fire each template's {@code <clinit>} and pull SLF4J/Logback into the build-time
 * image heap). That enumeration lives inside the {@code flamingock-graalvm} module so it
 * can use the JDK 9+ {@code ServiceLoader.Provider::type} API; this module is Java-8
 * compatible and stays small.
 *
 * <p><strong>Thread Safety Note:</strong> not thread-safe during initialization.
 * {@link #loadTemplates()} modifies static state and is intended to be called only once
 * during application startup from a single thread. After initialization, the template
 * registry is effectively read-only and can be safely accessed concurrently.
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
     *
     * <p>Runtime-only. Iterates {@link ServiceLoader} for {@link ChangeTemplate}, which
     * instantiates each provider — that's fine here because we're at runtime; static
     * initializers (loggers etc.) are allowed to fire. The build-time path that must
     * <em>not</em> instantiate templates lives in the {@code flamingock-graalvm} module.
     *
     * <p>Not thread-safe; call once during application startup from a single thread, before
     * any template lookups.
     */
    @SuppressWarnings("unchecked")
    public static void loadTemplates() {
        logger.debug("Registering templates");
        for (ChangeTemplate<?, ?, ?> template : ServiceLoader.load(ChangeTemplate.class)) {
            Class<? extends ChangeTemplate<?, ?, ?>> templateClass =
                    (Class<? extends ChangeTemplate<?, ?, ?>>) template.getClass();
            ChangeTemplateDefinition definition = buildDefinition(templateClass);
            templates.put(definition.getId(), definition);
            logger.debug("registered template: {}", definition.getId());
        }
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
     * Adds a template to the internal registry for testing purposes.
     * <p>
     * This method is intended for use in test environments only to register mock or test templates.
     * It directly modifies the internal template registry and is not thread-safe.
     * The template is registered under its {@code @ChangeTemplate} annotation's {@code id}.
     *
     * @param templateClass The template class to register
     */
    @TestOnly
    public static void addTemplate(Class<? extends ChangeTemplate<?, ?, ?>> templateClass) {
        ChangeTemplateDefinition definition = buildDefinition(templateClass);
        templates.put(definition.getId(), definition);
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
        String id = annotation.name();
        if (id == null || id.trim().isEmpty()) {
            throw new FlamingockException(String.format(
                    "Template class '%s' has a blank @ChangeTemplate id. The id must be a non-empty string",
                    templateClass.getSimpleName()));
        }
        if (!ReflectionUtil.findFirstAnnotatedMethod(templateClass, RollbackTemplate.class).isPresent()) {
            throw new FlamingockException(String.format(
                    "Template class '%s' is missing required @RollbackTemplate method",
                    templateClass.getSimpleName()));
        }
        return new ChangeTemplateDefinition(id, templateClass, annotation.multiStep(), annotation.rollbackPayloadRequired());
    }
}
