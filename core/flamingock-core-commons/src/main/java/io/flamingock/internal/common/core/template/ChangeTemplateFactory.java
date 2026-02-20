/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.template;

import io.flamingock.api.template.ChangeTemplate;

import java.util.Collection;
import java.util.ServiceLoader;

/**
 * Service provider interface for factories that produce {@link ChangeTemplate} instances.
 * <p>
 * This interface enables a federated approach to template discovery, allowing modules
 * to provide multiple templates through a single service provider. Implementations of
 * this interface are discovered via Java's {@link ServiceLoader} mechanism during both:
 * <ul>
 *   <li>GraalVM build-time processing for native image reflection registration</li>
 *   <li>Runtime template discovery and registration</li>
 * </ul>
 * <p>
 * To register a factory implementation, create a file at:
 * {@code META-INF/services/io.flamingock.internal.common.core.template.ChangeTemplateFactory}
 * containing the fully qualified class name of your implementation.
 * <p>
 * Factory implementations should be stateless and thread-safe, as they may be
 * instantiated multiple times and accessed concurrently.
 *
 * @see ChangeTemplateManager
 * @see ServiceLoader
 */

public interface ChangeTemplateFactory {

    /**
     * Returns a collection of {@link ChangeTemplate} instances provided by this factory.
     * <p>
     * This method is called by {@link ChangeTemplateManager#getRawTemplates()} to discover templates
     * in a federated manner. It is invoked in two contexts:
     * <ul>
     *   <li>During GraalVM build-time processing to register template classes for reflection</li>
     *   <li>During runtime initialization to populate the template registry</li>
     * </ul>
     * <p>
     * Implementations should:
     * <ul>
     *   <li>Create and return new instances of templates each time this method is called</li>
     *   <li>Not maintain any state between invocations</li>
     *   <li>Be thread-safe</li>
     *   <li>Handle any exceptions internally to prevent disrupting the template discovery process</li>
     * </ul>
     *
     * @return A collection of template instances provided by this factory
     */
    Collection<ChangeTemplate<?, ?, ?>> getTemplates();
}
