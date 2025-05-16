package io.flamingock.core.plugin;

import io.flamingock.core.context.ContextInitializable;

import java.util.Collection;

public interface PluginManager extends ContextInitializable {
    Collection<Plugin> getPlugins();
}
