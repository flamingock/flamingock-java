package io.flamingock.core.api.metadata;

import java.util.Collection;

/**
 * Provides metadata about classes that require reflective access.
 * <p>
 * Implementations of this interface declare a collection of classes that should be registered
 * for reflection at build time—commonly used in native image generation processes such as GraalVM.
 * </p>
 * <p>
 * Optionally, the implementing class itself can also be marked for reflection if needed.
 * </p>
 */
public interface ReflectionMetadataProvider {

    /**
     * Returns a collection of classes that should be registered for reflective access.
     * <p>
     * This method does not perform any registration itself—it only declares the classes
     * that need to be registered. The returned collection does not require a specific
     * ordering and may contain any number of class references.
     * </p>
     *
     * @return a collection of classes to be registered for reflection
     */
    Collection<Class<?>> getReflectiveClasses();

    /**
     * Indicates whether the implementing class itself should also be registered for reflection.
     * <p>
     * This is useful when the provider is instantiated reflectively or used in a context
     * (e.g., service loader) that requires reflective access to its constructor or methods.
     * </p>
     *
     * @return {@code true} if the implementing class should also be registered for reflection; {@code false} otherwise
     */
    boolean shouldRegisterSelf();
}