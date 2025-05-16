package io.flamingock.core.context;

public interface ContextInitializable {
    void initialize(ContextResolver dependencyContext);
}
