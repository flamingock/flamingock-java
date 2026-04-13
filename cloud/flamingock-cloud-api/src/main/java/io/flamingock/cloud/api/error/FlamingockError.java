package io.flamingock.cloud.api.error;

import java.util.Map;

public interface FlamingockError {

    String getCode();

    boolean getRecoverable();

    String getPublicMessage();

    String getInternalMessage();

    Map<String, Object> getParameters();
}
