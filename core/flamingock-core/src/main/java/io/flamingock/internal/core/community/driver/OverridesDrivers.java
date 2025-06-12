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

package io.flamingock.internal.core.community.driver;

import io.flamingock.internal.core.builder.Driver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a {@link Driver} implementation should take precedence over other Driver implementations
 * in the classpath when multiple candidates are discovered via {@link java.util.ServiceLoader}.
 * <p>
 * This annotation is particularly useful in inheritance hierarchies where both parent and child classes
 * implement the Driver interface. When Flamingock discovers multiple Driver implementations through
 * ServiceLoader, it needs to determine which one to use. A Driver annotated with {@code @OverridesDrivers}
 * will be selected over other implementations.
 * <p>
 * The {@code value} attribute must be used to explicitly specify which Driver implementations this
 * Driver should override. At least one Driver class must be provided.
 *
 * @see java.util.ServiceLoader
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OverridesDrivers {

    /**
     * Specifies which Driver implementations this Driver should override.
     * <p>
     * This attribute must be provided with at least one Driver class to override.
     * The annotation is used to explicitly declare which Driver implementations
     * should be overridden by this implementation when multiple candidates are
     * discovered via ServiceLoader.
     *
     * @return an array of Driver classes that this Driver implementation overrides
     */
    Class<? extends Driver<?>>[] value();

}