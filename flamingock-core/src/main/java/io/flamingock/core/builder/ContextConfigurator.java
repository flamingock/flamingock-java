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
import io.flamingock.core.runtime.dependency.Dependency;

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
import java.util.Date;
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


    default HOLDER setProperty(String key, String value) {
        return addDependency(key, String.class, value);
    }

    default HOLDER setProperty(String key, Boolean value) {
        return addDependency(key,Boolean.class, value);
    }

    default HOLDER setProperty(String key, Integer value) {
        return addDependency(key, Integer.class, value);
    }

    default HOLDER setProperty(String key, Float value) {
        return addDependency(key, Float.class, value);
    }

    default HOLDER setProperty(String key, Long value) {
        return addDependency(key, Long.class, value);
    }

    default HOLDER setProperty(String key, Double value) {
        return addDependency(key, Double.class, value);
    }

    default HOLDER setProperty(String key, UUID value) {
        return addDependency(key, UUID.class, value);
    }

    default HOLDER setProperty(String key, Currency value) {
        return addDependency(key, Currency.class, value);
    }

    default HOLDER setProperty(String key, Locale value) {
        return addDependency(key, Locale.class, value);
    }

    default HOLDER setProperty(String key, Charset value) {
        return addDependency(key, Charset.class, value);
    }

    default HOLDER setProperty(String key, File value) {
        return addDependency(key, File.class, value);
    }

    default HOLDER setProperty(String key, Path value) {
        return addDependency(key, Path.class, value);
    }

    default HOLDER setProperty(String key, InetAddress value) {
        return addDependency(key, InetAddress.class, value);
    }

    default HOLDER setProperty(String key, URL value) {
        return addDependency(key, URL.class, value);
    }

    default HOLDER setProperty(String key, URI value) {
        return addDependency(key, URI.class, value);
    }

    default HOLDER setProperty(String key, Duration value) {
        return addDependency(key, Duration.class, value);
    }

    default HOLDER setProperty(String key, Period value) {
        return addDependency(key, Period.class, value);
    }

    default HOLDER setProperty(String key, Instant value) {
        return addDependency(key, Instant.class, value);
    }

    default HOLDER setProperty(String key, LocalDate value) {
        return addDependency(key, LocalDate.class, value);
    }

    default HOLDER setProperty(String key, LocalTime value) {
        return addDependency(key, LocalTime.class, value);
    }

    default HOLDER setProperty(String key, LocalDateTime value) {
        return addDependency(key, LocalDateTime.class, value);
    }

    default HOLDER setProperty(String key, ZonedDateTime value) {
        return addDependency(key, ZonedDateTime.class, value);
    }

    default HOLDER setProperty(String key, OffsetDateTime value) {
        return addDependency(key, OffsetDateTime.class, value);
    }

    default HOLDER setProperty(String key, OffsetTime value) {
        return addDependency(key, OffsetTime.class, value);
    }

    default HOLDER setProperty(String key, java.util.Date value) {
        return addDependency(key, java.util.Date.class, value);
    }

    default HOLDER setProperty(String key, java.sql.Date value) {
        return addDependency(key, java.sql.Date.class, value);
    }

    default HOLDER setProperty(String key, Time value) {
        return addDependency(key, Time.class, value);
    }

    default HOLDER setProperty(String key, Timestamp value) {
        return addDependency(key, Timestamp.class, value);
    }

    default HOLDER setProperty(String key, String[] value) {
        return addDependency(key, String[].class, value);
    }

    default HOLDER setProperty(String key, Integer[] value) {
        return addDependency(key, Integer[].class, value);
    }

    default HOLDER setProperty(String key, Long[] value) {
        return addDependency(key, Long[].class, value);
    }

    default HOLDER setProperty(String key, Double[] value) {
        return addDependency(key, Double[].class, value);
    }

    default HOLDER setProperty(String key, Float[] value) {
        return addDependency(key, Float[].class, value);
    }

    default HOLDER setProperty(String key, Boolean[] value) {
        return addDependency(key, Boolean[].class, value);
    }

    default HOLDER setProperty(String key, Byte[] value) {
        return addDependency(key, Byte[].class, value);
    }

    default HOLDER setProperty(String key, Short[] value) {
        return addDependency(key, Short[].class, value);
    }

    default HOLDER setProperty(String key, Character[] value) {
        return addDependency(key, Character[].class, value);
    }

    /**
     * Sets a property as an enum value. Throws an exception if the value is not an enum.
     *
     * @param key   property key
     * @param value enum value or null
     * @return fluent builder
     * @throws IllegalArgumentException if {@code value} is not null and not an enum constant
     */
    default HOLDER setEnumProperty(String key, Object value) {
        if (value == null) {
            return addDependency(key, null);
        } else if (!value.getClass().isEnum()) {
            throw new IllegalArgumentException("setEnumProperty requires an enum value or null");
        }
        return addDependency(key, value.getClass(), value);
    }
}
