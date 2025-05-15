package io.flamingock.core.cloud;

import io.flamingock.commons.utils.Pair;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.builder.Driver;
import io.flamingock.core.community.driver.OverridesDrivers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

public interface CloudDriver extends Driver<CloudEngine> {

    @Override
    default boolean isCloud() {
        return true;
    }

    static Optional<CloudDriver> getDriver() {

        Pair<CloudDriver, Set<Class<?>>> current = null;//contains driver and the list of precedent classes
        for (CloudDriver driver : ServiceLoader.load(CloudDriver.class)) {

            Set<Class<?>> precedentClasses;
            OverridesDrivers annotation = driver.getClass().getAnnotation(OverridesDrivers.class);

            if (annotation != null && annotation.value() != null) {
                precedentClasses = new HashSet<>(Arrays.asList(annotation.value()));
            } else {
                precedentClasses = Collections.emptySet();
            }

            if (current == null || precedentClasses.contains(current.getFirst().getClass())) {
                current = new Pair<>(driver, precedentClasses);
            } else if (!current.getSecond().contains(driver.getClass())) {
                throw new FlamingockException("More than one cloud driver is injected, without a clear hierarchy");
            }

        }

        return Optional.ofNullable(current != null ? current.getFirst() : null);
    }
}
