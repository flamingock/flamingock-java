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


import io.flamingock.core.event.model.IPipelineCompletedEvent;
import io.flamingock.core.event.model.IPipelineFailedEvent;
import io.flamingock.core.event.model.IPipelineIgnoredEvent;
import io.flamingock.core.event.model.IPipelineStartedEvent;
import io.flamingock.core.event.model.IStageCompletedEvent;
import io.flamingock.core.event.model.IStageFailedEvent;
import io.flamingock.core.event.model.IStageIgnoredEvent;
import io.flamingock.core.event.model.IStageStartedEvent;

import java.util.function.Consumer;

public interface StandaloneConfigurator<HOLDER> {


    /**
     * Manually adds a dependency to be used in the  changeUnits, which can be retrieved by its own type
     *
     * @param instance dependency
     * @return builder for fluent interface
     */
    HOLDER addDependency(Object instance);

    /**
     * Manually adds a dependency to be used in the  changeUnits, which can be retrieved by a name
     *
     * @param name     name for which it should be searched by
     * @param instance dependency
     * @return builder for fluent interface
     */
    HOLDER addDependency(String name, Object instance);

    /**
     * Manually adds a dependency to be used in the  changeUnits, which can be retrieved by a type
     *
     * @param type     type for which it should be searched by
     * @param instance dependency
     * @return builder for fluent interface
     */
    HOLDER addDependency(Class<?> type, Object instance);

    /**
     * Manually adds a dependency to be used in the  changeUnits, which can be retrieved by a type or name
     *
     * @param name     name for which it should be searched by
     * @param type     type for which it should be searched by
     * @param instance dependency
     * @return builder for fluent interface
     */
    HOLDER addDependency(String name, Class<?> type, Object instance);

    //TODO javadoc
    HOLDER setPipelineStartedListener(Consumer<IPipelineStartedEvent> listener);

    //TODO javadoc
    HOLDER setPipelineCompletedListener(Consumer<IPipelineCompletedEvent> listener);

    //TODO javadoc
    HOLDER setPipelineIgnoredListener(Consumer<IPipelineIgnoredEvent> listener);

    //TODO javadoc
    HOLDER setPipelineFailedListener(Consumer<IPipelineFailedEvent> listener);

    //TODO javadoc
    HOLDER setStageStartedListener(Consumer<IStageStartedEvent> listener);

    //TODO javadoc
    HOLDER setStageCompletedListener(Consumer<IStageCompletedEvent> listener);

    //TODO javadoc
    HOLDER setStageIgnoredListener(Consumer<IStageIgnoredEvent> listener);

    //TODO javadoc
    HOLDER setStageFailedListener(Consumer<IStageFailedEvent> listener);

    Consumer<IPipelineStartedEvent> getPipelineStartedListener();

    //TODO javadoc
    Consumer<IPipelineCompletedEvent> getPipelineCompletedListener();

    //TODO javadoc
    Consumer<IPipelineIgnoredEvent> getPipelineIgnoredListener();

    //TODO javadoc
    Consumer<IPipelineFailedEvent> getPipelineFailureListener();

    Consumer<IStageStartedEvent> getStageStartedListener();

    //TODO javadoc
    Consumer<IStageCompletedEvent> getStageCompletedListener();

    //TODO javadoc
    Consumer<IStageIgnoredEvent> getStageIgnoredListener();

    //TODO javadoc
    Consumer<IStageFailedEvent> getStageFailureListener();
}
