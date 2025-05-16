package io.flamingock.core.system;

import io.flamingock.core.context.ContextInjectable;
import io.flamingock.core.context.ContextResolver;
import io.flamingock.core.context.DependencyInjectable;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultSystemModuleManager implements SystemModuleManager {

    private final Set<SystemModule> systemModules = new LinkedHashSet<>();

    @Override
    public void initialize(ContextResolver dependencyContext) {
        systemModules.forEach(module -> module.initialize(dependencyContext));
    }

    @Override
    public void add(SystemModule module) {
        systemModules.add(module);
    }

    @Override
    public Iterable<SystemModule> getModules() {
        return systemModules;
    }

    @Override
    public void contributeToContext(ContextInjectable dependencyInjectable) {
        systemModules.forEach(module -> module.contributeToContext(dependencyInjectable));
    }
}
