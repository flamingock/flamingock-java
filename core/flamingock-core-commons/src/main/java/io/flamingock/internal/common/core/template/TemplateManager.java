package io.flamingock.internal.common.core.template;

import io.flamingock.api.template.ChangeTemplate;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li><strong>GraalVM Build-time Context</strong> - The {@link #getTemplates()} method is called by 
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

public final class TemplateManager {

    private static final Logger logger = LoggerFactory.getLogger(TemplateManager.class);

    private static final Map<String, Class<? extends ChangeTemplate<?, ?, ?>>> templates = new HashMap<>();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TemplateManager() {
    }


    /**
     * Loads and registers all available templates from the classpath into the internal registry.
     * <p>
     * This method is intended to be called once during Flamingock runtime initialization.
     * It discovers all templates via {@link #getTemplates()} and registers them in the internal
     * registry, indexed by their simple class name.
     * <p>
     * This method is not thread-safe and should be called from a single thread during application
     * startup before any template lookups are performed.
     */
    @SuppressWarnings("unchecked")
    public static void loadTemplates() {
        logger.debug("Registering templates");
        getTemplates().forEach(template -> {
            Class<? extends ChangeTemplate<?, ?, ?>> templateClass = (Class<? extends ChangeTemplate<?, ?, ?>>) template.getClass();
            templates.put(templateClass.getSimpleName(), templateClass);
            logger.debug("registered template: {}", templateClass.getSimpleName());
        });

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
    public static Collection<ChangeTemplate<?, ?, ?>> getTemplates() {
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
        templates.put(templateName, templateClass);
    }

    /**
     * Retrieves a template class by name from the internal registry.
     * <p>
     * This method is used during runtime to look up template classes by their simple name.
     * It returns an {@link Optional} that will be empty if no template with the specified
     * name has been registered.
     * <p>
     * This method is thread-safe after initialization (after {@link #loadTemplates()} has been called).
     *
     * @param templateName The simple class name of the template to retrieve
     * @return An Optional containing the template class if found, or empty if not found
     */
    public static Optional<Class<? extends ChangeTemplate<?, ?, ?>>> getTemplate(String templateName) {
        return Optional.ofNullable(templates.get(templateName));
    }
}
