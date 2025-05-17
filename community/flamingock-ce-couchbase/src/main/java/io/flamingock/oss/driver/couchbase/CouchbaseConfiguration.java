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

package io.flamingock.oss.driver.couchbase;

import io.flamingock.internal.core.community.driver.DriverConfigurable;
import io.flamingock.internal.core.context.ContextResolver;

public class CouchbaseConfiguration implements DriverConfigurable {

    private boolean autoCreate = true;

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public void mergeConfig(ContextResolver dependencyContext) {
        dependencyContext.getPropertyAs("couchbase.autoCreate", boolean.class)
                .ifPresent(this::setAutoCreate);
    }
}
