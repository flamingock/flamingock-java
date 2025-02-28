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

package io.flamingock.core.task.descriptor;

import java.util.Objects;
import java.util.Optional;

public abstract class AbstractLoadedTask implements LoadedTask {

    private final String id;

    private final String order;

    private final boolean runAlways;
    
    private final boolean transactional;

    public AbstractLoadedTask(String id,
                              String order,
                              boolean runAlways,
                              boolean transactional) {
        this.id = id;
        this.order = order;
        this.runAlways = runAlways;
        this.transactional = transactional;
    }

    @Override
    public String getId() {
        return id;
    }


    @Override
    public boolean isRunAlways() {
        return runAlways;
    }

    @Override
    public boolean isTransactional() {
        return transactional;
    }

    @Override
    public Optional<String> getOrder() {
        return Optional.ofNullable(order);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractLoadedTask)) return false;
        AbstractLoadedTask that = (AbstractLoadedTask) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
