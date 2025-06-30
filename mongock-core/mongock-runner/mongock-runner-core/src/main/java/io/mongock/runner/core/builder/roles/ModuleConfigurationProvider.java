package io.mongock.runner.core.builder.roles;

import io.mongock.api.config.MongockConfiguration;
import io.mongock.api.module.MongockModuleConfig;

import java.util.Collection;
import java.util.Collections;

public interface ModuleConfigurationProvider<SELF extends ModuleConfigurationProvider<SELF, CONFIG>, CONFIG extends MongockConfiguration>
    extends Configurable<SELF, CONFIG>, SelfInstanstiator<SELF> {

  default SELF addModule(MongockModuleConfig moduleConfig) {
    return addModules(Collections.singletonList(moduleConfig));
  }

  default SELF addModules(Collection<MongockModuleConfig> moduleConfigs) {
    if (moduleConfigs != null) {
      getConfig().getMongockModules().addAll(moduleConfigs);
    }
    return getInstance();
  }
}
