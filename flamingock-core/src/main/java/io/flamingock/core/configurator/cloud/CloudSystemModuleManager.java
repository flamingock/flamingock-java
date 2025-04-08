package io.flamingock.core.configurator.cloud;

import io.flamingock.core.system.CloudSystemModule;
import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.core.configurator.SystemModuleManager;

import java.util.LinkedHashSet;
import java.util.Set;

public class CloudSystemModuleManager implements SystemModuleManager<CloudSystemModule> {

    private final Set<CloudSystemModule> systemModules = new LinkedHashSet<>();

    public void initialize(EnvironmentId environmentId, ServiceId serviceId, String jwt, String serverHost) {
        systemModules.forEach(m -> m.initialise(environmentId, serviceId, jwt, serverHost));
    }

    @Override
    public void add(CloudSystemModule module) {
        systemModules.add(module);
    }

    @Override
    public Set<CloudSystemModule> getModules() {
        return systemModules;
    }

}
