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
package io.flamingock.springboot;

/**
 * Defines how Flamingock integrates with the Spring Boot application lifecycle.
 *
 * <p>This enum controls the level of automation in Flamingock's Spring Boot integration,
 * ranging from fully managed execution to complete manual control.</p>
 *
 * @see FlamingockAutoConfiguration
 */
public enum SpringbootManagementMode {

    /**
     * Spring Boot creates, builds, and executes the Flamingock runner automatically
     * via Spring's {@link org.springframework.boot.ApplicationRunner} interface.
     *
     * <p>This is the default mode. Flamingock executes after the application context
     * is fully initialized but before the application is ready to accept traffic.</p>
     */
    APPLICATION_RUNNER,

    /**
     * Spring Boot creates, builds, and executes the Flamingock runner automatically
     * via Spring's {@link org.springframework.beans.factory.InitializingBean} interface.
     *
     * <p>Flamingock executes during the bean initialization phase, which occurs
     * earlier in the lifecycle than {@link #APPLICATION_RUNNER}. Use this mode
     * when changes must be applied before other beans complete initialization.</p>
     */
    INITIALIZING_BEAN,

    /**
     * Spring Boot creates and configures the Flamingock builder, but the application
     * controls when execution occurs.
     *
     * <p>The builder is exposed as a Spring bean, allowing the application to
     * invoke {@code builder.build().run()} at the appropriate time. This mode
     * is ideal for testing scenarios or when execution timing must be controlled
     * programmatically.</p>
     */
    DEFERRED,

    /**
     * No Flamingock beans are created by Spring Boot auto-configuration.
     *
     * <p>The application assumes full responsibility for creating, configuring,
     * and executing the Flamingock runner. Use this mode when complete control
     * over the Flamingock lifecycle is required.</p>
     */
    UNMANAGED
}
