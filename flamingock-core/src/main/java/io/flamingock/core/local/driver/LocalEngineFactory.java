package io.flamingock.core.local.driver;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.local.LocalEngine;

public interface LocalEngineFactory {

    LocalEngine initializeAndGetEngine(RunnerId runnerId,
                                       CoreConfigurable coreConfiguration,
                                       LocalConfigurable localConfiguration);
}
