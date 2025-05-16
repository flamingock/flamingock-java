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


import io.flamingock.commons.utils.Property;
import io.flamingock.core.event.model.IPipelineCompletedEvent;
import io.flamingock.core.event.model.IPipelineFailedEvent;
import io.flamingock.core.event.model.IPipelineIgnoredEvent;
import io.flamingock.core.event.model.IPipelineStartedEvent;
import io.flamingock.core.event.model.IStageCompletedEvent;
import io.flamingock.core.event.model.IStageFailedEvent;
import io.flamingock.core.event.model.IStageIgnoredEvent;
import io.flamingock.core.event.model.IStageStartedEvent;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public interface ContextConfigurator<HOLDER> {

    /**
     * Manually adds a dependency to be used in the change units, which can be retrieved by its own type.
     *
     * @param instance the dependency instance
     * @return fluent builder
     */
    HOLDER addDependency(Object instance);

    /**
     * Manually adds a dependency to be used in the change units, which can be retrieved by a name.
     *
     * @param name     name under which the dependency will be registered
     * @param instance the dependency instance
     * @return fluent builder
     */
    HOLDER addDependency(String name, Object instance);

    /**
     * Manually adds a dependency to be used in the change units, which can be retrieved by a type.
     *
     * @param type     type under which the dependency will be registered
     * @param instance the dependency instance
     * @return fluent builder
     */
    HOLDER addDependency(Class<?> type, Object instance);

    /**
     * Manually adds a dependency to be used in the change units, retrievable by both name and type.
     *
     * @param name     name under which the dependency will be registered
     * @param type     type under which the dependency will be registered
     * @param instance the dependency instance
     * @return fluent builder
     */
    HOLDER addDependency(String name, Class<?> type, Object instance);

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

    HOLDER setProperty(Property property);

    HOLDER setProperty(String key, String value);

    HOLDER setProperty(String key, Boolean value);

    HOLDER setProperty(String key, Integer value);

    HOLDER setProperty(String key, Float value);

    HOLDER setProperty(String key, Long value);

    HOLDER setProperty(String key, Double value);

    HOLDER setProperty(String key, UUID value);

    HOLDER setProperty(String key, Currency value);

    HOLDER setProperty(String key, Locale value);

    HOLDER setProperty(String key, Charset value);

    HOLDER setProperty(String key, File value);

    HOLDER setProperty(String key, Path value);

    HOLDER setProperty(String key, InetAddress value);

    HOLDER setProperty(String key, URL value);

    HOLDER setProperty(String key, URI value);

    HOLDER setProperty(String key, Duration value);

    HOLDER setProperty(String key, Period value);

    HOLDER setProperty(String key, Instant value);

    HOLDER setProperty(String key, LocalDate value);

    HOLDER setProperty(String key, LocalTime value);

    HOLDER setProperty(String key, LocalDateTime value);

    HOLDER setProperty(String key, ZonedDateTime value);

    HOLDER setProperty(String key, OffsetDateTime value);

    HOLDER setProperty(String key, OffsetTime value);

    HOLDER setProperty(String key, java.util.Date value);

    HOLDER setProperty(String key, java.sql.Date value);

    HOLDER setProperty(String key, Time value);

    HOLDER setProperty(String key, Timestamp value);

    HOLDER setProperty(String key, String[] value);

    HOLDER setProperty(String key, Integer[] value);

    HOLDER setProperty(String key, Long[] value);

    HOLDER setProperty(String key, Double[] value);

    HOLDER setProperty(String key, Float[] value);

    HOLDER setProperty(String key, Boolean[] value);

    HOLDER setProperty(String key, Byte[] value);

    HOLDER setProperty(String key, Short[] value);

    HOLDER setProperty(String key, Character[] value);

    <T extends Enum<T>> HOLDER setProperty(String key, T value);


}
