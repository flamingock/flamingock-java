package io.flamingock.core.cloud;

import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.core.engine.ConnectionEngine;

public interface CloudEngine extends ConnectionEngine {

    EnvironmentId getEnvironmentId();

    ServiceId getServiceId();

    String getJwt();
}
