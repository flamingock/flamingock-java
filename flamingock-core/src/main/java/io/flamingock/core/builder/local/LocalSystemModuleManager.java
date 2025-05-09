package io.flamingock.core.builder.local;

import io.flamingock.core.system.LocalSystemModule;
import io.flamingock.core.builder.SystemModuleManager;

import java.util.LinkedHashSet;
import java.util.Set;

public class LocalSystemModuleManager implements SystemModuleManager<LocalSystemModule> {

    private final Set<LocalSystemModule> systemModules = new LinkedHashSet<>();

    public void initialize() {
        systemModules.forEach(LocalSystemModule::initialise);
    }

    @Override
    public void add(LocalSystemModule module) {
        systemModules.add(module);
    }

    @Override
    public Iterable<LocalSystemModule> getModules() {
        return systemModules;
    }

}
