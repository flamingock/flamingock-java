package io.flamingock.core.runtime.dependency;

import io.flamingock.core.runtime.dependency.exception.ForbiddenParameterException;

import java.util.Optional;
import java.util.function.Function;

public class DependencyContextWrapper implements DependencyContext{

    private final Function<Class<?>, Object> byTypeFunc;
    private final Function<String, Object> byNameFunc;

    public DependencyContextWrapper(Function<Class<?>, Object> byTypeFunc,
                                    Function<String, Object> byNameFunc) {
        this.byTypeFunc = byTypeFunc;
        this.byNameFunc = byNameFunc;
    }
    @Override
    public Optional<Dependency> getDependency(Class<?> type) throws ForbiddenParameterException {
        try {
            return Optional.of(new Dependency(type, byTypeFunc.apply(type)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Dependency> getDependency(String name) throws ForbiddenParameterException {
        try {
            return Optional.of(new Dependency(byNameFunc.apply(name)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
