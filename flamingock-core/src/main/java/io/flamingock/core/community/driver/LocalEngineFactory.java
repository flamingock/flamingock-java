package io.flamingock.core.community.driver;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.builder.core.CoreConfigurable;
import io.flamingock.core.builder.local.CommunityConfigurable;
import io.flamingock.core.community.LocalEngine;

public interface LocalEngineFactory {

    LocalEngine initializeAndGetEngine(RunnerId runnerId,
                                       CoreConfigurable coreConfiguration,
                                       CommunityConfigurable localConfiguration);
}
