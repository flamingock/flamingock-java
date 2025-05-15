package io.flamingock.core.builder;

import io.flamingock.core.system.SystemModule;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultSystemModuleManager implements SystemModuleManager<SystemModule> {

    private final Set<SystemModule> systemModules = new LinkedHashSet<>();

    public void initialize() {
        systemModules.forEach(SystemModule::initialise);
    }

    @Override
    public void add(SystemModule module) {
        systemModules.add(module);
    }

    @Override
    public Iterable<SystemModule> getModules() {
        return systemModules;
    }

}
