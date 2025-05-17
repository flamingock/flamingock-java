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

package io.flamingock.springboot.v2;

import io.flamingock.core.context.Dependency;
import io.flamingock.internal.core.context.ContextResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Optional;


/**
 * Implementation of {@link ContextResolver} that resolves dependencies and properties
 * using a Spring Boot 2 {@link ApplicationContext}.
 * <p>
 * It supports retrieval of beans by type or name and reads namespaced properties (prefixed with {@code flamingock.})
 * from the Spring {@link Environment}.
 */
public class SpringbootV2DependencyContext implements ContextResolver {

    private final ApplicationContext applicationContext;
    private final Environment environment;

    /**
     * Creates a new dependency context backed by the given Spring {@link ApplicationContext}.
     *
     * @param applicationContext the Spring application context to use for dependency and property resolution
     */
    public SpringbootV2DependencyContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.environment = applicationContext.getEnvironment();
    }

    /**
     * Attempts to retrieve a bean from the Spring context by its type.
     *
     * @param type the class type of the bean
     * @return an {@link Optional} containing the dependency if found, otherwise empty
     */
    @Override
    public Optional<Dependency> getDependency(Class<?> type) {
        try {
            return Optional.of(new Dependency(type, applicationContext.getBean(type)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to retrieve a bean from the Spring context by its name.
     *
     * @param name the name of the bean
     * @return an {@link Optional} containing the dependency if found, otherwise empty
     */
    @Override
    public Optional<Dependency> getDependency(String name) {
        try {
            return Optional.of(new Dependency(applicationContext.getBean(name)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /**
     * Retrieves a property value as a {@code String} from the Spring {@link Environment}, using the {@code flamingock.} prefix.
     *
     * @param key the property key (without prefix)
     * @return an {@link Optional} containing the value if present, otherwise empty
     */
    @Override
    public Optional<String> getProperty(String key) {
        return Optional.ofNullable(environment.getProperty(flamingockKey(key)));
    }

    /**
     * Retrieves a property value and converts it to the specified type using Spring's conversion service,
     * using the {@code flamingock.} prefix.
     *
     * @param key  the property key (without prefix)
     * @param type the expected type of the value
     * @param <T>  the type of the returned value
     * @return an {@link Optional} containing the converted value if present, otherwise empty
     */
    @Override
    public <T> Optional<T> getPropertyAs(String key, Class<T> type) {
        return Optional.ofNullable(environment.getProperty(flamingockKey(key), type));
    }

    /**
     * Adds the {@code flamingock.} namespace prefix to the property key.
     *
     * @param key the raw property key
     * @return the fully qualified key with the {@code flamingock.} prefix
     */
    private static String flamingockKey(String key) {
        return "flamingock." + key;
    }
}
