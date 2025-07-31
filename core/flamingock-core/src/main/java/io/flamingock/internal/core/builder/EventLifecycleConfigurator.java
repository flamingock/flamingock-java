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

package io.flamingock.internal.core.builder;


import io.flamingock.internal.core.event.model.IPipelineCompletedEvent;
import io.flamingock.internal.core.event.model.IPipelineFailedEvent;
import io.flamingock.internal.core.event.model.IPipelineIgnoredEvent;
import io.flamingock.internal.core.event.model.IPipelineStartedEvent;
import io.flamingock.internal.core.event.model.IStageCompletedEvent;
import io.flamingock.internal.core.event.model.IStageFailedEvent;
import io.flamingock.internal.core.event.model.IStageIgnoredEvent;
import io.flamingock.internal.core.event.model.IStageStartedEvent;

import java.util.function.Consumer;

public interface EventLifecycleConfigurator<HOLDER> {

    /**
     * Sets the listener to be notified when a pipeline is started.
     *
     * @param listener consumer of the pipeline started event
     * @return fluent builder
     */
    HOLDER setPipelineStartedListener(Consumer<IPipelineStartedEvent> listener);

    /**
     * Sets the listener to be notified when a pipeline is successfully completed.
     *
     * @param listener consumer of the pipeline completed event
     * @return fluent builder
     */
    HOLDER setPipelineCompletedListener(Consumer<IPipelineCompletedEvent> listener);

    /**
     * Sets the listener to be notified when a pipeline is ignored.
     *
     * @param listener consumer of the pipeline ignored event
     * @return fluent builder
     */
    HOLDER setPipelineIgnoredListener(Consumer<IPipelineIgnoredEvent> listener);

    /**
     * Sets the listener to be notified when a pipeline fails.
     *
     * @param listener consumer of the pipeline failed event
     * @return fluent builder
     */
    HOLDER setPipelineFailedListener(Consumer<IPipelineFailedEvent> listener);

    /**
     * Sets the listener to be notified when a stage is started.
     *
     * @param listener consumer of the stage started event
     * @return fluent builder
     */
    HOLDER setStageStartedListener(Consumer<IStageStartedEvent> listener);

    /**
     * Sets the listener to be notified when a stage is successfully completed.
     *
     * @param listener consumer of the stage completed event
     * @return fluent builder
     */
    HOLDER setStageCompletedListener(Consumer<IStageCompletedEvent> listener);

    /**
     * Sets the listener to be notified when a stage is ignored.
     *
     * @param listener consumer of the stage ignored event
     * @return fluent builder
     */
    HOLDER setStageIgnoredListener(Consumer<IStageIgnoredEvent> listener);

    /**
     * Sets the listener to be notified when a stage fails.
     *
     * @param listener consumer of the stage failed event
     * @return fluent builder
     */
    HOLDER setStageFailedListener(Consumer<IStageFailedEvent> listener);

    /**
     * Gets the registered pipeline started listener.
     *
     * @return pipeline started event listener
     */
    Consumer<IPipelineStartedEvent> getPipelineStartedListener();

    /**
     * Gets the registered pipeline completed listener.
     *
     * @return pipeline completed event listener
     */
    Consumer<IPipelineCompletedEvent> getPipelineCompletedListener();

    /**
     * Gets the registered pipeline ignored listener.
     *
     * @return pipeline ignored event listener
     */
    Consumer<IPipelineIgnoredEvent> getPipelineIgnoredListener();

    /**
     * Gets the registered pipeline failed listener.
     *
     * @return pipeline failed event listener
     */
    Consumer<IPipelineFailedEvent> getPipelineFailureListener();

    /**
     * Gets the registered stage started listener.
     *
     * @return stage started event listener
     */
    Consumer<IStageStartedEvent> getStageStartedListener();

    /**
     * Gets the registered stage completed listener.
     *
     * @return stage completed event listener
     */
    Consumer<IStageCompletedEvent> getStageCompletedListener();

    /**
     * Gets the registered stage ignored listener.
     *
     * @return stage ignored event listener
     */
    Consumer<IStageIgnoredEvent> getStageIgnoredListener();

    /**
     * Gets the registered stage failed listener.
     *
     * @return stage failed event listener
     */
    Consumer<IStageFailedEvent> getStageFailureListener();

}
