package io.flamingock.core.context;

import io.flamingock.core.api.exception.FlamingockException;

public class NotFoundDependencyException extends FlamingockException {

    public NotFoundDependencyException(String dependencyName) {
        super("Dependency/property named '" + dependencyName + "' was not found in the context.");
    }

    public NotFoundDependencyException(Class<?> dependencyType) {
        super("Dependency/property of type '" + dependencyType.getName() + "' was not found in the context.");
    }

    public NotFoundDependencyException(String name, Class<?> dependencyType) {
        super("Dependency/property named '" + name + "' of type '" + dependencyType.getName() + "' was not found in the context.");
    }
}