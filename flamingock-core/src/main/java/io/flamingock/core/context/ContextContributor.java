package io.flamingock.core.context;

public interface ContextContributor {
    void contributeToContext(ContextInjectable contextInjectable);
}
