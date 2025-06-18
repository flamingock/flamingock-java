/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.internal.core.cloud;

import io.flamingock.internal.util.Pair;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.Driver;
import io.flamingock.internal.core.community.driver.OverridesDrivers;

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
