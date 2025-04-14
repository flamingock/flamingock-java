package io.flamingock.core.api.template;

import io.flamingock.core.api.metadata.ReflectionMetadataProvider;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Interface representing a reusable change template with configuration of type {@code CONFIG}.
 * <p>
 * This interface is commonly implemented by classes that act as templates for Change Units
 * where a specific configuration needs to be injected and managed independently.
 *
 * @param <CONFIG> The type of configuration this template works with.
 */
public interface ChangeTemplate<CONFIG extends ChangeTemplateConfig<?, ?>> extends ReflectionMetadataProvider {

    /**
     * Injects the configuration for this template.
     *
     * @param configuration Configuration instance of type {@code CONFIG}.
     */
    void setConfiguration(CONFIG configuration);

    /**
     * Returns the {@link Class} representing the configuration type {@code CONFIG} that this template is bound to.
     * <p>
     * This method uses Java Reflection to inspect the actual generic type argument used in the concrete
     * implementation of {@code ChangeTemplate<CONFIG>}. This allows the framework or caller to determine
     * the expected configuration class without the need to explicitly pass it.
     *
     * <p><strong>Usage example:</strong>
     * <pre>{@code
     * class MyTemplate implements ChangeTemplate<MyConfig> {
     *     ...
     * }
     * ChangeTemplate<?> template = new MyTemplate();
     * Class<?> configClass = template.getConfigClass(); // returns MyConfig.class
     * }</pre>
     *
     * <p><strong>Limitations:</strong>
     * <ul>
     *   <li>This method relies on Java's generic type information being available at runtime.
     *       Due to type erasure, it only works if the concrete class directly implements
     *       {@code ChangeTemplate<CONFIG>} with an actual type (e.g., {@code MyTemplate implements ChangeTemplate<MyConfig>}).
     *   </li>
     *   <li>If the generic type {@code CONFIG} is declared or inferred indirectly (e.g., via abstract superclasses
     *       or through anonymous classes), the returned type may be {@code Object.class} or throw an exception.</li>
     *   <li>In some complex scenarios (e.g., proxy classes, lambdas, or advanced frameworks like Spring AOP),
     *       the actual generic interface may not be directly visible on {@code this.getClass()}, and this method
     *       may fail to resolve the correct type.</li>
     * </ul>
     *
     * <p>In cases where the type cannot be reliably resolved, consider overriding this method
     * in the concrete implementation to return the expected {@link Class} explicitly.
     *
     * @return The {@link Class} object representing the configuration type {@code CONFIG}.
     * @throws IllegalStateException if the configuration type cannot be determined.
     */
    @SuppressWarnings("unchecked")
    default Class<CONFIG> getConfigClass() {
        for (Type type : this.getClass().getGenericInterfaces()) {
            if (type instanceof ParameterizedType) {
                ParameterizedType pType = (ParameterizedType) type;
                if (pType.getRawType() instanceof Class &&
                        ChangeTemplate.class.isAssignableFrom((Class<?>) pType.getRawType())) {
                    return (Class<CONFIG>) pType.getActualTypeArguments()[0];
                }
            }
        }
        throw new IllegalStateException("Cannot determine generic type");
    }
}
