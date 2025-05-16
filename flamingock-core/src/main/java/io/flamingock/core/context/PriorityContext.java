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

package io.flamingock.core.context;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;

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

/**
 * A {@link Context} that combines a writable priority context
 * with a read-only fallback context.
 * <p>
 * Dependency resolution prioritizes the {@code priorityInjectableContext}, but dependency
 * injection operations (add/remove) only affect the priority context.
 */
public class PriorityContext extends PriorityContextResolver implements Context {

    private final Context priorityInjectableContext;

    /**
     * Creates a context that combines a writable {@link SimpleContext}
     * with a read-only fallback base context.
     *
     * @param baseContext the fallback context to be used when the dependency is not found in the priority context
     */
    public PriorityContext(ContextResolver baseContext) {
        this(new SimpleContext(), baseContext);
    }

    /**
     * Creates a context that combines the given writable {@code priorityInjectableContext}
     * with the given read-only {@code baseContext}.
     *
     * @param priorityInjectableContext the context where dependencies are added or removed
     * @param baseContext               the fallback context used only for resolution
     */
    public PriorityContext(Context priorityInjectableContext, ContextResolver baseContext) {
        super(priorityInjectableContext, baseContext);
        this.priorityInjectableContext = priorityInjectableContext;
    }

    /**
     * Adds a dependency to the priority (writable) context.
     *
     * @param dependency the dependency to add
     */
    @Override
    public void addDependency(Dependency dependency) {
        priorityInjectableContext.addDependency(dependency);
    }

    /**
     * Removes the specified dependency from the priority (writable) context only.
     * This operation is idempotent and does not affect the base context.
     *
     * @param dependency the dependency to be removed
     */
    @Override
    public void removeDependencyByRef(Dependency dependency) {
        priorityInjectableContext.removeDependencyByRef(dependency);
    }

    @Override
    public void setProperty(String key, EnvironmentId value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, ServiceId value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, RunnerId value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, String value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Boolean value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Integer value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Float value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Long value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Double value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, UUID value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Currency value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Locale value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Charset value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, File value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Path value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, InetAddress value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, URL value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, URI value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Duration value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Period value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Instant value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, LocalDate value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, LocalTime value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, LocalDateTime value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, ZonedDateTime value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, OffsetDateTime value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, OffsetTime value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Date value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, java.sql.Date value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Time value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Timestamp value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, String[] value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Integer[] value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Long[] value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Double[] value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Float[] value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Boolean[] value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Byte[] value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Short[] value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setProperty(String key, Character[] value) {
        priorityInjectableContext.setProperty(key, value);
    }

    @Override
    public void setEnumProperty(String key, Object value) {
        priorityInjectableContext.setEnumProperty(key, value);
    }

}
