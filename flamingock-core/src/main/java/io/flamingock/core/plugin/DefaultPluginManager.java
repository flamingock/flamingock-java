package io.flamingock.core.plugin;

import io.flamingock.core.context.DependencyContext;

import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DefaultPluginManager implements PluginManager {

    private List<Plugin> frameworkPlugins;

    @Override
    public void initialize(DependencyContext dependencyContext) {
        frameworkPlugins = StreamSupport
                .stream(ServiceLoader.load(Plugin.class).spliterator(), false)
                .peek(frameworkPlugin -> frameworkPlugin.initialize(dependencyContext))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Plugin> getPlugins() {
        return frameworkPlugins;
    }
}
