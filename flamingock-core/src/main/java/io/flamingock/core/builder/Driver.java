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

package io.flamingock.core.builder;

import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.cloud.CloudDriver;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.context.ContextInitializable;
import io.flamingock.core.engine.ConnectionEngine;

import java.util.Optional;

public interface Driver<ENGINE extends ConnectionEngine> extends ContextInitializable {

    default boolean isCloud() {
        return false;
    }

    ENGINE getEngine();

    static Driver<?> getDriver() {
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
