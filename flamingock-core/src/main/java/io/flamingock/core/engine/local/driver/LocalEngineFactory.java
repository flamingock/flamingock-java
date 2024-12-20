package io.flamingock.core.engine.local.driver;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.engine.local.LocalEngine;

public interface LocalEngineFactory {

    LocalEngine initializeAndGetEngine(RunnerId runnerId,
                                       CoreConfigurable coreConfiguration,
                                       LocalConfigurable localConfiguration);
}
