/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.context;


import io.flamingock.internal.util.Property;

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

public interface ContextConfigurable<HOLDER> {

    /**
     * Manually adds a dependency to be used in the changes, which can be retrieved by its own type.
     *
     * @param instance the dependency instance
     * @return fluent builder
     */
    HOLDER addDependency(Object instance);

    /**
     * Manually adds a dependency to be used in the changes, which can be retrieved by a name.
     *
     * @param name     name under which the dependency will be registered
     * @param instance the dependency instance
     * @return fluent builder
     */
    HOLDER addDependency(String name, Object instance);

    /**
     * Manually adds a dependency to be used in the changes, which can be retrieved by a type.
     *
     * @param type     type under which the dependency will be registered
     * @param instance the dependency instance
     * @return fluent builder
     */
    HOLDER addDependency(Class<?> type, Object instance);

    /**
     * Manually adds a dependency to be used in the changes, retrievable by both name and type.
     *
     * @param name     name under which the dependency will be registered
     * @param type     type under which the dependency will be registered
     * @param instance the dependency instance
     * @return fluent builder
     */
    HOLDER addDependency(String name, Class<?> type, Object instance);

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
