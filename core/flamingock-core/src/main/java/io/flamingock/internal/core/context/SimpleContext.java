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
package io.flamingock.internal.core.context;

import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.Dependency;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SimpleContext extends AbstractContextResolver implements Context {

    private final Map<String, Dependency> dependenciesByName;
    private final Map<Class<?>, Dependency> dependenciesByExactType;

    public SimpleContext() {
        this.dependenciesByName = new HashMap<>();
        this.dependenciesByExactType = new HashMap<>();
    }

    @Override
    protected Optional<Dependency> getByName(String name) {
        return Optional.ofNullable(dependenciesByName.get(name));
    }

    @Override
    protected Optional<Dependency> getByType(Class<?> type) {
        Optional<Dependency> dependencyByExactClass = Optional.ofNullable(dependenciesByExactType.get(type));
        if (dependencyByExactClass.isPresent()) {
            return dependencyByExactClass;
        } else {
            return getFirstAssignableDependency(type);
        }
    }

    private Optional<Dependency> getFirstAssignableDependency(Class<?> type) {
        return dependenciesByExactType.entrySet().stream()
                .filter(entry -> type.isAssignableFrom(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Override
    public void addDependency(Dependency dependency) {
        if (!dependency.isDefaultNamed()) {
            dependenciesByName.put(dependency.getName(), dependency);
        }
        dependenciesByExactType.put(dependency.getType(), dependency);
    }

    @Override
    public void setProperty(Property value) {
        addDependency(new Dependency(value.getKey(), value.getClass(), value));
    }

    @Override
    public void setProperty(String key, String value) {
        addDependency(new Dependency(key, String.class, value));
    }

    @Override
    public void setProperty(String key, Boolean value) {
        addDependency(new Dependency(key, Boolean.class, value));
    }

    @Override
    public void setProperty(String key, Integer value) {
        addDependency(new Dependency(key, Integer.class, value));
    }

    @Override
    public void setProperty(String key, Float value) {
        addDependency(new Dependency(key, Float.class, value));
    }

    @Override
    public void setProperty(String key, Long value) {
        addDependency(new Dependency(key, Long.class, value));
    }

    @Override
    public void setProperty(String key, Double value) {
        addDependency(new Dependency(key, Double.class, value));
    }

    @Override
    public void setProperty(String key, UUID value) {
        addDependency(new Dependency(key, UUID.class, value));
    }

    @Override
    public void setProperty(String key, Currency value) {
        addDependency(new Dependency(key, Currency.class, value));
    }

    @Override
    public void setProperty(String key, Locale value) {
        addDependency(new Dependency(key, Locale.class, value));
    }

    @Override
    public void setProperty(String key, Charset value) {
        addDependency(new Dependency(key, Charset.class, value));
    }

    @Override
    public void setProperty(String key, File value) {
        addDependency(new Dependency(key, File.class, value));
    }

    @Override
    public void setProperty(String key, Path value) {
        addDependency(new Dependency(key, Path.class, value));
    }

    @Override
    public void setProperty(String key, InetAddress value) {
        addDependency(new Dependency(key, InetAddress.class, value));
    }

    @Override
    public void setProperty(String key, URL value) {
        addDependency(new Dependency(key, URL.class, value));
    }

    @Override
    public void setProperty(String key, URI value) {
        addDependency(new Dependency(key, URI.class, value));
    }

    @Override
    public void setProperty(String key, Duration value) {
        addDependency(new Dependency(key, Duration.class, value));
    }

    @Override
    public void setProperty(String key, Period value) {
        addDependency(new Dependency(key, Period.class, value));
    }

    @Override
    public void setProperty(String key, Instant value) {
        addDependency(new Dependency(key, Instant.class, value));
    }

    @Override
    public void setProperty(String key, LocalDate value) {
        addDependency(new Dependency(key, LocalDate.class, value));
    }

    @Override
    public void setProperty(String key, LocalTime value) {
        addDependency(new Dependency(key, LocalTime.class, value));
    }

    @Override
    public void setProperty(String key, LocalDateTime value) {
        addDependency(new Dependency(key, LocalDateTime.class, value));
    }

    @Override
    public void setProperty(String key, ZonedDateTime value) {
        addDependency(new Dependency(key, ZonedDateTime.class, value));
    }

    @Override
    public void setProperty(String key, OffsetDateTime value) {
        addDependency(new Dependency(key, OffsetDateTime.class, value));
    }

    @Override
    public void setProperty(String key, OffsetTime value) {
        addDependency(new Dependency(key, OffsetTime.class, value));
    }

    @Override
    public void setProperty(String key, Date value) {
        addDependency(new Dependency(key, Date.class, value));
    }

    @Override
    public void setProperty(String key, java.sql.Date value) {
        addDependency(new Dependency(key, java.sql.Date.class, value));
    }

    @Override
    public void setProperty(String key, Time value) {
        addDependency(new Dependency(key, Time.class, value));
    }

    @Override
    public void setProperty(String key, Timestamp value) {
        addDependency(new Dependency(key, Timestamp.class, value));
    }

    @Override
    public void setProperty(String key, String[] value) {
        addDependency(new Dependency(key, String[].class, value));
    }

    @Override
    public void setProperty(String key, Integer[] value) {
        addDependency(new Dependency(key, Integer[].class, value));
    }

    @Override
    public void setProperty(String key, Long[] value) {
        addDependency(new Dependency(key, Long[].class, value));
    }

    @Override
    public void setProperty(String key, Double[] value) {
        addDependency(new Dependency(key, Double[].class, value));
    }

    @Override
    public void setProperty(String key, Float[] value) {
        addDependency(new Dependency(key, Float[].class, value));
    }

    @Override
    public void setProperty(String key, Boolean[] value) {
        addDependency(new Dependency(key, Boolean[].class, value));
    }

    @Override
    public void setProperty(String key, Byte[] value) {
        addDependency(new Dependency(key, Byte[].class, value));
    }

    @Override
    public void setProperty(String key, Short[] value) {
        addDependency(new Dependency(key, Short[].class, value));
    }

    @Override
    public void setProperty(String key, Character[] value) {
        addDependency(new Dependency(key, Character[].class, value));
    }

    @Override
    public <T extends Enum<T>> void setProperty(String key, T value) {
        addDependency(new Dependency(key, value.getClass(), value));
    }

}
