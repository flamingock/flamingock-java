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

package io.flamingock.core.plugin;

import io.flamingock.core.context.ContextInitializable;
import io.flamingock.core.event.EventPublisher;
import io.flamingock.core.context.ContextResolver;
import io.flamingock.core.task.filter.TaskFilter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface Plugin extends ContextInitializable {

    default Optional<EventPublisher> getEventPublisher() {
        return Optional.empty();
    }

    default Optional<ContextResolver> getDependencyContext() {
        return Optional.empty();
    }

    default List<TaskFilter> getTaskFilters() {
        return Collections.emptyList();
    }

}
