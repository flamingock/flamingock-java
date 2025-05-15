package io.flamingock.core.builder;

import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.cloud.CloudDriver;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.runtime.dependency.DependencyContext;

import java.util.Optional;

public interface Driver {

    default boolean isCloud() {
        return false;
    }

    void initialize(DependencyContext dependencyContext);

    static Driver getDriver() {
        Optional<CloudDriver> cloudDriver = CloudDriver.getDriver();
        if(cloudDriver.isPresent()) {
            return cloudDriver.get();
        }

        Optional<LocalDriver> communityDriver = LocalDriver.getDriver();
        if(communityDriver.isPresent()) {
            return communityDriver.get();
        }

        throw new FlamingockException(
                "No compatible edition detected. Make sure the Cloud Edition or a supported Community Edition is included in your dependencies."
        );
    }
}
